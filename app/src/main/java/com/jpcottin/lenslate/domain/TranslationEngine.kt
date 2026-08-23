package com.jpcottin.lenslate.domain

/** Which backend translates text. */
enum class EngineKind {
    /** ML Kit on-device translation: private, offline once the models are downloaded. */
    ON_DEVICE,

    /** Gemini API (cloud): higher quality, needs a user-provided API key and network. */
    GEMINI,
}

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface TranslationEngine {
    /**
     * Warms up the engine for a language pair (for example downloads ML Kit models).
     * Translating without calling this first still works; it is only slower.
     */
    suspend fun prepare(from: Language, to: Language) {}

    /** Translates [text] from [from] to [to]. Throws [TranslationException] on failure. */
    suspend fun translate(text: String, from: Language, to: Language): String
}
