package com.jpcottin.lenslate.ui.glasses

import com.jpcottin.lenslate.domain.FakeSpeechSource
import com.jpcottin.lenslate.domain.FakeTranslationEngine
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlassesMicrophoneTest {

    private val phoneMic = FakeSpeechSource()
    private val glassesMic = FakeSpeechSource()

    /** What the permission flow answers; null means denied on the glasses and on the phone. */
    private var granted: SpeechSource? = glassesMic

    /** The `mayAsk` argument of every resolution, in order. */
    private val resolutions = mutableListOf<Boolean>()

    private fun TestScope.translator() = LiveTranslator(engine = { FakeTranslationEngine() }, scope = this)

    private fun TestScope.microphone(
        translator: LiveTranslator,
        resolve: suspend (Boolean) -> SpeechSource? = { granted },
    ) = GlassesMicrophone(translator, this) { mayAsk ->
        resolutions += mayAsk
        resolve(mayAsk)
    }

    @Test
    fun onStart_listensOnTheGlasses() = runTest {
        val translator = translator()
        val microphone = microphone(translator)

        microphone.onStart()
        advanceUntilIdle()

        assertTrue(translator.isListeningOn(glassesMic))
        assertEquals(1, glassesMic.listenCalls)
        assertFalse(microphone.permissionDenied.value)
        microphone.onStop()
    }

    @Test
    fun onStart_takesTheMicrophoneOverFromThePhone() = runTest {
        val translator = translator()
        val microphone = microphone(translator)
        translator.start(phoneMic)
        advanceUntilIdle()

        microphone.onStart()
        advanceUntilIdle()

        assertTrue(translator.state.value.isListening)
        assertTrue(translator.isListeningOn(glassesMic))
        assertEquals(1, glassesMic.listenCalls)
        microphone.onStop()
    }

    @Test
    fun onStop_endsTheGlassesSession() = runTest {
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()

        microphone.onStop()
        advanceUntilIdle()

        assertFalse(translator.state.value.isListening)
    }

    @Test
    fun onStop_leavesASessionThePhoneTookBack() = runTest {
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()
        translator.start(phoneMic)
        advanceUntilIdle()

        microphone.onStop()
        advanceUntilIdle()

        assertTrue(translator.state.value.isListening)
        assertTrue(translator.isListeningOn(phoneMic))
        translator.stop()
    }

    @Test
    fun restart_whileListeningOnTheGlasses_keepsTheSession() = runTest {
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()

        microphone.onStart()
        advanceUntilIdle()

        assertEquals(1, glassesMic.listenCalls)
        assertEquals(listOf(true), resolutions)
        microphone.onStop()
    }

    @Test
    fun onStart_afterADenial_doesNotAskAgain() = runTest {
        granted = null
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()
        assertTrue(microphone.permissionDenied.value)

        microphone.onStop()
        microphone.onStart()
        advanceUntilIdle()

        assertEquals(listOf(true, false), resolutions)
        assertTrue(microphone.permissionDenied.value)
        assertFalse(translator.state.value.isListening)
    }

    @Test
    fun onStart_afterADenial_usesAPermissionGrantedMeanwhile() = runTest {
        granted = null
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()
        microphone.onStop()

        // Granted from the system settings while the activity was away.
        granted = glassesMic
        microphone.onStart()
        advanceUntilIdle()

        assertEquals(listOf(true, false), resolutions)
        assertFalse(microphone.permissionDenied.value)
        assertTrue(translator.isListeningOn(glassesMic))
        microphone.onStop()
    }

    @Test
    fun retry_afterADenial_asksAgain() = runTest {
        granted = null
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()

        granted = glassesMic
        microphone.retry()
        advanceUntilIdle()

        assertEquals(listOf(true, true), resolutions)
        assertFalse(microphone.permissionDenied.value)
        assertTrue(translator.isListeningOn(glassesMic))
        microphone.onStop()
    }

    @Test
    fun restart_whileThePromptIsShowing_doesNotAskTwice() = runTest {
        val answer = CompletableDeferred<SpeechSource?>()
        val translator = translator()
        val microphone = microphone(translator) { answer.await() }
        microphone.onStart()
        advanceUntilIdle()

        // The permission prompt takes the front, then hands it back.
        microphone.onStop()
        microphone.onStart()
        advanceUntilIdle()
        answer.complete(glassesMic)
        advanceUntilIdle()

        assertEquals(listOf(true), resolutions)
        assertEquals(1, glassesMic.listenCalls)
        assertTrue(translator.isListeningOn(glassesMic))
        microphone.onStop()
    }

    @Test
    fun permissionAnsweredAfterStop_waitsForTheNextStart() = runTest {
        val answer = CompletableDeferred<SpeechSource?>()
        val translator = translator()
        val microphone = microphone(translator) { answer.await() }
        microphone.onStart()
        advanceUntilIdle()
        microphone.onStop()

        answer.complete(glassesMic)
        advanceUntilIdle()

        assertFalse(translator.state.value.isListening)
        assertEquals(0, glassesMic.listenCalls)

        microphone.onStart()
        advanceUntilIdle()

        assertEquals(listOf(true), resolutions)
        assertTrue(translator.isListeningOn(glassesMic))
        microphone.onStop()
    }

    @Test
    fun toggle_stopsAndResumesListening() = runTest {
        val translator = translator()
        val microphone = microphone(translator)
        microphone.onStart()
        advanceUntilIdle()

        microphone.toggle()
        advanceUntilIdle()
        assertFalse(translator.state.value.isListening)

        microphone.toggle()
        advanceUntilIdle()
        assertTrue(translator.isListeningOn(glassesMic))
        assertEquals(listOf(true), resolutions)
        microphone.onStop()
    }
}
