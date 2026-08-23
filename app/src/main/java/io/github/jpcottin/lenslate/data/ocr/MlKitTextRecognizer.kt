package io.github.jpcottin.lenslate.data.ocr

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer as MlKitRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.jpcottin.lenslate.domain.Frame
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.TextRecognitionException
import io.github.jpcottin.lenslate.domain.TextRecognizer
import io.github.jpcottin.lenslate.util.await

/** On-device OCR with ML Kit Text Recognition v2: the Latin model for en/fr/es/de, the Japanese one for ja. */
class MlKitTextRecognizer : TextRecognizer {
    private val latin: MlKitRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val japanese: MlKitRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    override suspend fun recognize(frame: Frame, language: Language): String {
        val client = if (language == Language.JAPANESE) japanese else latin
        val image = InputImage.fromBitmap(frame.bitmap, frame.rotationDegrees)
        return try {
            client.process(image).await().text
        } catch (e: Exception) {
            throw TextRecognitionException(e.message ?: "Text recognition failed", e)
        }
    }

    fun close() {
        latin.close()
        japanese.close()
    }
}
