package com.jpcottin.lenslate.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.TranslationEngine
import com.jpcottin.lenslate.domain.TranslationException

/** Validates `translateText` arguments and runs the translation; kept free of Android types so it is unit-testable. */
internal class TranslateTextHandler(
    private val settings: Settings,
    private val engine: TranslationEngine,
) {
    suspend fun handle(text: String, fromLanguage: String, toLanguage: String): TranslationResult {
        if (text.isBlank()) throw AppFunctionInvalidArgumentException("text must not be empty")
        val from = resolve(fromLanguage, settings.from, "fromLanguage")
        val to = resolve(toLanguage, settings.to, "toLanguage")
        val translated = try {
            engine.translate(text, from, to)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: TranslationException) {
            throw AppFunctionAppUnknownException(e.message ?: "Translation failed")
        } catch (e: Exception) {
            // Anything else (Play Services failures, URL problems, ...) must still leave the
            // AppFunction boundary as the structured error the contract promises.
            throw AppFunctionAppUnknownException(e.message ?: "Translation failed unexpectedly")
        }
        return TranslationResult(
            sourceText = text,
            translatedText = translated,
            fromLanguage = from.code,
            toLanguage = to.code,
            engine = engineName(settings.engine),
        )
    }

    private fun resolve(code: String, default: Language, argument: String): Language {
        if (code.isBlank()) return default
        return Language.fromCode(code) ?: throw AppFunctionInvalidArgumentException(
            "$argument must be one of ${Language.entries.joinToString { it.code }} (got \"$code\")"
        )
    }

    companion object {
        fun engineName(kind: EngineKind): String = when (kind) {
            EngineKind.ON_DEVICE -> "on-device"
            EngineKind.GEMINI -> "gemini"
        }
    }
}
