package io.github.jpcottin.lenslate.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.jpcottin.lenslate.data.settings.Settings
import io.github.jpcottin.lenslate.data.settings.SettingsRepository
import io.github.jpcottin.lenslate.data.speech.AndroidSpeechSource
import io.github.jpcottin.lenslate.data.speech.InjectableSpeechSource
import io.github.jpcottin.lenslate.data.translate.GeminiTranslationEngine
import io.github.jpcottin.lenslate.data.translate.MlKitTranslationEngine
import io.github.jpcottin.lenslate.data.translate.ModelRepository
import io.github.jpcottin.lenslate.data.ocr.MlKitTextRecognizer
import io.github.jpcottin.lenslate.data.tts.TranslationSpeaker
import io.github.jpcottin.lenslate.domain.EngineKind
import io.github.jpcottin.lenslate.domain.LiveTranslator
import io.github.jpcottin.lenslate.domain.SpeechSource
import io.github.jpcottin.lenslate.domain.TranslationEngine
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

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@OptIn(ExperimentalProjectedApi::class)
/** Manual dependency container, owned by [io.github.jpcottin.lenslate.LenslateApplication]. */
class AppContainer(private val appContext: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(appContext.settingsDataStore)

    /** Hot snapshot of the settings so engines can read them synchronously. */
    val settings: StateFlow<Settings> =
        settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, Settings())

    val modelRepository = ModelRepository()

    val onDeviceEngine = MlKitTranslationEngine()

    val geminiEngine = GeminiTranslationEngine(
        apiKey = { settings.value.geminiApiKey },
        model = { settings.value.geminiModel },
    )

    fun engineFor(kind: EngineKind): TranslationEngine = when (kind) {
        EngineKind.ON_DEVICE -> onDeviceEngine
        EngineKind.GEMINI -> geminiEngine
    }

    /** The engine currently selected in settings, read from disk (for AppFunctions). */
    suspend fun currentEngine(): TranslationEngine = engineFor(settingsRepository.settings.first().engine)

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
            settingsRepository.settings.collect { s -> liveTranslator.setLanguages(s.from, s.to) }
        }
    }

    /**
     * Speech from the microphone reachable through [context]: pass the projected activity for the
     * glasses' microphone, or a phone context for the phone's. Debug builds and tests can also
     * inject sentences through [io.github.jpcottin.lenslate.data.speech.UtteranceInjector].
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
