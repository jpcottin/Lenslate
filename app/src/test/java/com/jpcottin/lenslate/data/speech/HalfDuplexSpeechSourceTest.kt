package com.jpcottin.lenslate.data.speech

import app.cash.turbine.test
import com.jpcottin.lenslate.domain.FakeSpeechSource
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.SpeechEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HalfDuplexSpeechSourceTest {
    private val real = FakeSpeechSource()
    private val muted = MutableStateFlow(false)
    private val source = HalfDuplexSpeechSource(real, muted)

    @Test
    fun unmuted_eventsPassThrough() = runTest {
        source.listen(Language.FRENCH).test {
            real.emit(SpeechEvent.Partial("Bonjour"))
            assertEquals(SpeechEvent.Partial("Bonjour"), awaitItem())
            real.emit(SpeechEvent.Final("Bonjour tout le monde"))
            assertEquals(SpeechEvent.Final("Bonjour tout le monde"), awaitItem())
            assertEquals(Language.FRENCH, real.lastLanguage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun muting_withdrawsThePartial_andUnmutingListensAgain() = runTest {
        source.listen(Language.FRENCH).test {
            real.emit(SpeechEvent.Partial("Bon"))
            assertEquals(SpeechEvent.Partial("Bon"), awaitItem())

            muted.value = true
            // The interim hypothesis is withdrawn so it does not linger on screen while muted.
            assertEquals(SpeechEvent.Partial(""), awaitItem())
            assertEquals(1, real.listenCalls)

            muted.value = false
            real.emit(SpeechEvent.Final("Bonjour"))
            assertEquals(SpeechEvent.Final("Bonjour"), awaitItem())
            // Unmuting started a fresh recognition, it did not resume the cancelled one.
            assertEquals(2, real.listenCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun gatedFlow_completesWhenTheDelegateCompletes() = runTest {
        source.listen(Language.FRENCH).test {
            real.emit(SpeechEvent.Ready)
            assertEquals(SpeechEvent.Ready, awaitItem())
            real.complete()
            awaitComplete()
        }
    }
}
