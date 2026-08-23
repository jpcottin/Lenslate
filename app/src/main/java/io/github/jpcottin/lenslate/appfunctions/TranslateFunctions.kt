package io.github.jpcottin.lenslate.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import io.github.jpcottin.lenslate.appContainer

/**
 * A translation produced by Lenslate.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class TranslationResult(
    /** The original text. */
    val sourceText: String,
    /** The translated text. */
    val translatedText: String,
    /** ISO 639-1 code of the source language (en, fr, es, de or ja). */
    val fromLanguage: String,
    /** ISO 639-1 code of the target language (en, fr, es, de or ja). */
    val toLanguage: String,
    /** Which engine produced the translation: "on-device" (ML Kit) or "gemini". */
    val engine: String,
)

/**
 * The app's [AppFunction]s, letting on-device AI agents translate text without opening the UI.
 */
// The AppFunctions service instantiates this class through its no-arg constructor.
class TranslateFunctions {

    /**
     * Translate a piece of text between two of the supported languages.
     *
     * @param appFunctionContext The execution context.
     * @param text The text to translate. Must not be empty.
     * @param fromLanguage ISO 639-1 code of the source language: en, fr, es, de or ja. Pass an
     * empty string to use the source language currently selected in the app.
     * @param toLanguage ISO 639-1 code of the target language: en, fr, es, de or ja. Pass an
     * empty string to use the target language currently selected in the app.
     * @return The [TranslationResult].
     * @throws AppFunctionInvalidArgumentException If [text] is empty or a language code is not
     * supported.
     * @throws AppFunctionAppUnknownException If the translation engine fails, for example when
     * an offline model cannot be downloaded or the Gemini API key is missing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun translateText(
        appFunctionContext: AppFunctionContext,
        text: String,
        fromLanguage: String,
        toLanguage: String,
    ): TranslationResult {
        val container = appFunctionContext.context.appContainer
        val settings = container.settings.value
        return TranslateTextHandler(settings, container.engineFor(settings.engine)).handle(text, fromLanguage, toLanguage)
    }
}
