package com.jpcottin.lenslate.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.SpeechEvent
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * [SpeechSource] backed by Android's [SpeechRecognizer].
 *
 * Pass the projected activity as [context] to capture from the AI glasses' microphone, or a
 * phone context to use the phone's. On-device recognition is preferred when available (no
 * audio leaves the device); it falls back to the default recognition service otherwise, or when
 * the on-device recognizer lacks the requested language.
 *
 * Android's recognizer stops after each sentence, so this source restarts it automatically to
 * provide continuous listening until the flow is cancelled.
 */
class AndroidSpeechSource(
    private val context: Context,
    private val preferOnDevice: Boolean = true,
) : SpeechSource {

    override fun listen(language: Language): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error("Speech recognition is not available on this device", fatal = true))
            close()
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        var useOnDevice = preferOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        var recognizer: SpeechRecognizer? = null
        var closed = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        fun startListening() {
            if (closed) return
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice)
            recognizer?.startListening(intent)
        }

        fun restart(delayMs: Long) {
            handler.postDelayed({ startListening() }, delayMs)
        }

        lateinit var createRecognizer: () -> Unit

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) trySend(SpeechEvent.Partial(text))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                val best = if (confidences != null && confidences.size == matches.size && matches.isNotEmpty()) {
                    matches[confidences.indices.maxBy { confidences[it] }]
                } else {
                    matches.firstOrNull()
                }
                if (!best.isNullOrBlank()) trySend(SpeechEvent.Final(best))
                restart(0)
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Recognizer error $error (onDevice=$useOnDevice)")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(0)

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> restart(400)

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        trySend(SpeechEvent.Error("Microphone permission is missing", fatal = true))
                        close()
                    }

                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        if (useOnDevice) {
                            // The on-device recognizer lacks this language: fall back to the
                            // default recognition service, which can use the network.
                            useOnDevice = false
                            trySend(SpeechEvent.Error("${language.nativeName} is not available offline; using online recognition"))
                            createRecognizer()
                            restart(200)
                        } else {
                            trySend(SpeechEvent.Error("${language.nativeName} speech recognition is not supported", fatal = true))
                            close()
                        }
                    }

                    else -> {
                        trySend(SpeechEvent.Error(describe(error)))
                        restart(1000)
                    }
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        createRecognizer = {
            recognizer?.destroy()
            recognizer = if (useOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }.also { it.setRecognitionListener(listener) }
        }

        createRecognizer()
        startListening()

        awaitClose {
            closed = true
            handler.removeCallbacksAndMessages(null)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }.flowOn(Dispatchers.Main.immediate) // SpeechRecognizer must be driven from the main thread.

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Recognition service disconnected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many recognition requests"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Cannot check language support"
        else -> "Speech recognition error $error"
    }

    private companion object {
        const val TAG = "AndroidSpeechSource"
    }
}
