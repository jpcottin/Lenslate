package com.jpcottin.lenslate.data.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jpcottin.lenslate.appContainer
import com.jpcottin.lenslate.data.camera.BitmapFrameCapture
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Frame
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Renders text into a bitmap and checks ML Kit reads it back — no camera needed. */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognizerTest {
    private val container = ApplicationProvider.getApplicationContext<android.content.Context>().appContainer

    private fun sign(vararg lines: String, size: Float = 96f): Bitmap {
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = size
            isFakeBoldText = true
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 80f, 200f + i * 160f, paint) }
        return bitmap
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.Main) {
        container.liveTranslator.clear()
    }

    @Test
    fun recognizesLatinText() = runBlocking {
        val text = MlKitTextRecognizer().recognize(Frame(sign("SORTIE DE SECOURS", "Ne pas obstruer")), Language.FRENCH)
        assertTrue("got: $text", text.contains("SORTIE", ignoreCase = true))
        assertTrue("got: $text", text.contains("obstruer", ignoreCase = true))
    }

    @Test
    fun blankImage_yieldsNoText() = runBlocking {
        val text = MlKitTextRecognizer().recognize(Frame(sign()), Language.ENGLISH)
        assertEquals("", text.trim())
    }

    @Test
    fun readText_endToEnd_translatesTheSign() = runBlocking {
        withContext(Dispatchers.Main) {
            container.settingsRepository.setLanguages(Language.FRENCH, Language.ENGLISH)
            container.settingsRepository.setEngine(EngineKind.ON_DEVICE)
            container.liveTranslator.setLanguages(Language.FRENCH, Language.ENGLISH)
        }
        val recognized = withTimeout(60_000) {
            container.liveTranslator.readText(BitmapFrameCapture(sign("SORTIE DE SECOURS")), container.textRecognizer)
        }
        assertNotNull("error: ${container.liveTranslator.state.value.error}", recognized)
        val utterance = withTimeout(180_000) {
            container.liveTranslator.state.first { s -> s.latest?.let { it.translation != null || it.error != null } == true }.latest!!
        }
        assertEquals(null, utterance.error)
        assertTrue("got: ${utterance.translation}", utterance.translation!!.contains("exit", ignoreCase = true))
    }
}
