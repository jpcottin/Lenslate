package com.jpcottin.lenslate.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDirectorTest {

    private val translated = MutableSharedFlow<Utterance>(extraBufferCapacity = 16)
    private val isSpeaking = MutableStateFlow(false)
    private var speakEnabled = false
    private var conversationMode = false
    private val spoken = mutableListOf<String>()
    private var swaps = 0

    /** [speak] raises [isSpeaking] like the real TranslationSpeaker does, synchronously. */
    private fun TestScope.startDirector(): Job {
        val job = ConversationDirector(
            translated = translated,
            isSpeaking = isSpeaking,
            speakEnabled = { speakEnabled },
            conversationMode = { conversationMode },
            speak = { text ->
                spoken += text
                isSpeaking.value = true
            },
            swapLanguages = { swaps++ },
        ).start(this)
        // A shared flow drops emissions that arrive before the collector has subscribed.
        advanceUntilIdle()
        return job
    }

    private fun emit(translation: String = "Hello", kind: UtteranceKind = UtteranceKind.SPOKEN) {
        translated.tryEmit(Utterance(id = 1, source = "Bonjour", translation = translation, kind = kind))
    }

    @Test
    fun speaksTranslations_whenEnabled_withoutConversationMode() = runTest {
        speakEnabled = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()

        assertEquals(listOf("Hello"), spoken)
        assertEquals(0, swaps)
        job.cancel()
    }

    @Test
    fun staysSilent_whenSpeechIsDisabled() = runTest {
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), spoken)
        assertEquals(0, swaps)
        job.cancel()
    }

    @Test
    fun conversationMode_swapsImmediately_whenNothingIsSpoken() = runTest {
        conversationMode = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()

        assertEquals(1, swaps)
        job.cancel()
    }

    @Test
    fun conversationMode_waitsForPlaybackToEnd_beforeSwapping() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()
        assertEquals(listOf("Hello"), spoken)
        assertEquals(0, swaps)

        isSpeaking.value = false
        advanceUntilIdle()
        assertEquals(1, swaps)
        job.cancel()
    }

    @Test
    fun readUtterance_isSpoken_butNeverSwaps() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()

        emit("Exit", kind = UtteranceKind.READ)
        advanceUntilIdle()
        isSpeaking.value = false
        advanceUntilIdle()

        assertEquals(listOf("Exit"), spoken)
        assertEquals(0, swaps)
        job.cancel()
    }

    @Test
    fun swapIsDropped_whenModeIsTurnedOffDuringPlayback() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()
        conversationMode = false
        isSpeaking.value = false
        advanceUntilIdle()

        assertEquals(0, swaps)
        job.cancel()
    }

    @Test
    fun eachSentenceSwapsOnce_inOrder() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()
        isSpeaking.value = false
        advanceUntilIdle()
        emit("Bonjour")
        advanceUntilIdle()
        isSpeaking.value = false
        advanceUntilIdle()

        assertEquals(listOf("Hello", "Bonjour"), spoken)
        assertEquals(2, swaps)
        job.cancel()
    }
}
