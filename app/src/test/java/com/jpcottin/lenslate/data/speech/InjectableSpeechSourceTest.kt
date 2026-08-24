package com.jpcottin.lenslate.data.speech

import app.cash.turbine.test
import com.jpcottin.lenslate.domain.FakeSpeechSource
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.SpeechEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectableSpeechSourceTest {
    @Test
    fun withoutDelegate_onlyInjectedSentencesFlow() = runTest {
        InjectableSpeechSource(delegate = null).listen(Language.FRENCH).test {
            assertTrue(UtteranceInjector.inject("Bonjour"))
            assertEquals(SpeechEvent.Final("Bonjour"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withDelegate_realAndInjectedEventsAreMerged() = runTest {
        val real = FakeSpeechSource()
        InjectableSpeechSource(real).listen(Language.SPANISH).test {
            real.emit(SpeechEvent.Partial("Hola"))
            assertEquals(SpeechEvent.Partial("Hola"), awaitItem())
            UtteranceInjector.inject("Buenos días")
            assertEquals(SpeechEvent.Final("Buenos días"), awaitItem())
            real.emit(SpeechEvent.Final("Hola"))
            assertEquals(SpeechEvent.Final("Hola"), awaitItem())
            assertEquals(Language.SPANISH, real.lastLanguage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mergedFlow_completesWhenTheRealSourceCompletes() = runTest {
        val real = FakeSpeechSource()
        InjectableSpeechSource(real).listen(Language.FRENCH).test {
            real.emit(SpeechEvent.Ready)
            assertEquals(SpeechEvent.Ready, awaitItem())
            real.complete()
            awaitComplete()
        }
    }
}
