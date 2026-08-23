package com.jpcottin.lenslate.data.translate

import com.jpcottin.lenslate.BuildConfig
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.TranslationEngine
import com.jpcottin.lenslate.domain.TranslationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cloud translation through the Gemini API (`generateContent`), using the user's own API key
 * from the app settings. Offered as a higher-quality alternative to the on-device engine.
 */
class GeminiTranslationEngine(
    private val apiKey: () -> String,
    private val model: () -> String,
    private val baseUrl: String = BuildConfig.GEMINI_API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) : TranslationEngine {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        if (from == to || text.isBlank()) return text
        val key = apiKey().trim()
        if (key.isEmpty()) throw TranslationException("Add a Gemini API key in Settings to use the Gemini engine")
        val modelName = model().trim().ifEmpty { BuildConfig.GEMINI_DEFAULT_MODEL }

        val body = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(prompt(text, from, to))))),
            generationConfig = GenerationConfig(temperature = 0.2),
        )
        val request = Request.Builder()
            .url("${baseUrl}models/$modelName:generateContent")
            .header("x-goog-api-key", key)
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    if (!response.isSuccessful) {
                        throw TranslationException("Gemini error ${response.code}: ${errorMessage(raw)}")
                    }
                    parseTranslation(raw)
                }
            } catch (e: IOException) {
                throw TranslationException("Could not reach Gemini: ${e.message}", e)
            }
        }
    }

    private fun parseTranslation(raw: String): String {
        val parsed = runCatching { json.decodeFromString<GenerateContentResponse>(raw) }
            .getOrElse { throw TranslationException("Unexpected Gemini response", it) }
        val text = parsed.candidates?.firstOrNull()?.content?.parts
            ?.mapNotNull { it.text }?.joinToString("")?.trim()
        if (text.isNullOrEmpty()) {
            val reason = parsed.promptFeedback?.blockReason ?: parsed.candidates?.firstOrNull()?.finishReason
            throw TranslationException("Gemini returned no translation" + (reason?.let { " ($it)" } ?: ""))
        }
        return text
    }

    private fun errorMessage(raw: String): String =
        runCatching { json.decodeFromString<ErrorResponse>(raw).error?.message }.getOrNull()
            ?: raw.take(200)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun prompt(text: String, from: Language, to: Language): String =
            "Translate the following ${from.englishName} text into ${to.englishName}. " +
                "Reply with only the translation, without quotes or commentary.\n\n$text"
    }

    @Serializable
    internal data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
    )

    @Serializable
    internal data class Content(val parts: List<Part>, val role: String? = null)

    @Serializable
    internal data class Part(val text: String? = null)

    @Serializable
    internal data class GenerationConfig(val temperature: Double? = null)

    @Serializable
    internal data class GenerateContentResponse(
        val candidates: List<Candidate>? = null,
        val promptFeedback: PromptFeedback? = null,
    )

    @Serializable
    internal data class Candidate(val content: Content? = null, val finishReason: String? = null)

    @Serializable
    internal data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    internal data class ErrorResponse(val error: ErrorBody? = null)

    @Serializable
    internal data class ErrorBody(val code: Int? = null, val message: String? = null, val status: String? = null)
}
