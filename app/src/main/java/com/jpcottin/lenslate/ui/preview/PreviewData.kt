package com.jpcottin.lenslate.ui.preview

import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.data.translate.ModelStatus
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.Utterance

/** Sample data shared by the `@Preview`s and the screenshot tests. */
object PreviewData {
    val transcript = LiveTranslationState(
        isListening = true,
        from = Language.FRENCH,
        to = Language.ENGLISH,
        partialSource = "et je voudrais un café",
        partialTranslation = "and I would like a coffee",
        utterances = listOf(
            Utterance(1, "Bonjour tout le monde", "Hello everyone"),
            Utterance(2, "Où est la gare ?", "Where is the train station?"),
            Utterance(3, "Je ne comprends pas", null),
        ),
    )

    val glassesTranslation = LiveTranslationState(
        isListening = true,
        from = Language.FRENCH,
        to = Language.ENGLISH,
        utterances = listOf(Utterance(1, "Où est la gare, s'il vous plaît ?", "Where is the train station, please?")),
    )

    val geminiSettings = Settings(engine = EngineKind.GEMINI, geminiApiKey = "AIza-secret")

    val models: Map<Language, ModelStatus> = mapOf(
        Language.ENGLISH to ModelStatus.Downloaded,
        Language.FRENCH to ModelStatus.Downloaded,
        Language.SPANISH to ModelStatus.Downloading,
        Language.GERMAN to ModelStatus.Failed("No network"),
        Language.JAPANESE to ModelStatus.NotDownloaded,
    )
}
