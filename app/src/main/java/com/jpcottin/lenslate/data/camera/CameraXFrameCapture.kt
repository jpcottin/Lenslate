package com.jpcottin.lenslate.data.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.jpcottin.lenslate.domain.Frame
import com.jpcottin.lenslate.domain.FrameCapture
import com.jpcottin.lenslate.domain.TextRecognitionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One-shot snapshot with CameraX [ImageCapture].
 *
 * Pass the projected activity as [context] to use the AI glasses' camera (the glasses' outward
 * camera is `DEFAULT_BACK_CAMERA` in a projected context) or a phone context for the phone's.
 * The camera is bound only for the duration of the capture and unbound right after: glasses have
 * tight power and thermal budgets, so nothing streams while the user is not reading.
 *
 * When [imageCapture] is provided (the phone's Read screen, which already shows a viewfinder), the
 * use case is assumed to be bound by the caller and is not rebound here.
 */
class CameraXFrameCapture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val imageCapture: ImageCapture? = null,
) : FrameCapture {

    override suspend fun capture(): Frame = withContext(Dispatchers.Main.immediate) {
        if (imageCapture != null) return@withContext takePicture(imageCapture)

        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) throw TextRecognitionException("No camera available")
        val useCase = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
        try {
            // Bind only this use case: unbindAll() would tear down use cases other surfaces
            // (like the phone Read screen's viewfinder) have bound on the shared provider.
            provider.bindToLifecycle(lifecycleOwner, selector, useCase)
            takePicture(useCase)
        } finally {
            provider.unbind(useCase)
        }
    }

    private suspend fun takePicture(useCase: ImageCapture): Frame = suspendCancellableCoroutine { cont ->
        useCase.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val frame = image.use { Frame(it.toBitmap(), it.imageInfo.rotationDegrees) }
                    cont.resume(frame)
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(TextRecognitionException("Camera capture failed: ${exception.message}", exception))
                }
            },
        )
    }
}
