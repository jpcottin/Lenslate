package com.jpcottin.lenslate.data.tts

import android.content.Context
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Speaks translations with Android text-to-speech.
 *
 * The engine is created from whichever context is attached: the glasses activity attaches
 * itself so audio goes to the glasses' speakers, and detaches when it stops, which reverts to
 * the phone (a Bluetooth headset pairing still routes that to the glasses).
 *
 * [isSpeaking] is true while any utterance is queued or playing, plus a short tail so the room
 * echo dies out; the microphone is suspended while it is true so the recognizer does not hear
 * the app's own voice and feed it back into the pipeline. An utterance that waits for the engine
 * to come up is queued like any other: the conversation swap and the microphone wait for it too.
 *
 * All state is confined to the main thread: public methods are called from main, the engine
 * reports its initialization on main, and utterance progress is re-dispatched through [scope],
 * which runs on main.
 */
class TranslationSpeaker internal constructor(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val speakingTailMs: Long,
    private val initTimeoutMs: Long,
    private val engines: SpeechEngineFactory,
) {
    constructor(appContext: Context, scope: CoroutineScope) :
        this(appContext, scope, SPEAKING_TAIL_MS, INIT_TIMEOUT_MS, SpeechEngineFactory(::AndroidSpeechEngine))

    private var engine: SpeechEngine? = null
    private var ready = false

    /** The engine could not be used; the next utterance tries a new one. */
    private var initFailed = false
    private var context = appContext
    private val pending = ArrayDeque<Pair<String, Language>>()

    /** Identity of the engine currently being initialized; stale init callbacks are ignored. */
    private var initToken: Any? = null
    private var initTimeoutJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)

    /** True from the moment an utterance is enqueued until shortly after the last one finished. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val activeUtterances = mutableSetOf<String>()
    private var speakingTailJob: Job? = null

    fun attach(context: Context) = recreate(context)

    fun detach() = recreate(appContext)

    fun speak(text: String, language: Language) {
        if (text.isBlank()) return
        if (engine == null || initFailed) recreate(context)
        val engine = engine ?: return
        // The engine can report a failed initialization before its creation even returns.
        if (initFailed) return
        if (!ready) {
            pending.addLast(text to language)
            holdSpeaking()
            return
        }
        val utteranceId = "lenslate-${System.nanoTime()}"
        utteranceStarted(utteranceId)
        if (!engine.speak(text, language, utteranceId)) utteranceFinished(utteranceId)
    }

    fun stop() {
        pending.clear()
        engine?.stop()
        resetSpeaking()
    }

    fun shutdown() {
        pending.clear()
        initToken = null
        initTimeoutJob?.cancel()
        engine?.shutdown()
        engine = null
        ready = false
        resetSpeaking()
    }

    private fun recreate(context: Context) {
        engine?.shutdown()
        this.context = context
        ready = false
        initFailed = false
        // Utterances in flight on the old engine die with it; their callbacks will not come.
        // Those still waiting for an engine are spoken by the new one.
        resetSpeaking()
        val token = Any()
        initToken = token
        initTimeoutJob?.cancel()
        // An engine that never reports back must not keep the microphone muted for good.
        initTimeoutJob = scope.launch {
            delay(initTimeoutMs)
            initialized(token, success = false)
        }
        engine = engines.create(
            context = context,
            onInit = { success -> initialized(token, success) },
            // Progress callbacks arrive on a binder thread; hop to main where state lives.
            onFinished = { id -> scope.launch { utteranceFinished(id) } },
        )
    }

    private fun initialized(token: Any, success: Boolean) {
        // A callback from an engine that was already replaced must not touch state, and only the
        // first answer of an engine counts.
        if (initToken !== token || ready || initFailed) return
        initTimeoutJob?.cancel()
        if (!success) {
            initFailed = true
            pending.clear()
            resetSpeaking()
            return
        }
        ready = true
        while (pending.isNotEmpty()) {
            val (text, language) = pending.removeFirst()
            speak(text, language)
        }
    }

    private fun utteranceStarted(id: String) {
        activeUtterances += id
        holdSpeaking()
    }

    private fun holdSpeaking() {
        speakingTailJob?.cancel()
        speakingTailJob = null
        _isSpeaking.value = true
    }

    private fun utteranceFinished(id: String) {
        if (!activeUtterances.remove(id) || activeUtterances.isNotEmpty()) return
        speakingTailJob?.cancel()
        speakingTailJob = scope.launch {
            delay(speakingTailMs)
            if (activeUtterances.isEmpty() && pending.isEmpty()) _isSpeaking.value = false
        }
    }

    private fun resetSpeaking() {
        activeUtterances.clear()
        speakingTailJob?.cancel()
        speakingTailJob = null
        _isSpeaking.value = pending.isNotEmpty()
    }

    private companion object {
        /** How long after the last utterance the microphone stays muted, for the echo to fade. */
        const val SPEAKING_TAIL_MS = 300L

        /** How long an engine may take to come up before it is given up on. */
        const val INIT_TIMEOUT_MS = 5_000L
    }
}
