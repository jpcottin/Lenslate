package com.jpcottin.lenslate.data.tts

import android.content.Context
import android.content.ContextWrapper
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationSpeakerTest {

    /** Scripted engine: the test decides when it comes up and when an utterance ends. */
    private class FakeEngine(
        val context: Context,
        val onInit: (Boolean) -> Unit,
        val onFinished: (String) -> Unit,
    ) : SpeechEngine {
        val spoken = mutableListOf<Pair<String, Language>>()
        val utteranceIds = mutableListOf<String>()
        var isShutDown = false

        override fun speak(text: String, language: Language, utteranceId: String): Boolean {
            spoken += text to language
            utteranceIds += utteranceId
            return true
        }

        override fun stop() {}

        override fun shutdown() {
            isShutDown = true
        }

        fun finishAll() = utteranceIds.toList().forEach(onFinished)
    }

    private val phone = fakeContext()
    private val glasses = fakeContext()
    private val engines = mutableListOf<FakeEngine>()

    /** When set, every new engine reports this from inside its own creation. */
    private var initInPlace: Boolean? = null

    private fun TestScope.speaker() = TranslationSpeaker(
        appContext = phone,
        scope = this,
        speakingTailMs = TAIL_MS,
        initTimeoutMs = INIT_TIMEOUT_MS,
        engines = { context, onInit, onFinished ->
            FakeEngine(context, onInit, onFinished).also { engine ->
                engines += engine
                initInPlace?.let(onInit)
            }
        },
    )

    @Test
    fun utteranceWaitingForTheEngine_alreadyCountsAsSpeaking() = runTest {
        val speaker = speaker()

        speaker.speak("Hello", Language.ENGLISH)

        assertTrue(speaker.isSpeaking.value)
        assertTrue(engines.single().spoken.isEmpty())
        speaker.shutdown()
    }

    @Test
    fun queuedUtterances_areSpokenInOrder_onceTheEngineIsUp() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)
        speaker.speak("Bonjour", Language.FRENCH)

        engines.single().onInit(true)

        assertEquals(listOf("Hello" to Language.ENGLISH, "Bonjour" to Language.FRENCH), engines.single().spoken)
        assertTrue(speaker.isSpeaking.value)

        engines.single().finishAll()
        runCurrent()
        assertTrue(speaker.isSpeaking.value)
        advanceTimeBy(TAIL_MS + 1)
        assertFalse(speaker.isSpeaking.value)
        speaker.shutdown()
    }

    @Test
    fun failedEngine_dropsTheQueue_andReleasesTheMicrophone() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)

        engines.single().onInit(false)

        assertFalse(speaker.isSpeaking.value)
        speaker.shutdown()
    }

    @Test
    fun failedEngine_isReplacedByTheNextUtterance() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)
        engines.single().onInit(false)

        speaker.speak("Bonjour", Language.FRENCH)
        assertEquals(2, engines.size)
        assertTrue(engines[0].isShutDown)
        engines[1].onInit(true)

        assertEquals(listOf("Bonjour" to Language.FRENCH), engines[1].spoken)
        speaker.shutdown()
    }

    @Test
    fun engineFailingInsideItsCreation_neverHoldsTheMicrophone() = runTest {
        initInPlace = false
        val speaker = speaker()

        speaker.speak("Hello", Language.ENGLISH)
        speaker.speak("Bonjour", Language.FRENCH)
        advanceUntilIdle()

        assertFalse(speaker.isSpeaking.value)
        assertTrue(engines.all { it.spoken.isEmpty() })
        speaker.shutdown()
    }

    @Test
    fun engineThatNeverAnswers_isGivenUpOn() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)

        advanceTimeBy(INIT_TIMEOUT_MS - 1)
        assertTrue(speaker.isSpeaking.value)
        advanceTimeBy(2)
        assertFalse(speaker.isSpeaking.value)

        // An answer that comes after all is ignored; the next utterance starts over.
        engines.single().onInit(true)
        assertTrue(engines.single().spoken.isEmpty())
        speaker.speak("Bonjour", Language.FRENCH)
        assertEquals(2, engines.size)
        speaker.shutdown()
    }

    @Test
    fun attachingTheGlasses_keepsWhatWaitsForAnEngine() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)

        speaker.attach(glasses)

        assertTrue(speaker.isSpeaking.value)
        assertTrue(engines[0].isShutDown)
        assertSame(glasses, engines[1].context)

        // The replaced engine answers late: nothing may be spoken through it.
        engines[0].onInit(true)
        assertTrue(engines[0].spoken.isEmpty())

        engines[1].onInit(true)
        assertEquals(listOf("Hello" to Language.ENGLISH), engines[1].spoken)
        speaker.shutdown()
    }

    @Test
    fun attachingTheGlasses_endsWhatTheOldEngineWasSaying() = runTest {
        val speaker = speaker()
        speaker.attach(phone)
        engines.single().onInit(true)
        speaker.speak("Hello", Language.ENGLISH)
        assertTrue(speaker.isSpeaking.value)

        speaker.attach(glasses)

        assertFalse(speaker.isSpeaking.value)
        speaker.shutdown()
    }

    @Test
    fun stop_dropsTheQueue() = runTest {
        val speaker = speaker()
        speaker.speak("Hello", Language.ENGLISH)

        speaker.stop()
        assertFalse(speaker.isSpeaking.value)

        engines.single().onInit(true)
        assertTrue(engines.single().spoken.isEmpty())
        speaker.shutdown()
    }

    @Test
    fun blankText_isIgnored() = runTest {
        val speaker = speaker()

        speaker.speak("  ", Language.ENGLISH)

        assertFalse(speaker.isSpeaking.value)
        assertTrue(engines.isEmpty())
    }

    private companion object {
        const val TAIL_MS = 300L
        const val INIT_TIMEOUT_MS = 5_000L

        /** android.content.Context is a stub on the JVM; allocate one without running the stub constructor. */
        fun fakeContext(): Context {
            val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val unsafe = unsafeField.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(ContextWrapper::class.java) as Context
        }
    }
}
