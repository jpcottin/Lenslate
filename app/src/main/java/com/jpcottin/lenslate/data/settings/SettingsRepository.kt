package com.jpcottin.lenslate.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpcottin.lenslate.BuildConfig
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class Settings(
    val from: Language = Language.DEFAULT_SOURCE,
    val to: Language = Language.DEFAULT_TARGET,
    val engine: EngineKind = EngineKind.ON_DEVICE,
    val geminiApiKey: String = "",
    val geminiModel: String = BuildConfig.GEMINI_DEFAULT_MODEL,
    val speakTranslations: Boolean = false,
    val conversationMode: Boolean = false,
    val showSourceOnGlasses: Boolean = true,
) {
    val isGeminiConfigured: Boolean get() = geminiApiKey.isNotBlank()
}

/**
 * User preferences persisted with Jetpack DataStore. The Gemini API key is only ever written
 * encrypted by [cipher].
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    // Settings are re-emitted on every edit: only go back to the Keystore when the key changed.
    @Volatile private var decrypted: Pair<String, String>? = null

    val settings: Flow<Settings> = dataStore.data
        // An unreadable preferences file must never crash (or brick) the app: fall back to
        // defaults; the next successful edit rewrites the file.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
        Settings(
            from = Language.fromCode(p[FROM]) ?: Language.DEFAULT_SOURCE,
            to = Language.fromCode(p[TO]) ?: Language.DEFAULT_TARGET,
            engine = p[ENGINE]?.let { runCatching { EngineKind.valueOf(it) }.getOrNull() } ?: EngineKind.ON_DEVICE,
            geminiApiKey = p[GEMINI_API_KEY_ENCRYPTED]?.let(::decryptApiKey).orEmpty(),
            geminiModel = p[GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_DEFAULT_MODEL,
            speakTranslations = p[SPEAK_TRANSLATIONS] ?: false,
            conversationMode = p[CONVERSATION_MODE] ?: false,
            showSourceOnGlasses = p[SHOW_SOURCE_ON_GLASSES] ?: true,
        )
    }

    suspend fun setLanguages(from: Language, to: Language) = dataStore.edit {
        it[FROM] = from.code
        it[TO] = to.code
    }

    suspend fun setEngine(engine: EngineKind) = dataStore.edit { it[ENGINE] = engine.name }
    suspend fun setGeminiApiKey(key: String) = dataStore.edit {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) it.remove(GEMINI_API_KEY_ENCRYPTED) else it[GEMINI_API_KEY_ENCRYPTED] = cipher.encrypt(trimmed)
    }
    suspend fun setGeminiModel(model: String) = dataStore.edit { it[GEMINI_MODEL] = model.trim() }
    suspend fun setSpeakTranslations(enabled: Boolean) = dataStore.edit { it[SPEAK_TRANSLATIONS] = enabled }
    suspend fun setConversationMode(enabled: Boolean) = dataStore.edit { it[CONVERSATION_MODE] = enabled }
    suspend fun setShowSourceOnGlasses(enabled: Boolean) = dataStore.edit { it[SHOW_SOURCE_ON_GLASSES] = enabled }

    /** An undecryptable key (a settings file restored on another device) reads as "no key". */
    private fun decryptApiKey(stored: String): String {
        decrypted?.let { (cached, plain) -> if (cached == stored) return plain }
        return cipher.decrypt(stored).orEmpty().also { decrypted = stored to it }
    }

    private companion object {
        val FROM = stringPreferencesKey("from_language")
        val TO = stringPreferencesKey("to_language")
        val ENGINE = stringPreferencesKey("engine")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val SPEAK_TRANSLATIONS = booleanPreferencesKey("speak_translations")
        val CONVERSATION_MODE = booleanPreferencesKey("conversation_mode")
        val SHOW_SOURCE_ON_GLASSES = booleanPreferencesKey("show_source_on_glasses")
    }
}

// Earlier versions stored the API key in plain text under LEGACY_GEMINI_API_KEY.
private val LEGACY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
private val GEMINI_API_KEY_ENCRYPTED = stringPreferencesKey("gemini_api_key_encrypted")

/** Re-writes an API key saved in plain text by an earlier version as an encrypted one. */
class EncryptGeminiApiKeyMigration(private val cipher: SecretCipher) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = LEGACY_GEMINI_API_KEY in currentData

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            val legacy = remove(LEGACY_GEMINI_API_KEY).orEmpty().trim()
            if (legacy.isNotEmpty() && GEMINI_API_KEY_ENCRYPTED !in this) {
                this[GEMINI_API_KEY_ENCRYPTED] = cipher.encrypt(legacy)
            }
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}
