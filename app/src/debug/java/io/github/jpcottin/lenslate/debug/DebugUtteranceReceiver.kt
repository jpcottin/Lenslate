package io.github.jpcottin.lenslate.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.jpcottin.lenslate.appContainer
import io.github.jpcottin.lenslate.data.camera.BitmapFrameCapture
import io.github.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug-only hooks to drive both modes from the shell — emulators have no usable microphone and
 * their camera shows a virtual scene, so this is how Listen and Read are exercised on an AVD and
 * in CI. Quote the whole command for the device shell, otherwise only the first word is delivered.
 *
 * Listen: inject a sentence as if it had been heard.
 * ```
 * adb shell "am broadcast -a io.github.jpcottin.lenslate.debug.UTTERANCE \
 *     -p io.github.jpcottin.lenslate --es text 'Bonjour tout le monde'"
 * ```
 * Read: OCR an image on the device as if the camera had captured it. Relative paths resolve
 * under the app's external files directory, which adb can push to without any permission.
 * ```
 * adb push docs/test-images/sign-fr.png /sdcard/Android/data/io.github.jpcottin.lenslate/files/sign-fr.png
 * adb shell "am broadcast -a io.github.jpcottin.lenslate.debug.READ_IMAGE \
 *     -p io.github.jpcottin.lenslate --es path sign-fr.png"
 * ```
 * Both actions accept optional `--es from fr --es to en` extras to pin the language pair first.
 */
class DebugUtteranceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val from = Language.fromCode(intent.getStringExtra(EXTRA_FROM))
        val to = Language.fromCode(intent.getStringExtra(EXTRA_TO))
        val action = intent.action
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        val path = intent.getStringExtra(EXTRA_PATH)?.trim().orEmpty()
        val file = if (path.startsWith("/")) File(path) else File(context.getExternalFilesDir(null), path)

        // One async result for the whole job: the work runs on the app scope and finishes it.
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (from != null || to != null) {
                    val current = container.settingsRepository.settings.first()
                    container.settingsRepository.setLanguages(from ?: current.from, to ?: current.to)
                    container.liveTranslator.setLanguages(from ?: current.from, to ?: current.to)
                }
                when (action) {
                    ACTION_UTTERANCE -> {
                        if (text.isEmpty()) {
                            Log.w(TAG, "Missing --es $EXTRA_TEXT")
                            return@launch
                        }
                        Log.i(TAG, "Injecting utterance: $text")
                        container.liveTranslator.submit(text)
                    }
                    ACTION_READ_IMAGE -> {
                        if (path.isEmpty()) {
                            Log.w(TAG, "Missing --es $EXTRA_PATH")
                            return@launch
                        }
                        Log.i(TAG, "Reading image: ${file.path}")
                        val capture = runCatching { BitmapFrameCapture.fromFile(file.path) }
                            .getOrElse { Log.w(TAG, "Cannot read ${file.path}", it); return@launch }
                        val recognized = container.liveTranslator.readText(capture, container.textRecognizer)
                        Log.i(TAG, "Recognized: ${recognized ?: "(nothing)"}")
                    }
                    else -> Log.w(TAG, "Unknown action $action")
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UTTERANCE = "io.github.jpcottin.lenslate.debug.UTTERANCE"
        const val ACTION_READ_IMAGE = "io.github.jpcottin.lenslate.debug.READ_IMAGE"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PATH = "path"
        const val EXTRA_FROM = "from"
        const val EXTRA_TO = "to"
        private const val TAG = "DebugUtteranceReceiver"
    }
}
