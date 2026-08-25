package com.jpcottin.lenslate.domain

/** Formats the transcript as plain text for the share sheet and the clipboard. */
object TranscriptFormatter {

    /** The text worth copying from one row: the translation when it exists, the source otherwise. */
    fun copyText(utterance: Utterance): String =
        utterance.translation?.takeIf { it.isNotBlank() } ?: utterance.source

    /**
     * One block per utterance — the source line, then the translation prefixed with an arrow —
     * separated by blank lines. Rows whose translation failed or is still pending keep just
     * their source line.
     */
    fun format(utterances: List<Utterance>): String =
        utterances.joinToString(separator = "\n\n") { utterance ->
            val translation = utterance.translation?.takeIf { it.isNotBlank() }
            if (translation != null) "${utterance.source}\n→ $translation" else utterance.source
        }
}
