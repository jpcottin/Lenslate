package com.jpcottin.lenslate.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
 * The [TextToSpeech] instance is created from whichever context is attached: the glasses
 * activity attaches itself so audio goes to the glasses' speakers, and detaches when it stops,
 * which reverts to the phone (a Bluetooth headset pairing still routes that to the glasses).
 *
 * [isSpeaking] is true while any utterance is queued or playing, plus a short tail so the room
 * echo dies out; the microphone is suspended while it is true so the recognizer does not hear
 * the app's own voice and feed it back into the pipeline.
 *
 * All state is confined to the main thread: public methods are called from main, and engine
 * callbacks (init, utterance progress) re-dispatch through [scope], which runs on main.
 */
class TranslationSpeaker(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val speakingTailMs: Long = SPEAKING_TAIL_MS,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Language>>()

    /** Identity of the engine currently being initialized; stale init callbacks are ignored. */
    private var initToken: Any? = null

    private val _isSpeaking = MutableStateFlow(false)

    /** True from the moment an utterance is enqueued until shortly after the last one finished. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val activeUtterances = mutableSetOf<String>()
    private var speakingTailJob: Job? = null

    fun attach(context: Context) = recreate(context)

    fun detach() = recreate(appContext)

    fun speak(text: String, language: Language) {
        if (text.isBlank()) return
        val engine = tts ?: run { recreate(appContext); tts } ?: return
        if (!ready) {
            pending.addLast(text to language)
            return
        }
        val result = engine.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS voice for ${language.tag} unavailable ($result); speaking with the default voice")
        }
        val utteranceId = "lenslate-${System.nanoTime()}"
        utteranceStarted(utteranceId)
        if (engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) != TextToSpeech.SUCCESS) {
            utteranceFinished(utteranceId)
        }
    }

    fun stop() {
        pending.clear()
        tts?.stop()
        resetSpeaking()
    }

    fun shutdown() {
        pending.clear()
        tts?.shutdown()
        tts = null
        ready = false
        resetSpeaking()
    }

    private fun recreate(context: Context) {
        tts?.shutdown()
        ready = false
        // Utterances in flight on the old engine die with it; their callbacks will not come.
        resetSpeaking()
        val token = Any()
        initToken = token
        tts = TextToSpeech(context) { status ->
            // A callback from an engine that was already replaced must not touch state.
            if (initToken !== token) return@TextToSpeech
            ready = status == TextToSpeech.SUCCESS
            if (!ready) {
                Log.w(TAG, "TextToSpeech init failed: $status")
                pending.clear()
                return@TextToSpeech
            }
            while (pending.isNotEmpty()) {
                val (text, language) = pending.removeFirst()
                speak(text, language)
            }
        }.also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) = finished(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

                // Progress callbacks arrive on a binder thread; hop to main where state lives.
                private fun finished(utteranceId: String?) {
                    val id = utteranceId ?: return
                    scope.launch { utteranceFinished(id) }
                }
            })
        }
    }

    private fun utteranceStarted(id: String) {
        activeUtterances += id
        speakingTailJob?.cancel()
        speakingTailJob = null
        _isSpeaking.value = true
    }

    private fun utteranceFinished(id: String) {
        if (!activeUtterances.remove(id) || activeUtterances.isNotEmpty()) return
        speakingTailJob?.cancel()
        speakingTailJob = scope.launch {
            delay(speakingTailMs)
            if (activeUtterances.isEmpty()) _isSpeaking.value = false
        }
    }

    private fun resetSpeaking() {
        activeUtterances.clear()
        speakingTailJob?.cancel()
        speakingTailJob = null
        _isSpeaking.value = false
    }

    private companion object {
        const val TAG = "TranslationSpeaker"

        /** How long after the last utterance the microphone stays muted, for the echo to fade. */
        const val SPEAKING_TAIL_MS = 300L
    }
}
