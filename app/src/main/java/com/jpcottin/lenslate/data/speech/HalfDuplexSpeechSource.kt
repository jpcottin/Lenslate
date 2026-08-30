package com.jpcottin.lenslate.data.speech

import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.SpeechEvent
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Half-duplex gate: suspends [delegate] while [muted] is true, so the microphone does not hear
 * the app's own text-to-speech and feed the spoken translation back into the pipeline.
 *
 * While muted the delegate's flow is cancelled outright (the recognizer is destroyed, nothing is
 * captured); when unmuted the delegate is listened to afresh. Whatever the user says during
 * playback is lost — the standard half-duplex trade-off. The merged flow itself stays alive
 * across mutes, so [com.jpcottin.lenslate.domain.LiveTranslator] keeps showing "Listening".
 */
class HalfDuplexSpeechSource(
    private val delegate: SpeechSource,
    private val muted: StateFlow<Boolean>,
) : SpeechSource {
    override fun listen(language: Language): Flow<SpeechEvent> = channelFlow {
        muted.collectLatest { isMuted ->
            // Once closed (the delegate ended the stream), later mute flips must not send.
            if (isClosedForSend) return@collectLatest
            if (isMuted) {
                // The recognizer was cancelled mid-sentence; drop its stale interim hypothesis.
                send(SpeechEvent.Partial(""))
            } else {
                delegate.listen(language).collect { send(it) }
                // The delegate completed on its own (fatal error): end the gated flow too,
                // instead of keeping a dead microphone looking alive until the next mute.
                close()
            }
        }
    }
}
