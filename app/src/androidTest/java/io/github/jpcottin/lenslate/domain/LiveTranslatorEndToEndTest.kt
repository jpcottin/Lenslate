package io.github.jpcottin.lenslate.domain

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jpcottin.lenslate.appContainer
import io.github.jpcottin.lenslate.data.speech.InjectableSpeechSource
import io.github.jpcottin.lenslate.data.speech.UtteranceInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real app container on the device: a sentence injected into the pipeline must come
 * out translated by the on-device ML Kit engine (downloading the model if the emulator has
 * network access).
 */
@RunWith(AndroidJUnit4::class)
class LiveTranslatorEndToEndTest {
    private val container = ApplicationProvider.getApplicationContext<android.content.Context>().appContainer

    @After
    fun tearDown() = runBlocking(Dispatchers.Main) {
        container.liveTranslator.stop()
        container.liveTranslator.clear()
    }

    @Test
    fun injectedSentence_isTranslatedOnDevice() = runBlocking {
        withContext(Dispatchers.Main) {
            container.settingsRepository.setLanguages(Language.FRENCH, Language.ENGLISH)
            container.settingsRepository.setEngine(EngineKind.ON_DEVICE)
            container.liveTranslator.setLanguages(Language.FRENCH, Language.ENGLISH)
            // No microphone: listen only to injected utterances.
            container.liveTranslator.start(InjectableSpeechSource(delegate = null))
        }
        // Model download on first run can take a while on a cold emulator.
        withTimeout(180_000) {
            container.liveTranslator.state.first { !it.isPreparing }
        }
        assertTrue(UtteranceInjector.inject("Bonjour tout le monde"))

        val utterance = withTimeout(60_000) {
            container.liveTranslator.state.first { s -> s.latest?.let { it.translation != null || it.error != null } == true }.latest!!
        }
        assertEquals("Bonjour tout le monde", utterance.source)
        assertEquals("Translation failed: ${utterance.error}", null, utterance.error)
        assertTrue("Unexpected translation: ${utterance.translation}", utterance.translation!!.contains("hello", ignoreCase = true))
    }
}
