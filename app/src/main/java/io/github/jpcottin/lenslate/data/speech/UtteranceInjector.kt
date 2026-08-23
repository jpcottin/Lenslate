package io.github.jpcottin.lenslate.data.speech

import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.SpeechEvent
import io.github.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

/** Merges a real [SpeechSource] with sentences from [UtteranceInjector]. */
class InjectableSpeechSource(private val delegate: SpeechSource?) : SpeechSource {
    override fun listen(language: Language): Flow<SpeechEvent> {
        val injected: Flow<SpeechEvent> = UtteranceInjector.utterances.map { SpeechEvent.Final(it) }
        val real = delegate?.listen(language) ?: return injected
        return merge(real, injected)
    }
}
