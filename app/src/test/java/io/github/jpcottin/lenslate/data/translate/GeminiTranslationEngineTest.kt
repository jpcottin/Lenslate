package io.github.jpcottin.lenslate.data.translate

import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.TranslationException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeminiTranslationEngineTest {
    private lateinit var server: MockWebServer
    private var apiKey = "test-key"
    private var model = "gemini-2.5-flash"

    private fun engine() = GeminiTranslationEngine(
        apiKey = { apiKey },
        model = { model },
        baseUrl = server.url("/v1beta/").toString(),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun translate_postsPrompt_andParsesCandidateText() = runTest {
        server.enqueue(
            MockResponse(
                body = """{"candidates":[{"content":{"parts":[{"text":"  Hello everyone\n"}],"role":"model"},"finishReason":"STOP"}]}""",
                headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
            )
        )

        val result = engine().translate("Bonjour tout le monde", Language.FRENCH, Language.ENGLISH)

        assertEquals("Hello everyone", result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", request.url.encodedPath)
        assertEquals("test-key", request.headers["x-goog-api-key"])
        val body = request.body!!.utf8()
        assertTrue(body.contains("Translate the following French text into English"))
        assertTrue(body.contains("Bonjour tout le monde"))
    }

    @Test
    fun translate_failsWithServerMessage_onHttpError() = runTest {
        server.enqueue(
            MockResponse(
                code = 400,
                body = """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""",
            )
        )
        try {
            engine().translate("Salut", Language.FRENCH, Language.ENGLISH)
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertEquals("Gemini error 400: API key not valid", e.message)
        }
    }

    @Test
    fun translate_failsWhenBlocked() = runTest {
        server.enqueue(MockResponse(body = """{"candidates":[],"promptFeedback":{"blockReason":"SAFETY"}}"""))
        try {
            engine().translate("Salut", Language.FRENCH, Language.ENGLISH)
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertEquals("Gemini returned no translation (SAFETY)", e.message)
        }
    }

    @Test
    fun translate_requiresApiKey_withoutCallingTheNetwork() = runTest {
        apiKey = " "
        try {
            engine().translate("Salut", Language.FRENCH, Language.ENGLISH)
            fail("expected TranslationException")
        } catch (e: TranslationException) {
            assertTrue(e.message!!.contains("API key"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun translate_shortCircuits_sameLanguageAndBlankText() = runTest {
        assertEquals("Salut", engine().translate("Salut", Language.FRENCH, Language.FRENCH))
        assertEquals("  ", engine().translate("  ", Language.FRENCH, Language.ENGLISH))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun blankModel_fallsBackToDefault() = runTest {
        model = ""
        server.enqueue(MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""))
        engine().translate("Salut", Language.FRENCH, Language.ENGLISH)
        assertTrue(server.takeRequest().url.encodedPath.contains("models/gemini-2.5-flash:"))
    }
}
