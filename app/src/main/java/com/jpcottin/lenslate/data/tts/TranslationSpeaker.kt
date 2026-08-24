package com.jpcottin.lenslate.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.jpcottin.lenslate.domain.Language

/**
 * Speaks translations with Android text-to-speech.
 *
 * The [TextToSpeech] instance is created from whichever context is attached: the glasses
 * activity attaches itself so audio goes to the glasses' speakers, and detaches when it stops,
 * which reverts to the phone (a Bluetooth headset pairing still routes that to the glasses).
 */
class TranslationSpeaker(private val appContext: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Language>>()

    /** Identity of the engine currently being initialized; stale init callbacks are ignored. */
    private var initToken: Any? = null

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
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "lenslate-${System.nanoTime()}")
    }

    fun stop() {
        pending.clear()
        tts?.stop()
    }

    fun shutdown() {
        pending.clear()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun recreate(context: Context) {
        tts?.shutdown()
        ready = false
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
        }
    }

    private companion object {
        const val TAG = "TranslationSpeaker"
    }
}
