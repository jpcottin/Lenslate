package com.jpcottin.lenslate.domain

import java.util.Locale

/**
 * The languages Lenslate can listen to and translate between.
 *
 * @property code ISO 639-1 code, which is also the ML Kit `TranslateLanguage` tag.
 * @property tag BCP-47 tag used for speech recognition and text-to-speech.
 * @property englishName Name used when prompting Gemini.
 * @property nativeName Name shown in the UI, in the language itself.
 */
enum class Language(
    val code: String,
    val tag: String,
    val englishName: String,
    val nativeName: String,
) {
    ENGLISH("en", "en-US", "English", "English"),
    FRENCH("fr", "fr-FR", "French", "Français"),
    SPANISH("es", "es-ES", "Spanish", "Español"),
    GERMAN("de", "de-DE", "German", "Deutsch"),
    JAPANESE("ja", "ja-JP", "Japanese", "日本語");

    val locale: Locale
        get() = Locale.forLanguageTag(tag)

    /** Two-letter label for compact UI such as the glasses title chip ("FR → EN"). */
    val shortLabel: String
        get() = code.uppercase(Locale.ROOT)

    companion object {
        val DEFAULT_SOURCE = FRENCH
        val DEFAULT_TARGET = ENGLISH

        fun fromCode(code: String?): Language? =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
    }
}
