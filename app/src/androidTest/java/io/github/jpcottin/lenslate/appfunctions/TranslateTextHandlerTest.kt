package io.github.jpcottin.lenslate.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import io.github.jpcottin.lenslate.data.settings.Settings
import io.github.jpcottin.lenslate.domain.EngineKind
import io.github.jpcottin.lenslate.domain.FakeTranslationEngine
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.TranslationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Runs on a device: AppFunction exceptions carry an android.os.Bundle, which is a stub on the JVM. */
@RunWith(AndroidJUnit4::class)
class TranslateTextHandlerTest {
    private val settings = Settings(from = Language.GERMAN, to = Language.JAPANESE, engine = EngineKind.ON_DEVICE)

    @Test
    fun usesAppLanguagePair_whenCodesAreEmpty() = runTest {
        val result = TranslateTextHandler(settings, FakeTranslationEngine()).handle("Hallo", "", "")
        assertEquals("Hallo", result.sourceText)
        assertEquals("[de→ja] Hallo", result.translatedText)
        assertEquals("de", result.fromLanguage)
        assertEquals("ja", result.toLanguage)
        assertEquals("on-device", result.engine)
    }

    @Test
    fun explicitCodes_overrideSettings_andEngineNameFollowsSettings() = runTest {
        val gemini = settings.copy(engine = EngineKind.GEMINI)
        val result = TranslateTextHandler(gemini, FakeTranslationEngine()).handle("Hello", "EN", " fr ")
        assertEquals("[en→fr] Hello", result.translatedText)
        assertEquals("gemini", result.engine)
    }

    @Test
    fun blankText_isInvalid() = runTest {
        try {
            TranslateTextHandler(settings, FakeTranslationEngine()).handle("  ", "", "")
            fail()
        } catch (e: AppFunctionInvalidArgumentException) {
            assertTrue(e.message!!.contains("text"))
        }
    }

    @Test
    fun unknownLanguage_isInvalid() = runTest {
        try {
            TranslateTextHandler(settings, FakeTranslationEngine()).handle("Hi", "xx", "")
            fail()
        } catch (e: AppFunctionInvalidArgumentException) {
            assertTrue(e.message!!.contains("fromLanguage"))
            assertTrue(e.message!!.contains("xx"))
        }
    }

    @Test
    fun engineFailure_isReportedAsAppUnknown() = runTest {
        val engine = FakeTranslationEngine(failWith = TranslationException("Add a Gemini API key"))
        try {
            TranslateTextHandler(settings, engine).handle("Hi", "", "")
            fail()
        } catch (e: AppFunctionAppUnknownException) {
            assertEquals("Add a Gemini API key", e.message)
        }
    }
}
