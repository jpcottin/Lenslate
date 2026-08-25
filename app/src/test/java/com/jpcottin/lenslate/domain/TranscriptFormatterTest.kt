package com.jpcottin.lenslate.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFormatterTest {

    @Test
    fun format_emptyTranscript_isEmpty() {
        assertEquals("", TranscriptFormatter.format(emptyList()))
    }

    @Test
    fun format_joinsSourceAndTranslationBlocks() {
        val text = TranscriptFormatter.format(
            listOf(
                Utterance(1, "Bonjour tout le monde", "Hello everyone"),
                Utterance(2, "Sortie de secours", "Emergency exit", kind = UtteranceKind.READ),
            )
        )
        assertEquals(
            "Bonjour tout le monde\n→ Hello everyone\n\nSortie de secours\n→ Emergency exit",
            text,
        )
    }

    @Test
    fun format_keepsOnlyTheSourceLine_whenTranslationFailedOrIsPending() {
        val text = TranscriptFormatter.format(
            listOf(
                Utterance(1, "Salut", translation = null),
                Utterance(2, "Bof", translation = null, error = "boom"),
                Utterance(3, "Oui", translation = "Yes"),
            )
        )
        assertEquals("Salut\n\nBof\n\nOui\n→ Yes", text)
    }

    @Test
    fun format_treatsBlankTranslationAsMissing() {
        assertEquals("Hein", TranscriptFormatter.format(listOf(Utterance(1, "Hein", "  "))))
    }

    @Test
    fun copyText_prefersTheTranslation() {
        assertEquals("Hello", TranscriptFormatter.copyText(Utterance(1, "Bonjour", "Hello")))
    }

    @Test
    fun copyText_fallsBackToTheSource_whenThereIsNoTranslation() {
        assertEquals("Bonjour", TranscriptFormatter.copyText(Utterance(1, "Bonjour", null)))
        assertEquals("Bonjour", TranscriptFormatter.copyText(Utterance(1, "Bonjour", " ")))
    }
}
