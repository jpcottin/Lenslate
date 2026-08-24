package com.jpcottin.lenslate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.data.settings.SettingsRepository
import com.jpcottin.lenslate.data.speech.AndroidSpeechSource
import com.jpcottin.lenslate.data.speech.InjectableSpeechSource
import com.jpcottin.lenslate.data.translate.GeminiTranslationEngine
import com.jpcottin.lenslate.data.translate.MlKitTranslationEngine
import com.jpcottin.lenslate.data.translate.ModelRepository
import com.jpcottin.lenslate.data.ocr.MlKitTextRecognizer
import com.jpcottin.lenslate.data.tts.TranslationSpeaker
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.SpeechSource
import com.jpcottin.lenslate.domain.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    // A corrupted file is replaced with defaults instead of crash-looping the app at startup.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@OptIn(ExperimentalProjectedApi::class)
/** Manual dependency container, owned by [com.jpcottin.lenslate.LenslateApplication]. */
class AppContainer(private val appContext: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)

    /** Hot snapshot of the settings so engines can read them synchronously. */
    val settings: StateFlow<Settings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, Settings())

    val modelRepository = ModelRepository()

    val onDeviceEngine = MlKitTranslationEngine()

    /**
     * Latest settings actually read from disk. The eagerly-started [settings] StateFlow can
     * still hold its construction-time defaults when a cold-started AppFunction runs;
     * [freshSettings] closes that race for suspending callers.
     */
    @Volatile
    private var settingsSnapshot: Settings? = null

    val geminiEngine = GeminiTranslationEngine(
        apiKey = { (settingsSnapshot ?: settings.value).geminiApiKey },
        model = { (settingsSnapshot ?: settings.value).geminiModel },
    )

    fun engineFor(kind: EngineKind): TranslationEngine = when (kind) {
        EngineKind.ON_DEVICE -> onDeviceEngine
        EngineKind.GEMINI -> geminiEngine
    }

    /** Settings read from disk right now — for entry points that may precede the eager collector. */
    suspend fun freshSettings(): Settings =
        settingsRepository.settings.first().also { settingsSnapshot = it }

    /** One shared pipeline: the phone UI and the glasses UI observe the same transcript. */
    val liveTranslator = LiveTranslator(
        engine = { engineFor(settings.value.engine) },
        scope = appScope,
        // Translating interim hypotheses is cheap on-device but would spam the Gemini API.
        partialTranslationEnabled = { settings.value.engine == EngineKind.ON_DEVICE },
    )

    val speaker = TranslationSpeaker(appContext)

    /** On-device OCR for Read mode. */
    val textRecognizer = MlKitTextRecognizer()

    init {
        appScope.launch {
            liveTranslator.translated.collect { utterance ->
                val s = settings.value
                if (s.speakTranslations) speaker.speak(utterance.translation.orEmpty(), s.to)
            }
        }
        appScope.launch {
            settingsRepository.settings.collect { s ->
                settingsSnapshot = s
                liveTranslator.setLanguages(s.from, s.to)
            }
        }
    }

    /**
     * Speech from the microphone reachable through [context]: pass the projected activity for the
     * glasses' microphone, or a phone context for the phone's. Debug builds and tests can also
     * inject sentences through [com.jpcottin.lenslate.data.speech.UtteranceInjector].
     */
    fun speechSource(context: Context): SpeechSource = InjectableSpeechSource(AndroidSpeechSource(context))

    /**
     * The phone's own context, explicitly via the host device context: the application context
     * can resolve to the glasses' device when a projected activity was the last one in front.
     */
    fun hostContext(): Context =
        runCatching { ProjectedContext.createHostDeviceContext(appContext) }.getOrDefault(appContext)

    /** The phone's microphone. */
    fun phoneSpeechSource(): SpeechSource = speechSource(hostContext())

    /** Whether Display AI Glasses are currently connected to this phone. */
    fun glassesConnected(): Flow<Boolean> =
        runCatching { ProjectedContext.isProjectedDeviceConnected(appContext, Dispatchers.Main) }
            .getOrElse { flowOf(false) }
}
