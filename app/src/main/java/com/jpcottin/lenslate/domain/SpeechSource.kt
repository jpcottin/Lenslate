package com.jpcottin.lenslate.domain

import kotlinx.coroutines.flow.Flow

sealed interface SpeechEvent {
    /** The recognizer is ready and listening. */
    data object Ready : SpeechEvent

    /** An interim hypothesis for the sentence currently being spoken. */
    data class Partial(val text: String) : SpeechEvent

    /** A finished sentence. */
    data class Final(val text: String) : SpeechEvent

    /**
     * Something went wrong. Non-fatal errors (no match, timeout...) are followed by the source
     * restarting on its own; a fatal error ends the flow.
     */
    data class Error(val message: String, val fatal: Boolean = false) : SpeechEvent
}

/** A stream of recognized speech, for example the glasses' microphone or the phone's. */
interface SpeechSource {
    /**
     * Cold flow: recognition starts when collected and keeps going, sentence after sentence,
     * until the collector cancels.
     */
    fun listen(language: Language): Flow<SpeechEvent>
}
