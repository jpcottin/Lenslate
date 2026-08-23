package io.github.jpcottin.lenslate.data.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.TranslationEngine
import io.github.jpcottin.lenslate.domain.TranslationException
import io.github.jpcottin.lenslate.util.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** On-device translation with ML Kit. Models (~30 MB per language) download on first use. */
class MlKitTranslationEngine : TranslationEngine {
    private val translators = mutableMapOf<Pair<Language, Language>, Translator>()
    private val mutex = Mutex()
    private val downloadConditions = DownloadConditions.Builder().build()

    private suspend fun translatorFor(from: Language, to: Language): Translator = mutex.withLock {
        translators.getOrPut(from to to) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(mlKitCode(from))
                .setTargetLanguage(mlKitCode(to))
                .build()
            Translation.getClient(options)
        }
    }

    override suspend fun prepare(from: Language, to: Language) {
        if (from == to) return
        try {
            translatorFor(from, to).downloadModelIfNeeded(downloadConditions).await()
        } catch (e: Exception) {
            throw TranslationException("Could not download the ${from.nativeName} → ${to.nativeName} models", e)
        }
    }

    override suspend fun translate(text: String, from: Language, to: Language): String {
        if (from == to || text.isBlank()) return text
        val translator = translatorFor(from, to)
        try {
            translator.downloadModelIfNeeded(downloadConditions).await()
            return translator.translate(text).await()
        } catch (e: Exception) {
            throw TranslationException(e.message ?: "On-device translation failed", e)
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }

    private fun mlKitCode(language: Language): String =
        TranslateLanguage.fromLanguageTag(language.code)
            ?: throw TranslationException("ML Kit does not support ${language.nativeName}")
}
