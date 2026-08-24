package com.jpcottin.lenslate.ui.glasses

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography
import androidx.xr.projected.ProjectedActivityCompat
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.ProjectedInputEvent
import androidx.xr.projected.ProjectedDeviceController
import androidx.xr.projected.ProjectedDisplayController
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import com.jpcottin.lenslate.appContainer
import com.jpcottin.lenslate.data.camera.CameraXFrameCapture
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Projected activity shown on Display AI Glasses. It runs on the phone, but because it is
 * declared with `xr_projected`, its context is the glasses' context: the SpeechRecognizer and the
 * CameraX capture it creates use the glasses' microphone and outward camera, and its UI renders
 * on the lens.
 *
 * Permissions are device-aware: the glasses have their own grant state, separate from the
 * phone's. The glasses-specific request goes through the projected library (which binds to the
 * glasses service and blocks for up to 5 s), so it is never run on the main thread. If it fails
 * — or is denied — the phone's hardware is used instead: glasses pair as a Bluetooth headset,
 * so the phone's audio input is routed through them anyway, and the phone camera is a usable
 * fallback for reading text.
 */
@OptIn(ExperimentalProjectedApi::class, ExperimentalComposeUiApi::class)
class GlassesActivity : ComponentActivity() {

    private var displayController: ProjectedDisplayController? = null
    private var isVisualUiSupported by mutableStateOf(true)
    private var areVisualsOn by mutableStateOf(true)
    private var permissionDenied by mutableStateOf(false)
    private var cameraPermissionDenied by mutableStateOf(false)

    /** Microphone to listen with, resolved once permissions are sorted out. */
    private var speechSource: SpeechSource? = null

    /** Pending glasses-side permission requests, completed from [onRequestPermissionsResult]. */
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<Boolean>>()

    private var activityCompat: ProjectedActivityCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Temporary requirement: let the system focus the first focusable element.
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true
        super.onCreate(savedInstanceState)

        // Listening is the whole point of the app; keep the lens on while the activity runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeGlassesFeatures()
        listenForHardwareButtons()

        val container = appContainer
        // Displayless glasses, or a lens that is off: read translations aloud. When the user
        // enabled "speak translations" the container already does it for every surface.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.liveTranslator.translated.collect { utterance ->
                    val settings = container.settings.value
                    if (!settings.speakTranslations && (!areVisualsOn || !isVisualUiSupported)) {
                        container.speaker.speak(utterance.translation.orEmpty(), settings.to)
                    }
                }
            }
        }

        setContent {
            GlimmerTheme(typography = createGoogleSansFlexTypography()) {
                val live by container.liveTranslator.state.collectAsStateWithLifecycle()
                val settings by container.settings.collectAsStateWithLifecycle()
                GlassesScreen(
                    live = live,
                    showSource = settings.showSourceOnGlasses,
                    isVisualUiSupported = isVisualUiSupported,
                    permissionDenied = permissionDenied,
                    cameraPermissionDenied = cameraPermissionDenied,
                    onToggleListening = {
                        if (live.isListening) container.liveTranslator.stop() else resolveMicrophoneAndListen()
                    },
                    onRead = { readText() },
                    onRetryPermission = { resolveMicrophoneAndListen(forceRequest = true) },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Route text-to-speech through the glasses while this activity is in front.
        appContainer.speaker.attach(this)
        resolveMicrophoneAndListen()
    }

    override fun onStop() {
        super.onStop()
        appContainer.liveTranslator.stop()
        appContainer.speaker.detach()
    }

    override fun onDestroy() {
        displayController?.close()
        displayController = null
        activityCompat?.close()
        activityCompat = null
        super.onDestroy()
    }

    /** Result of [ProjectedActivityCompat.requestPermissions] (glasses-specific permission). */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = permissions.indices.all { grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED }
        pendingRequests.remove(requestCode)?.complete(granted && permissions.isNotEmpty())
    }

    // ---- Listen ------------------------------------------------------------------------------

    private fun startListening() {
        val container = appContainer
        val source = speechSource ?: return
        if (container.liveTranslator.state.value.isListening) return
        container.liveTranslator.start(source)
    }

    private fun resolveMicrophoneAndListen(forceRequest: Boolean = false) {
        if (speechSource != null && !forceRequest) {
            startListening()
            return
        }
        lifecycleScope.launch {
            val micContext = resolveHardwareContext(Manifest.permission.RECORD_AUDIO, MIC_PERMISSION_REQUEST)
            if (micContext == null) {
                permissionDenied = true
                return@launch
            }
            permissionDenied = false
            speechSource = appContainer.speechSource(micContext)
            startListening()
        }
    }

    // ---- Read --------------------------------------------------------------------------------

    private fun readText(fromHardwareButton: Boolean = false) {
        val container = appContainer
        if (container.liveTranslator.state.value.isReading) return
        // An accidental hardware-button press must not re-run a denied permission flow in a
        // loop; the on-screen camera icon remains the deliberate retry path.
        if (fromHardwareButton && cameraPermissionDenied) return
        lifecycleScope.launch {
            val cameraContext = resolveHardwareContext(Manifest.permission.CAMERA, CAMERA_PERMISSION_REQUEST)
            if (cameraContext == null) {
                cameraPermissionDenied = true
                return@launch
            }
            cameraPermissionDenied = false
            container.liveTranslator.readText(
                CameraXFrameCapture(cameraContext, this@GlassesActivity),
                container.textRecognizer,
            )
        }
    }

    // ---- Permissions -------------------------------------------------------------------------

    /**
     * Returns the context whose hardware may be used for [permission]: the glasses (this activity)
     * when granted there — asking through the projected permission flow if needed — otherwise the
     * phone when granted there, or null when neither is available.
     */
    private suspend fun resolveHardwareContext(permission: String, requestCode: Int): Context? {
        if (hasPermission(this, permission)) return this
        val grantedOnGlasses = requestGlassesPermission(permission, requestCode)
        if (grantedOnGlasses) return this
        val host = runCatching { ProjectedContext.createHostDeviceContext(this) }.getOrNull()
        return host?.takeIf { hasPermission(it, permission) }
    }

    private suspend fun requestGlassesPermission(permission: String, requestCode: Int): Boolean {
        pendingRequests[requestCode]?.let { return it.await() }
        val result = CompletableDeferred<Boolean>()
        pendingRequests[requestCode] = result
        val launched = withContext(Dispatchers.IO) {
            runCatching {
                ProjectedActivityCompat.requestPermissions(this@GlassesActivity, arrayOf(permission), requestCode)
            }
        }
        return launched.fold(
            onSuccess = { result.await() },
            onFailure = { e ->
                Log.w(TAG, "Glasses permission request for $permission failed, falling back to the phone", e)
                pendingRequests.remove(requestCode)
                false
            },
        )
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** The glasses' hardware camera button triggers Read mode. */
    private fun listenForHardwareButtons() {
        lifecycleScope.launch {
            val compat = connectProjected("Projected input events", attempts = 3) {
                ProjectedActivityCompat.create(this@GlassesActivity)
            } ?: return@launch
            activityCompat = compat
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                compat.projectedInputEvents
                    // The stream must never crash the app when the glasses disconnect mid-session.
                    .catch { e -> Log.w(TAG, "Projected input event stream failed", e) }
                    .collect { event ->
                        if (event.inputAction == ProjectedInputEvent.ProjectedInputAction.TOGGLE_APP_CAMERA) {
                            Log.i(TAG, "Camera button pressed")
                            readText(fromHardwareButton = true)
                        }
                    }
            }
        }
    }

    /**
     * Connects to a projected service, retrying transient failures. No outer timeout: the
     * library's ProjectedServiceConnection enforces its own 5 s bind timeout, and cancelling
     * `create` mid-bind would leak the service binding (bindService is issued before suspending
     * with no cancellation cleanup). A connection that completes after the activity is destroyed
     * is closed on the spot, since onDestroy has already run.
     */
    private suspend fun <T : AutoCloseable> connectProjected(
        what: String,
        attempts: Int = 1,
        create: suspend () -> T,
    ): T? {
        repeat(attempts) { attempt ->
            try {
                val connection = create()
                if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    connection.close()
                    return null
                }
                return connection
            } catch (e: CancellationException) {
                // Our own scope was cancelled (lifecycle teardown): propagate silently.
                if (!currentCoroutineContext().isActive) throw e
                // Otherwise it is the library's internal bind timeout.
                Log.w(TAG, "$what timed out (attempt ${attempt + 1}/$attempts)")
            } catch (e: Exception) {
                Log.w(TAG, "$what unavailable (attempt ${attempt + 1}/$attempts)", e)
            }
            if (attempt < attempts - 1) delay(SERVICE_RETRY_DELAY_MS)
        }
        return null
    }

    // ---- Glasses device ----------------------------------------------------------------------

    private fun initializeGlassesFeatures() {
        lifecycleScope.launch {
            connectProjected("Glasses capabilities") {
                ProjectedDeviceController.create(this@GlassesActivity)
            }?.use { controller ->
                isVisualUiSupported =
                    controller.capabilities.contains(ProjectedDeviceController.Capability.CAPABILITY_VISUAL_UI)
            }
            connectProjected("Glasses display controller") {
                ProjectedDisplayController.create(this@GlassesActivity)
            }?.let { controller ->
                displayController = controller
                controller.addLayoutParamsFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller.addPresentationModeChangedListener(ContextCompat.getMainExecutor(this@GlassesActivity)) { flags ->
                    areVisualsOn = flags.hasPresentationMode(ProjectedDisplayController.PresentationMode.VISUALS_ON)
                }
            }
        }
    }

    private companion object {
        const val TAG = "GlassesActivity"
        const val MIC_PERMISSION_REQUEST = 1001
        const val CAMERA_PERMISSION_REQUEST = 1002
        const val SERVICE_RETRY_DELAY_MS = 2_000L
    }
}
