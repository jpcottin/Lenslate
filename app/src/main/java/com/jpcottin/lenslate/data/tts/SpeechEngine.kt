package com.jpcottin.lenslate.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jpcottin.lenslate.domain.Language

/** The part of a text-to-speech engine [TranslationSpeaker] relies on. */
internal interface SpeechEngine {
    /** Queues [text] for playback in [language]; false when the engine refused it. */
    fun speak(text: String, language: Language, utteranceId: String): Boolean

    fun stop()

    fun shutdown()
}

internal fun interface SpeechEngineFactory {
    /**
     * Creates an engine whose audio goes to [context]'s device. [onInit] reports once whether the
     * engine is usable, possibly before this function returns; [onFinished] reports every
     * utterance that ended, however it ended, on any thread.
     */
    fun create(
        context: Context,
        onInit: (ready: Boolean) -> Unit,
        onFinished: (utteranceId: String) -> Unit,
    ): SpeechEngine
}

/** Android's [TextToSpeech]. */
internal class AndroidSpeechEngine(
    context: Context,
    onInit: (ready: Boolean) -> Unit,
    onFinished: (utteranceId: String) -> Unit,
) : SpeechEngine {

    private val tts = TextToSpeech(context) { status ->
        if (status != TextToSpeech.SUCCESS) Log.w(TAG, "TextToSpeech init failed: $status")
        onInit(status == TextToSpeech.SUCCESS)
    }.also { engine ->
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)

            private fun finished(utteranceId: String?) {
                onFinished(utteranceId ?: return)
            }
        })
    }

    override fun speak(text: String, language: Language, utteranceId: String): Boolean {
        val result = tts.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS voice for ${language.tag} unavailable ($result); speaking with the default voice")
        }
        return tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() = tts.shutdown()

    private companion object {
        const val TAG = "TranslationSpeaker"
    }
}
