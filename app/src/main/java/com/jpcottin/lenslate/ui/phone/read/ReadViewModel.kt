package com.jpcottin.lenslate.ui.phone.read

import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.lenslate.di.AppContainer
import com.jpcottin.lenslate.domain.FrameCapture
import com.jpcottin.lenslate.domain.LiveTranslationState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ReadViewModel(private val container: AppContainer) : ViewModel() {
    val live: StateFlow<LiveTranslationState> = container.liveTranslator.state

    private val _readDone = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the recognized text once a snapshot was read and queued for translation. */
    val readDone: SharedFlow<String> = _readDone.asSharedFlow()

    /** Live-camera capture bound by the Read screen; the ViewModel only holds it while the screen is shown. */
    var imageCapture: ImageCapture? = null

    fun read(capture: FrameCapture) {
        viewModelScope.launch {
            container.liveTranslator.readText(capture, container.textRecognizer)?.let { _readDone.tryEmit(it) }
        }
    }
}
