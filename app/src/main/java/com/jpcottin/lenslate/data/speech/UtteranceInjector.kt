package com.jpcottin.lenslate.data.speech

import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.SpeechEvent
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lets sentences be injected into the live pipeline as if they had been heard. Emulators have
 * no usable microphone, so debug builds expose this through an `adb shell am broadcast`
 * receiver, and instrumented tests call [inject] directly.
 */
object UtteranceInjector {
    private val _utterances = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val utterances: SharedFlow<String> = _utterances.asSharedFlow()

    fun inject(text: String): Boolean = _utterances.tryEmit(text)
}

/**
 * Merges a real [SpeechSource] with sentences from [UtteranceInjector]. The merged flow
 * completes when the real flow completes: the injector never completes on its own, and a plain
 * `merge` would keep a dead microphone looking alive ("Listening" forever after a fatal error).
 */
class InjectableSpeechSource(private val delegate: SpeechSource?) : SpeechSource {
    override fun listen(language: Language): Flow<SpeechEvent> {
        val injected: Flow<SpeechEvent> = UtteranceInjector.utterances.map { SpeechEvent.Final(it) }
        val real = delegate?.listen(language) ?: return injected
        return channelFlow {
            val injectorJob = launch { injected.collect { send(it) } }
            real.collect { send(it) }
            injectorJob.cancel()
        }
    }
}
