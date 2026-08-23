package io.github.jpcottin.lenslate.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One recognized sentence and its translation (null while the translation is in flight). */
data class Utterance(
    val id: Long,
    val source: String,
    val translation: String? = null,
    val error: String? = null,
)

data class LiveTranslationState(
    val isListening: Boolean = false,
    val isPreparing: Boolean = false,
    val from: Language = Language.DEFAULT_SOURCE,
    val to: Language = Language.DEFAULT_TARGET,
    /** Sentence currently being spoken (interim recognition). */
    val partialSource: String = "",
    /** Live translation of [partialSource], when partial translation is enabled. */
    val partialTranslation: String = "",
    val utterances: List<Utterance> = emptyList(),
    val error: String? = null,
) {
    val latest: Utterance? get() = utterances.lastOrNull()
}

/**
 * The heart of Listen mode: consumes a [SpeechSource], translates every sentence with the
 * current [TranslationEngine], and exposes the result as observable state shared by the phone UI
 * and the glasses UI.
 *
 * Only one microphone is active at a time: calling [start] with a new source replaces the
 * previous one, so the glasses' mic can take over from the phone's and vice versa while the
 * transcript stays the same.
 */
class LiveTranslator(
    private val engine: () -> TranslationEngine,
    private val scope: CoroutineScope,
    private val partialTranslationEnabled: () -> Boolean = { true },
    private val partialTranslationDelayMs: Long = 350,
    private val maxUtterances: Int = 50,
) {
    private val _state = MutableStateFlow(LiveTranslationState())
    val state: StateFlow<LiveTranslationState> = _state.asStateFlow()

    private val _translated = MutableSharedFlow<Utterance>(extraBufferCapacity = 16)

    /** Emits every utterance once its translation is available (used for text-to-speech). */
    val translated: SharedFlow<Utterance> = _translated.asSharedFlow()

    private var listenJob: Job? = null
    private var partialJob: Job? = null
    private var nextId = 1L

    fun setLanguages(from: Language, to: Language) {
        if (from == state.value.from && to == state.value.to) return
        val activeSource = currentSource
        stop()
        _state.update { it.copy(from = from, to = to, partialSource = "", partialTranslation = "") }
        if (activeSource != null) start(activeSource)
    }

    private var currentSource: SpeechSource? = null

    /** Starts (or restarts) listening on [source] in the current source language. */
    fun start(source: SpeechSource) {
        stop()
        currentSource = source
        val from = state.value.from
        val to = state.value.to
        _state.update { it.copy(isListening = true, error = null) }
        scope.launch {
            _state.update { it.copy(isPreparing = true) }
            runCatching { engine().prepare(from, to) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Could not prepare the translation engine") } }
            _state.update { it.copy(isPreparing = false) }
        }
        listenJob = scope.launch {
            source.listen(from)
                .catch { e -> _state.update { it.copy(error = e.message ?: "Speech recognition failed") } }
                .collect { event -> handle(event) }
        }.also { job ->
            job.invokeOnCompletion {
                // A replaced job completes asynchronously, after its successor has started:
                // only the job that is still current may flip the state back to idle.
                if (listenJob === job) {
                    listenJob = null
                    currentSource = null
                    _state.update { it.copy(isListening = false, partialSource = "", partialTranslation = "") }
                }
            }
        }
    }

    fun stop() {
        partialJob?.cancel()
        partialJob = null
        val job = listenJob
        listenJob = null
        currentSource = null
        job?.cancel()
        _state.update { it.copy(isListening = false, partialSource = "", partialTranslation = "") }
    }

    fun clear() {
        _state.update { it.copy(utterances = emptyList(), partialSource = "", partialTranslation = "", error = null) }
    }

    /** Feeds a sentence as if it had been recognized; handy for typed input and tests. */
    fun submit(text: String) {
        scope.launch { handle(SpeechEvent.Final(text)) }
    }

    private fun handle(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> _state.update { it.copy(error = null) }
            is SpeechEvent.Partial -> {
                _state.update { it.copy(partialSource = event.text) }
                schedulePartialTranslation(event.text)
            }
            is SpeechEvent.Final -> {
                partialJob?.cancel()
                val text = event.text.trim()
                _state.update { it.copy(partialSource = "", partialTranslation = "") }
                if (text.isEmpty()) return
                val id = nextId++
                _state.update { s ->
                    s.copy(utterances = (s.utterances + Utterance(id, text)).takeLast(maxUtterances))
                }
                translateUtterance(id, text)
            }
            is SpeechEvent.Error -> _state.update { it.copy(error = event.message) }
        }
    }

    private fun translateUtterance(id: Long, text: String) {
        scope.launch {
            val s = state.value
            val result = runCatching { engine().translate(text, s.from, s.to) }
            val translation = result.getOrNull()
            val error = result.exceptionOrNull()?.message
            _state.update { st ->
                st.copy(
                    utterances = st.utterances.map { u ->
                        if (u.id == id) u.copy(translation = translation, error = error) else u
                    },
                    error = error ?: st.error,
                )
            }
            if (translation != null) _translated.tryEmit(Utterance(id, text, translation))
        }
    }

    private fun schedulePartialTranslation(text: String) {
        partialJob?.cancel()
        if (!partialTranslationEnabled() || text.isBlank()) return
        partialJob = scope.launch {
            delay(partialTranslationDelayMs)
            val s = state.value
            val translation = runCatching { engine().translate(text, s.from, s.to) }.getOrNull() ?: return@launch
            // Only apply if this is still the sentence being spoken.
            _state.update { if (it.partialSource == text) it.copy(partialTranslation = translation) else it }
        }
    }
}
