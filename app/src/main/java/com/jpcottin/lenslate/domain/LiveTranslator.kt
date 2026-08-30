package com.jpcottin.lenslate.domain

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

/** Where an [Utterance] came from. */
enum class UtteranceKind {
    /** Heard through the microphone (Listen mode). */
    SPOKEN,

    /** Read from a camera snapshot (Read mode). */
    READ,
}

/** One recognized sentence and its translation (null while the translation is in flight). */
data class Utterance(
    val id: Long,
    val source: String,
    val translation: String? = null,
    val error: String? = null,
    val kind: UtteranceKind = UtteranceKind.SPOKEN,
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
    /** A Read-mode snapshot is being captured or recognized. */
    val isReading: Boolean = false,
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
    private var prepareJob: Job? = null
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
        prepareJob?.cancel()
        prepareJob = scope.launch {
            _state.update { it.copy(isPreparing = true) }
            runCatching { engine().prepare(from, to) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(error = e.message ?: "Could not prepare the translation engine") }
                }
        }.also { job ->
            job.invokeOnCompletion {
                // A replaced prepare must not clear the flag its successor just set.
                if (prepareJob === job) {
                    prepareJob = null
                    _state.update { it.copy(isPreparing = false) }
                }
            }
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
        prepareJob?.cancel()
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
    fun submit(text: String, kind: UtteranceKind = UtteranceKind.SPOKEN) {
        scope.launch { addUtterance(text, kind) }
    }

    /**
     * Read mode: takes one snapshot from [capture], recognizes the text in the current source
     * language with [recognizer], and translates it like a spoken sentence. Returns the recognized
     * text, or null when nothing was read (the reason is in [LiveTranslationState.error]).
     */
    suspend fun readText(capture: FrameCapture, recognizer: TextRecognizer): String? {
        if (state.value.isReading) return null
        _state.update { it.copy(isReading = true, error = null) }
        try {
            val frame = capture.capture()
            val text = recognizer.recognize(frame, state.value.from).trim()
            if (text.isEmpty()) {
                _state.update { it.copy(error = NO_TEXT_FOUND) }
                return null
            }
            // OCR keeps line breaks; a sign reads better as one sentence per paragraph.
            val normalized = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            addUtterance(normalized, UtteranceKind.READ)
            return normalized
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _state.update { it.copy(error = e.message ?: "Could not read text") }
            return null
        } finally {
            _state.update { it.copy(isReading = false) }
        }
    }

    private fun handle(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> _state.update { it.copy(error = null) }
            is SpeechEvent.Partial -> {
                // A blank partial withdraws the current hypothesis (mic muted mid-sentence).
                _state.update {
                    it.copy(
                        partialSource = event.text,
                        partialTranslation = if (event.text.isBlank()) "" else it.partialTranslation,
                    )
                }
                schedulePartialTranslation(event.text)
            }
            is SpeechEvent.Final -> {
                partialJob?.cancel()
                _state.update { it.copy(partialSource = "", partialTranslation = "") }
                addUtterance(event.text, UtteranceKind.SPOKEN)
            }
            is SpeechEvent.Error -> _state.update { it.copy(error = event.message) }
        }
    }

    private fun addUtterance(rawText: String, kind: UtteranceKind) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val id = nextId++
        _state.update { s ->
            s.copy(utterances = (s.utterances + Utterance(id, text, kind = kind)).takeLast(maxUtterances))
        }
        translateUtterance(id, text)
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
                    // A successful translation clears the banner; a failure sets it.
                    error = error ?: if (translation != null) null else st.error,
                )
            }
            if (translation != null) {
                _translated.tryEmit(state.value.utterances.firstOrNull { it.id == id } ?: Utterance(id, text, translation))
            }
        }
    }

    companion object {
        const val NO_TEXT_FOUND = "No text found"
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
