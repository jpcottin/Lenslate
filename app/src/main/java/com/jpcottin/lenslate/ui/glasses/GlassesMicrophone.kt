package com.jpcottin.lenslate.ui.glasses

import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The glasses' share of the listening session that both surfaces observe: it takes the session
 * over while the glasses activity is in front and, when the activity leaves, ends only a session
 * it still owns — the phone may have taken the microphone back in the meantime.
 *
 * [resolve] returns the microphone to listen with, or null when neither the glasses nor the phone
 * may record. It shows a permission prompt only when `mayAsk` is true.
 */
internal class GlassesMicrophone(
    private val translator: LiveTranslator,
    private val scope: CoroutineScope,
    private val resolve: suspend (mayAsk: Boolean) -> SpeechSource?,
) {
    private val _permissionDenied = MutableStateFlow(false)

    /** No microphone may be used; the user has to grant the permission and retry. */
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    private var source: SpeechSource? = null
    private var resolving: Job? = null
    private var inFront = false

    /**
     * The activity came to the front. A refused permission is not requested again from here:
     * coming back from the prompt restarts the activity, and asking on every start would loop.
     * A permission granted in the meantime (system settings) is still picked up.
     */
    fun onStart() {
        inFront = true
        listen(mayAsk = !permissionDenied.value)
    }

    fun onStop() {
        inFront = false
        val own = source ?: return
        if (translator.isListeningOn(own)) translator.stop()
    }

    fun toggle() {
        if (translator.state.value.isListening) translator.stop() else listen(mayAsk = true)
    }

    /** The user asked for the permission prompt again. */
    fun retry() = listen(mayAsk = true, refresh = true)

    private fun listen(mayAsk: Boolean, refresh: Boolean = false) {
        // The resolution in flight starts listening when it completes.
        if (resolving?.isActive == true) return
        val known = source
        if (known != null && !refresh) {
            start(known)
            return
        }
        resolving = scope.launch {
            val resolved = resolve(mayAsk)
            source = resolved
            _permissionDenied.value = resolved == null
            // The prompt can outlive the activity's time in front; the next start listens then.
            if (resolved != null && inFront) start(resolved)
        }
    }

    private fun start(own: SpeechSource) {
        if (!translator.isListeningOn(own)) translator.start(own)
    }
}
