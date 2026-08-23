package com.jpcottin.lenslate.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jpcottin.lenslate.BuildConfig
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val from: Language = Language.DEFAULT_SOURCE,
    val to: Language = Language.DEFAULT_TARGET,
    val engine: EngineKind = EngineKind.ON_DEVICE,
    val geminiApiKey: String = "",
    val geminiModel: String = BuildConfig.GEMINI_DEFAULT_MODEL,
    val speakTranslations: Boolean = false,
    val showSourceOnGlasses: Boolean = true,
) {
    val isGeminiConfigured: Boolean get() = geminiApiKey.isNotBlank()
}

/** User preferences persisted with Jetpack DataStore. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(
            from = Language.fromCode(p[FROM]) ?: Language.DEFAULT_SOURCE,
            to = Language.fromCode(p[TO]) ?: Language.DEFAULT_TARGET,
            engine = p[ENGINE]?.let { runCatching { EngineKind.valueOf(it) }.getOrNull() } ?: EngineKind.ON_DEVICE,
            geminiApiKey = p[GEMINI_API_KEY].orEmpty(),
            geminiModel = p[GEMINI_MODEL]?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_DEFAULT_MODEL,
            speakTranslations = p[SPEAK_TRANSLATIONS] ?: false,
            showSourceOnGlasses = p[SHOW_SOURCE_ON_GLASSES] ?: true,
        )
    }

    suspend fun setLanguages(from: Language, to: Language) = dataStore.edit {
        it[FROM] = from.code
        it[TO] = to.code
    }

    suspend fun setEngine(engine: EngineKind) = dataStore.edit { it[ENGINE] = engine.name }
    suspend fun setGeminiApiKey(key: String) = dataStore.edit { it[GEMINI_API_KEY] = key.trim() }
    suspend fun setGeminiModel(model: String) = dataStore.edit { it[GEMINI_MODEL] = model.trim() }
    suspend fun setSpeakTranslations(enabled: Boolean) = dataStore.edit { it[SPEAK_TRANSLATIONS] = enabled }
    suspend fun setShowSourceOnGlasses(enabled: Boolean) = dataStore.edit { it[SHOW_SOURCE_ON_GLASSES] = enabled }

    private companion object {
        val FROM = stringPreferencesKey("from_language")
        val TO = stringPreferencesKey("to_language")
        val ENGINE = stringPreferencesKey("engine")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val SPEAK_TRANSLATIONS = booleanPreferencesKey("speak_translations")
        val SHOW_SOURCE_ON_GLASSES = booleanPreferencesKey("show_source_on_glasses")
    }
}
