package com.jpcottin.lenslate.domain

import android.graphics.Bitmap

/** One camera snapshot. [rotationDegrees] is the rotation needed to make the image upright. */
class Frame(val bitmap: Bitmap, val rotationDegrees: Int = 0)

/** Takes a single snapshot: the glasses' camera, the phone's camera, or a file in tests. */
interface FrameCapture {
    suspend fun capture(): Frame
}

/** Extracts the text visible in a [Frame]; returns an empty string when there is none. */
interface TextRecognizer {
    suspend fun recognize(frame: Frame, language: Language): String
}

class TextRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)
