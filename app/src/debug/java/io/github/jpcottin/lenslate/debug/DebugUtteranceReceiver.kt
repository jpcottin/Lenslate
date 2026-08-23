package io.github.jpcottin.lenslate.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.jpcottin.lenslate.appContainer

/**
 * Debug-only hook to feed a sentence into the live pipeline from the shell — emulators have no
 * usable microphone, so this is how Listen mode is exercised on an AVD and in CI:
 *
 * ```
 * adb shell "am broadcast -a io.github.jpcottin.lenslate.debug.UTTERANCE \
 *     -p io.github.jpcottin.lenslate --es text 'Bonjour tout le monde'"
 * ```
 * (Quote the sentence for the device shell, otherwise only the first word is delivered.)
 */
class DebugUtteranceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            Log.w(TAG, "Missing --es $EXTRA_TEXT")
            return
        }
        Log.i(TAG, "Injecting utterance: $text")
        context.appContainer.liveTranslator.submit(text)
    }

    companion object {
        const val ACTION = "io.github.jpcottin.lenslate.debug.UTTERANCE"
        const val EXTRA_TEXT = "text"
        private const val TAG = "DebugUtteranceReceiver"
    }
}
