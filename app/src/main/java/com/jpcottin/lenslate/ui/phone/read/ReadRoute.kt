package com.jpcottin.lenslate.ui.phone.read

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.lenslate.R
import com.jpcottin.lenslate.appContainer
import com.jpcottin.lenslate.data.camera.CameraXFrameCapture
import com.jpcottin.lenslate.domain.LiveTranslator
import kotlinx.coroutines.flow.collectLatest

/**
 * Phone Read mode: a CameraX viewfinder with a shutter. The snapshot is OCR'd and translated by
 * the shared pipeline, then the screen pops back to the transcript.
 */
@Composable
fun ReadRoute(
    onBack: () -> Unit,
    viewModel: ReadViewModel = readViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.readDone.collectLatest { onBack() }
    }

    // Bind Preview + ImageCapture for as long as this screen is composed.
    val cameraUnavailable = stringResource(R.string.camera_unavailable)
    LaunchedEffect(lifecycleOwner) {
        val hostContext = context.appContainer.hostContext()
        val provider = runCatching { ProcessCameraProvider.awaitInstance(hostContext) }.getOrElse {
            cameraError = it.message ?: cameraUnavailable
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request -> surfaceRequest = request } }
        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) {
            cameraError = cameraUnavailable
            return@LaunchedEffect
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            viewModel.imageCapture = imageCapture
        }.onFailure { cameraError = it.message ?: "Could not start the camera" }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            // Only clean up our own use case: a re-entered Read screen may already have bound
            // a fresh capture by the time this disposed entry's cleanup runs.
            if (viewModel.imageCapture === imageCapture) viewModel.imageCapture = null
            provider.unbind(preview, imageCapture)
        }
    }

    val noText = stringResource(R.string.read_no_text)
    ReadScreen(
        surfaceRequest = surfaceRequest,
        isReading = live.isReading,
        error = cameraError ?: live.error?.takeIf { !live.isReading }?.let { if (it == LiveTranslator.NO_TEXT_FOUND) noText else it },
        onCapture = {
            val capture = viewModel.imageCapture ?: return@ReadScreen
            viewModel.read(CameraXFrameCapture(context.appContainer.hostContext(), lifecycleOwner, capture))
        },
        onBack = onBack,
    )
}

@Composable
private fun readViewModel(): ReadViewModel {
    val container = LocalContext.current.appContainer
    return viewModel { ReadViewModel(container) }
}
