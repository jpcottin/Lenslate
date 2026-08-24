package com.jpcottin.lenslate.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

/** Scripted speech source: tests push events with [emit] and can [complete] the stream. */
class FakeSpeechSource : SpeechSource {
    private val channel = Channel<SpeechEvent>(Channel.UNLIMITED)
    var listenCalls = 0
        private set
    var lastLanguage: Language? = null
        private set

    override fun listen(language: Language): Flow<SpeechEvent> = channel.receiveAsFlow().onStart {
        listenCalls++
        lastLanguage = language
    }

    suspend fun emit(event: SpeechEvent) = channel.send(event)

    /** Ends the stream, like a real source closing after a fatal error. */
    fun complete() {
        channel.close()
    }
}

/** Deterministic engine: "<text>" → "[<from>→<to>] <text>", optionally failing or slow. */
class FakeTranslationEngine(
    var failWith: Throwable? = null,
    var delayMs: Long = 0,
) : TranslationEngine {
    val calls = mutableListOf<String>()
    val delaysByText = mutableMapOf<String, Long>()
    var prepareCalls = 0

    override suspend fun prepare(from: Language, to: Language) {
        prepareCalls++
    }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        calls += text
        val delay = delaysByText[text] ?: delayMs
        if (delay > 0) kotlinx.coroutines.delay(delay)
        failWith?.let { throw it }
        return "[${from.code}→${to.code}] $text"
    }
}
