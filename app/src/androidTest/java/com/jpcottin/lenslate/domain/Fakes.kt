package com.jpcottin.lenslate.domain

/** Deterministic engine for instrumented tests: "<text>" → "[<from>→<to>] <text>". */
class FakeTranslationEngine(var failWith: Throwable? = null) : TranslationEngine {
    override suspend fun translate(text: String, from: Language, to: Language): String {
        failWith?.let { throw it }
        return "[${from.code}→${to.code}] $text"
    }
}
