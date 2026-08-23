package io.github.jpcottin.lenslate.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.jpcottin.lenslate.domain.Frame
import io.github.jpcottin.lenslate.domain.FrameCapture
import io.github.jpcottin.lenslate.domain.TextRecognitionException
import java.io.File

/** A "camera" that returns a fixed image: used by the debug receiver and by tests. */
class BitmapFrameCapture(private val bitmap: Bitmap) : FrameCapture {
    override suspend fun capture(): Frame = Frame(bitmap)

    companion object {
        fun fromFile(path: String): BitmapFrameCapture {
            val file = File(path)
            val bitmap = BitmapFactory.decodeFile(file.path)
                ?: throw TextRecognitionException("Could not decode image: ${file.path}")
            return BitmapFrameCapture(bitmap)
        }
    }
}
