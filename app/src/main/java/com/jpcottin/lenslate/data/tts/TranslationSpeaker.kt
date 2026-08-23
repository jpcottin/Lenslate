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

    fun attach(context: Context) = recreate(context)

    fun detach() = recreate(appContext)

    fun speak(text: String, language: Language) {
        if (text.isBlank()) return
        val engine = tts ?: run { recreate(appContext); tts } ?: return
        if (!ready) {
            pending.addLast(text to language)
            return
        }
        engine.language = language.locale
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
        tts = TextToSpeech(context) { status ->
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
