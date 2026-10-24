package com.jpcottin.lenslate.ui.phone.read

import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.lenslate.di.AppContainer
import com.jpcottin.lenslate.domain.FrameCapture
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.TextRecognizer
import com.jpcottin.lenslate.domain.LiveTranslationState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ReadViewModel internal constructor(
    private val translator: LiveTranslator,
    private val recognizer: TextRecognizer,
) : ViewModel() {
    constructor(container: AppContainer) : this(container.liveTranslator, container.textRecognizer)

    val live: StateFlow<LiveTranslationState> = translator.state

    // A conflated channel, not a shared flow: the one event is kept for the next collector,
    // so a screen recreated (rotation, fold, resize) while the read ran still pops.
    private val _readDone = Channel<String>(Channel.CONFLATED)

    /** Emits the recognized text once a snapshot was read and queued for translation. */
    val readDone: Flow<String> = _readDone.receiveAsFlow()

    /** Live-camera capture bound by the Read screen; the ViewModel only holds it while the screen is shown. */
    var imageCapture: ImageCapture? = null

    fun read(capture: FrameCapture) {
        viewModelScope.launch {
            translator.readText(capture, recognizer)?.let { _readDone.trySend(it) }
        }
    }
}
