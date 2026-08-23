package io.github.jpcottin.lenslate.domain

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTranslatorTest {

    private fun TestScope.translator(
        engine: TranslationEngine = FakeTranslationEngine(),
        partials: Boolean = true,
    ) = LiveTranslator(
        engine = { engine },
        scope = this,
        partialTranslationEnabled = { partials },
        partialTranslationDelayMs = 100,
        maxUtterances = 3,
    )

    @Test
    fun start_listensInSourceLanguage_andPreparesEngine() = runTest {
        val source = FakeSpeechSource()
        val engine = FakeTranslationEngine()
        val translator = translator(engine)
        translator.setLanguages(Language.SPANISH, Language.GERMAN)

        translator.start(source)
        advanceUntilIdle()

        assertTrue(translator.state.value.isListening)
        assertEquals(1, source.listenCalls)
        assertEquals(Language.SPANISH, source.lastLanguage)
        assertEquals(1, engine.prepareCalls)
        assertFalse(translator.state.value.isPreparing)
        translator.stop()
    }

    @Test
    fun finalSentence_isTranslated_andEmitted() = runTest {
        val source = FakeSpeechSource()
        val translator = translator()
        translator.start(source)
        advanceUntilIdle()

        translator.translated.test {
            source.emit(SpeechEvent.Final("Bonjour"))
            advanceUntilIdle()

            val utterance = translator.state.value.latest!!
            assertEquals("Bonjour", utterance.source)
            assertEquals("[fr→en] Bonjour", utterance.translation)
            assertNull(utterance.error)
            assertEquals("[fr→en] Bonjour", awaitItem().translation)
        }
        translator.stop()
    }

    @Test
    fun partial_isTranslatedAfterDebounce_andClearedByFinal() = runTest {
        val source = FakeSpeechSource()
        val engine = FakeTranslationEngine()
        val translator = translator(engine)
        translator.start(source)
        advanceUntilIdle()

        source.emit(SpeechEvent.Partial("Bon"))
        advanceTimeBy(50)
        source.emit(SpeechEvent.Partial("Bonjour"))
        advanceTimeBy(50)
        // The first partial was superseded before its debounce elapsed.
        assertEquals("Bonjour", translator.state.value.partialSource)
        assertEquals("", translator.state.value.partialTranslation)

        advanceTimeBy(100)
        assertEquals("[fr→en] Bonjour", translator.state.value.partialTranslation)
        assertEquals(listOf("Bonjour"), engine.calls)

        source.emit(SpeechEvent.Final("Bonjour tout le monde"))
        advanceUntilIdle()
        assertEquals("", translator.state.value.partialSource)
        assertEquals("", translator.state.value.partialTranslation)
        assertEquals("[fr→en] Bonjour tout le monde", translator.state.value.latest?.translation)
        translator.stop()
    }

    @Test
    fun partialTranslation_isSkipped_whenDisabled() = runTest {
        val source = FakeSpeechSource()
        val engine = FakeTranslationEngine()
        val translator = translator(engine, partials = false)
        translator.start(source)
        advanceUntilIdle()

        source.emit(SpeechEvent.Partial("Bonjour"))
        advanceTimeBy(1_000)

        assertEquals("Bonjour", translator.state.value.partialSource)
        assertEquals("", translator.state.value.partialTranslation)
        assertTrue(engine.calls.isEmpty())
        translator.stop()
    }

    @Test
    fun engineFailure_isReportedOnTheUtterance() = runTest {
        val source = FakeSpeechSource()
        val translator = translator(FakeTranslationEngine(failWith = TranslationException("boom")))
        translator.start(source)
        advanceUntilIdle()

        source.emit(SpeechEvent.Final("Salut"))
        advanceUntilIdle()

        val utterance = translator.state.value.latest!!
        assertNull(utterance.translation)
        assertEquals("boom", utterance.error)
        assertEquals("boom", translator.state.value.error)
        translator.stop()
    }

    @Test
    fun blankFinal_isIgnored_andTranscriptIsCapped() = runTest {
        val source = FakeSpeechSource()
        val translator = translator()
        translator.start(source)
        advanceUntilIdle()

        source.emit(SpeechEvent.Final("   "))
        repeat(5) { source.emit(SpeechEvent.Final("phrase $it")) }
        advanceUntilIdle()

        val sources = translator.state.value.utterances.map { it.source }
        assertEquals(listOf("phrase 2", "phrase 3", "phrase 4"), sources)
        translator.stop()
    }

    @Test
    fun speechError_updatesState_withoutStoppingListening() = runTest {
        val source = FakeSpeechSource()
        val translator = translator()
        translator.start(source)
        advanceUntilIdle()

        source.emit(SpeechEvent.Error("Network error"))
        advanceUntilIdle()

        assertEquals("Network error", translator.state.value.error)
        assertTrue(translator.state.value.isListening)

        source.emit(SpeechEvent.Ready)
        advanceUntilIdle()
        assertNull(translator.state.value.error)
        translator.stop()
    }

    @Test
    fun stop_endsListening_andClearsPartials() = runTest {
        val source = FakeSpeechSource()
        val translator = translator()
        translator.start(source)
        advanceUntilIdle()
        source.emit(SpeechEvent.Partial("Bon"))
        advanceUntilIdle()

        translator.stop()
        advanceUntilIdle()

        assertFalse(translator.state.value.isListening)
        assertEquals("", translator.state.value.partialSource)
    }

    @Test
    fun setLanguages_whileListening_restartsOnSameSource() = runTest {
        val source = FakeSpeechSource()
        val translator = translator()
        translator.start(source)
        advanceUntilIdle()

        translator.setLanguages(Language.JAPANESE, Language.FRENCH)
        advanceUntilIdle()

        assertTrue(translator.state.value.isListening)
        assertEquals(2, source.listenCalls)
        assertEquals(Language.JAPANESE, source.lastLanguage)
        translator.stop()
    }

    @Test
    fun startWithAnotherSource_replacesTheFirst() = runTest {
        val phone = FakeSpeechSource()
        val glasses = FakeSpeechSource()
        val translator = translator()
        translator.start(phone)
        advanceUntilIdle()
        translator.start(glasses)
        advanceUntilIdle()

        assertTrue(translator.state.value.isListening)
        assertEquals(1, glasses.listenCalls)
        // The phone's flow was cancelled; its events no longer reach the pipeline.
        phone.emit(SpeechEvent.Final("ignored"))
        glasses.emit(SpeechEvent.Final("heard"))
        advanceUntilIdle()
        assertEquals(listOf("heard"), translator.state.value.utterances.map { it.source })
        translator.stop()
    }

    @Test
    fun submit_worksWithoutListening() = runTest {
        val translator = translator()
        translator.submit("Typed sentence")
        advanceUntilIdle()

        assertFalse(translator.state.value.isListening)
        assertEquals("[fr→en] Typed sentence", translator.state.value.latest?.translation)
    }

    @Test
    fun clear_dropsTranscript() = runTest {
        val translator = translator()
        translator.submit("Un")
        advanceUntilIdle()
        translator.clear()
        assertTrue(translator.state.value.utterances.isEmpty())
    }
}
