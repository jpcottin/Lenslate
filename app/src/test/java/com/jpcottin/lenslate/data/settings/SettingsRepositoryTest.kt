package com.jpcottin.lenslate.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DataStore so the repository's mapping can be tested on the JVM. */
private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> get() = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}

/** A store whose first [failures] reads throw, as a disk hiccup would, then behaves. */
private class FlakyDataStore(private var failures: Int) : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    var attempts = 0
        private set
    override val data: Flow<Preferences> get() = flow {
        attempts++
        if (failures > 0) { failures--; throw IOException("disk hiccup") }
        emitAll(state)
    }
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}

/** Reversible stand-in for the Keystore: "enc:" + the reversed secret. */
private object FakeCipher : SecretCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(stored: String) = stored.takeIf { it.startsWith("enc:") }?.removePrefix("enc:")?.reversed()
}

/** A Keystore that throws something the cipher does not expect. */
private object BrokenCipher : SecretCipher {
    override fun encrypt(plain: String): String = throw IllegalStateException("keystore down")
    override fun decrypt(stored: String): String? = throw IllegalStateException("keystore down")
}

class SettingsRepositoryTest {
    @Test
    fun aKeystoreFailureReadsAsNoKey_andTheFlowGoesOn() = runTest {
        val store = FakeDataStore(preferencesOf(stringPreferencesKey("gemini_api_key_encrypted") to "enc:x"))
        val repo = SettingsRepository(store, BrokenCipher)
        assertFalse(repo.settings.first().isGeminiConfigured)      // not a crash
        repo.setLanguages(Language.JAPANESE, Language.GERMAN)
        assertEquals(Language.JAPANESE, repo.settings.first().from)  // the flow still works
    }

    @Test
    fun aKeystoreThatCannotEncrypt_leavesTheStoredKeyAlone() = runTest {
        val store = FakeDataStore(preferencesOf(stringPreferencesKey("gemini_api_key_encrypted") to "enc:x"))
        val repo = SettingsRepository(store, BrokenCipher)
        assertFalse(repo.setGeminiApiKey("new-key"))
        assertEquals("enc:x", store.data.first()[stringPreferencesKey("gemini_api_key_encrypted")])
    }

    @Test
    fun aReadErrorIsRetriedAndTheCollectorKeepsFollowingTheSettings() = runTest {
        val store = FlakyDataStore(failures = 1)
        val repo = SettingsRepository(store, FakeCipher)
        val seen = mutableListOf<Settings>()
        val collector = launch { repo.settings.collect { seen += it } }
        advanceUntilIdle()
        assertEquals(2, store.attempts)                       // the failed read, then the retry
        repo.setLanguages(Language.JAPANESE, Language.GERMAN) // a later edit must still arrive
        advanceUntilIdle()
        assertEquals(Language.JAPANESE, seen.last().from)
        assertEquals(Language.GERMAN, seen.last().to)
        collector.cancel()
    }

    @Test
    fun aStoreThatNeverReadsFallsBackToDefaults() = runTest {
        val store = FlakyDataStore(failures = 10)
        val s = SettingsRepository(store, FakeCipher).settings.first()
        assertEquals(Language.DEFAULT_SOURCE, s.from)
        assertEquals(4, store.attempts)                       // one read and three retries
    }

    @Test
    fun defaults() = runTest {
        val s = SettingsRepository(FakeDataStore(), FakeCipher).settings.first()
        assertEquals(Language.FRENCH, s.from)
        assertEquals(Language.ENGLISH, s.to)
        assertEquals(EngineKind.ON_DEVICE, s.engine)
        assertEquals("gemini-3.5-flash-lite", s.geminiModel)
        assertFalse(s.speakTranslations)
        assertFalse(s.conversationMode)
        assertTrue(s.showSourceOnGlasses)
        assertFalse(s.isGeminiConfigured)
    }

    @Test
    fun roundTrip() = runTest {
        val repo = SettingsRepository(FakeDataStore(), FakeCipher)
        repo.setLanguages(Language.JAPANESE, Language.GERMAN)
        repo.setEngine(EngineKind.GEMINI)
        repo.setGeminiApiKey("  key  ")
        repo.setGeminiModel("gemini-3.8-flash")
        repo.setSpeakTranslations(true)
        repo.setConversationMode(true)
        repo.setShowSourceOnGlasses(false)

        val s = repo.settings.first()
        assertEquals(Language.JAPANESE, s.from)
        assertEquals(Language.GERMAN, s.to)
        assertEquals(EngineKind.GEMINI, s.engine)
        assertEquals("key", s.geminiApiKey)
        assertTrue(s.isGeminiConfigured)
        assertEquals("gemini-3.8-flash", s.geminiModel)
        assertTrue(s.speakTranslations)
        assertTrue(s.conversationMode)
        assertFalse(s.showSourceOnGlasses)
    }

    @Test
    fun corruptValues_fallBackToDefaults() = runTest {
        val store = FakeDataStore()
        store.updateData { p ->
            p.toMutablePreferences().apply {
                this[stringPreferencesKey("from_language")] = "klingon"
                this[stringPreferencesKey("engine")] = "CARRIER_PIGEON"
                this[stringPreferencesKey("gemini_model")] = "   "
            }
        }
        val s = SettingsRepository(store, FakeCipher).settings.first()
        assertEquals(Language.FRENCH, s.from)
        assertEquals(EngineKind.ON_DEVICE, s.engine)
        assertEquals("gemini-3.5-flash-lite", s.geminiModel)
    }

    @Test
    fun apiKey_isNeverStoredInPlainText() = runTest {
        val store = FakeDataStore()
        val repo = SettingsRepository(store, FakeCipher)
        repo.setGeminiApiKey("secret-key")

        val stored = store.data.first().asMap().values.map { it.toString() }
        assertTrue(stored.none { "secret-key" in it })
        assertEquals("secret-key", repo.settings.first().geminiApiKey)

        repo.setGeminiApiKey("  ")
        assertTrue(store.data.first().asMap().isEmpty())
        assertFalse(repo.settings.first().isGeminiConfigured)
    }

    @Test
    fun undecryptableApiKey_readsAsNotConfigured() = runTest {
        val store = FakeDataStore()
        store.updateData { p ->
            p.toMutablePreferences().apply {
                this[stringPreferencesKey("gemini_api_key_encrypted")] = "restored-from-another-device"
            }
        }
        assertFalse(SettingsRepository(store, FakeCipher).settings.first().isGeminiConfigured)
    }

    @Test
    fun migration_encryptsLegacyPlainTextKey() = runTest {
        val migration = EncryptGeminiApiKeyMigration(FakeCipher)
        assertFalse(migration.shouldMigrate(emptyPreferences()))

        val store = FakeDataStore()
        store.updateData { p ->
            p.toMutablePreferences().apply {
                this[stringPreferencesKey("gemini_api_key")] = " legacy-key "
                this[stringPreferencesKey("gemini_model")] = "gemini-3.8-flash"
            }
        }
        assertTrue(migration.shouldMigrate(store.data.first()))
        store.updateData { migration.migrate(it) }

        val migrated = store.data.first()
        assertFalse(migration.shouldMigrate(migrated))
        assertTrue(migrated.asMap().values.none { "legacy-key" in it.toString() })
        val s = SettingsRepository(store, FakeCipher).settings.first()
        assertEquals("legacy-key", s.geminiApiKey)
        assertEquals("gemini-3.8-flash", s.geminiModel)
    }
}
