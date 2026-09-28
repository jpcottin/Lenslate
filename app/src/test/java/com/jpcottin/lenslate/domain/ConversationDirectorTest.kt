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
    private val voices = mutableListOf<Language>()
    private var direction = Language.FRENCH to Language.ENGLISH
    private var swaps = 0

    /** [speak] raises [isSpeaking] like the real TranslationSpeaker does, synchronously. */
    private fun TestScope.startDirector(): Job {
        val job = ConversationDirector(
            translated = translated,
            isSpeaking = isSpeaking,
            speakEnabled = { speakEnabled },
            conversationMode = { conversationMode },
            direction = { direction },
            speak = { text, language ->
                spoken += text
                voices += language
                isSpeaking.value = true
            },
            setDirection = { from, to ->
                direction = from to to
                swaps++
            },
        ).start(this)
        // A shared flow drops emissions that arrive before the collector has subscribed.
        advanceUntilIdle()
        return job
    }

    /** An utterance heard in the current direction, unless [heardIn] says otherwise. */
    private fun emit(
        translation: String = "Hello",
        kind: UtteranceKind = UtteranceKind.SPOKEN,
        heardIn: Pair<Language, Language> = direction,
    ) {
        translated.tryEmit(
            Utterance(
                id = 1,
                source = "Bonjour",
                translation = translation,
                kind = kind,
                from = heardIn.first,
                to = heardIn.second,
            ),
        )
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

    @Test
    fun translationIsSpoken_inTheLanguageItWasTranslatedInto() = runTest {
        speakEnabled = true
        val job = startDirector()

        // The direction has turned around since this sentence was heard.
        direction = Language.ENGLISH to Language.FRENCH
        emit("Hello", heardIn = Language.FRENCH to Language.ENGLISH)
        advanceUntilIdle()

        assertEquals(listOf(Language.ENGLISH), voices)
        job.cancel()
    }

    @Test
    fun secondSentenceOfATurn_doesNotTurnTheDirectionBack() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()
        val heardIn = Language.FRENCH to Language.ENGLISH

        // Two sentences said in a row; the second translation arrives after the first swap.
        emit("Hello", heardIn = heardIn)
        advanceUntilIdle()
        isSpeaking.value = false
        advanceUntilIdle()
        emit("How are you?", heardIn = heardIn)
        advanceUntilIdle()
        isSpeaking.value = false
        advanceUntilIdle()

        assertEquals(listOf("Hello", "How are you?"), spoken)
        assertEquals(listOf(Language.ENGLISH, Language.ENGLISH), voices)
        assertEquals(1, swaps)
        assertEquals(Language.ENGLISH to Language.FRENCH, direction)
        job.cancel()
    }

    @Test
    fun swapIsDropped_whenTheUserChangedLanguagesDuringPlayback() = runTest {
        speakEnabled = true
        conversationMode = true
        val job = startDirector()

        emit("Hello")
        advanceUntilIdle()
        direction = Language.SPANISH to Language.GERMAN
        isSpeaking.value = false
        advanceUntilIdle()

        assertEquals(0, swaps)
        assertEquals(Language.SPANISH to Language.GERMAN, direction)
        job.cancel()
    }
}
