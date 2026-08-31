package com.jpcottin.lenslate.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reacts to every translated utterance: speaks it aloud when speech output is enabled, and in
 * conversation mode reverses the language direction afterwards, so two people can talk across
 * the device without anyone touching the swap button.
 *
 * The swap waits for [isSpeaking] to fall back to false — [speak] raises it synchronously in
 * this same coroutine — so the direction turns around exactly when the half-duplex microphone
 * reopens, never while the reply is still being spoken. With speech output off the swap is
 * immediate. Read-mode utterances never swap: photographing a sign mid-conversation must not
 * turn the direction around.
 */
class ConversationDirector(
    private val translated: Flow<Utterance>,
    private val isSpeaking: StateFlow<Boolean>,
    private val speakEnabled: () -> Boolean,
    private val conversationMode: () -> Boolean,
    private val speak: (translation: String) -> Unit,
    private val swapLanguages: suspend () -> Unit,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        translated.collect { utterance ->
            if (speakEnabled()) speak(utterance.translation.orEmpty())
            if (utterance.kind != UtteranceKind.SPOKEN || !conversationMode()) return@collect
            isSpeaking.first { !it }
            // Re-check: the user may have turned conversation mode off during playback.
            if (conversationMode()) swapLanguages()
        }
    }
}
