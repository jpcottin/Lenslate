package com.jpcottin.lenslate.ui.phone.read

import com.jpcottin.lenslate.domain.FakeTranslationEngine
import com.jpcottin.lenslate.domain.Frame
import com.jpcottin.lenslate.domain.FrameCapture
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.LiveTranslatorTest
import com.jpcottin.lenslate.domain.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Read screen's one event survives a screen recreated while the read ran. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadViewModelTest {
    private class FakeCapture : FrameCapture { override suspend fun capture() = Frame(LiveTranslatorTest.fakeBitmap()) }
    private class FakeRecognizer(val text: String) : TextRecognizer {
        override suspend fun recognize(frame: Frame, language: Language) = text
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun readDone_isDeliveredToACollectorThatSubscribesAfterTheRead() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val translator = LiveTranslator(engine = { FakeTranslationEngine() }, scope = this)
        val viewModel = ReadViewModel(translator, FakeRecognizer("SORTIE"))

        // Nobody collects: the Read screen is being recreated (rotation, fold, resize).
        viewModel.read(FakeCapture())
        advanceUntilIdle()

        assertEquals("SORTIE", withTimeout(1_000) { viewModel.readDone.first() })
    }
}
