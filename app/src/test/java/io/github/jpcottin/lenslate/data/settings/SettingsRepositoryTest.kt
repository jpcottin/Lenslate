package io.github.jpcottin.lenslate.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jpcottin.lenslate.domain.EngineKind
import io.github.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

class SettingsRepositoryTest {
    @Test
    fun defaults() = runTest {
        val s = SettingsRepository(FakeDataStore()).settings.first()
        assertEquals(Language.FRENCH, s.from)
        assertEquals(Language.ENGLISH, s.to)
        assertEquals(EngineKind.ON_DEVICE, s.engine)
        assertEquals("gemini-2.5-flash", s.geminiModel)
        assertFalse(s.speakTranslations)
        assertTrue(s.showSourceOnGlasses)
        assertFalse(s.isGeminiConfigured)
    }

    @Test
    fun roundTrip() = runTest {
        val repo = SettingsRepository(FakeDataStore())
        repo.setLanguages(Language.JAPANESE, Language.GERMAN)
        repo.setEngine(EngineKind.GEMINI)
        repo.setGeminiApiKey("  key  ")
        repo.setGeminiModel("gemini-2.5-pro")
        repo.setSpeakTranslations(true)
        repo.setShowSourceOnGlasses(false)

        val s = repo.settings.first()
        assertEquals(Language.JAPANESE, s.from)
        assertEquals(Language.GERMAN, s.to)
        assertEquals(EngineKind.GEMINI, s.engine)
        assertEquals("key", s.geminiApiKey)
        assertTrue(s.isGeminiConfigured)
        assertEquals("gemini-2.5-pro", s.geminiModel)
        assertTrue(s.speakTranslations)
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
        val s = SettingsRepository(store).settings.first()
        assertEquals(Language.FRENCH, s.from)
        assertEquals(EngineKind.ON_DEVICE, s.engine)
        assertEquals("gemini-2.5-flash", s.geminiModel)
    }
}
