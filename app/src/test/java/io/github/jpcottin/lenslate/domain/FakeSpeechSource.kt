package io.github.jpcottin.lenslate.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

/** Scripted speech source: tests push events with [emit]. */
class FakeSpeechSource : SpeechSource {
    private val events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    var listenCalls = 0
        private set
    var lastLanguage: Language? = null
        private set

    override fun listen(language: Language): Flow<SpeechEvent> = events.onStart {
        listenCalls++
        lastLanguage = language
    }

    suspend fun emit(event: SpeechEvent) = events.emit(event)
}

/** Deterministic engine: "<text>" → "[<from>→<to>] <text>", optionally failing or slow. */
class FakeTranslationEngine(
    var failWith: Throwable? = null,
    var delayMs: Long = 0,
) : TranslationEngine {
    val calls = mutableListOf<String>()
    var prepareCalls = 0

    override suspend fun prepare(from: Language, to: Language) {
        prepareCalls++
    }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        calls += text
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        failWith?.let { throw it }
        return "[${from.code}→${to.code}] $text"
    }
}
