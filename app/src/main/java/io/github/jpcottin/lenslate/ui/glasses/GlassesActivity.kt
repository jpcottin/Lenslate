package io.github.jpcottin.lenslate.ui.glasses

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography
import androidx.xr.projected.ProjectedActivityCompat
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.ProjectedDeviceController
import androidx.xr.projected.ProjectedDisplayController
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import io.github.jpcottin.lenslate.appContainer
import io.github.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Projected activity shown on Display AI Glasses. It runs on the phone, but because it is
 * declared with `xr_projected`, its context is the glasses' context: the SpeechRecognizer it
 * creates listens through the glasses' microphone and its UI renders on the lens.
 *
 * Permissions are device-aware: the glasses have their own grant state, separate from the
 * phone's. The glasses-specific request goes through the projected library (which binds to the
 * glasses service and blocks for up to 5 s), so it is never run on the main thread. If it fails
 * — or is denied — the phone's microphone is used instead: glasses pair as a Bluetooth headset,
 * so the phone's audio input is routed through them anyway.
 */
@OptIn(ExperimentalProjectedApi::class, ExperimentalComposeUiApi::class)
class GlassesActivity : ComponentActivity() {

    private var displayController: ProjectedDisplayController? = null
    private var isVisualUiSupported by mutableStateOf(true)
    private var areVisualsOn by mutableStateOf(true)
    private var permissionDenied by mutableStateOf(false)

    /** Microphone to listen with, resolved once permissions are sorted out. */
    private var speechSource: SpeechSource? = null
    private var resolvingMicrophone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Temporary requirement: let the system focus the first focusable element (the card).
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true
        super.onCreate(savedInstanceState)

        // Listening is the whole point of the app; keep the lens on while the activity runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initializeGlassesFeatures()

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
                    onToggleListening = {
                        if (live.isListening) container.liveTranslator.stop() else resolveMicrophoneAndListen()
                    },
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
        super.onDestroy()
    }

    /** Result of [ProjectedActivityCompat.requestPermissions] (glasses-specific permission). */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MIC_PERMISSION_REQUEST) return
        val index = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val granted = index >= 0 && grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            speechSource = appContainer.speechSource(this)
            permissionDenied = false
            startListening()
        } else {
            usePhoneMicrophoneOrDeny()
        }
    }

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
        if (hasMicPermission(this)) {
            // `this` is the projected context: the recognizer captures the glasses' microphone.
            speechSource = appContainer.speechSource(this)
            permissionDenied = false
            startListening()
            return
        }
        if (resolvingMicrophone) return
        resolvingMicrophone = true
        lifecycleScope.launch {
            val requested = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectedActivityCompat.requestPermissions(
                        this@GlassesActivity,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        MIC_PERMISSION_REQUEST,
                    )
                }
            }
            resolvingMicrophone = false
            requested.onFailure { e ->
                Log.w(TAG, "Glasses permission request failed, falling back to the phone microphone", e)
                usePhoneMicrophoneOrDeny()
            }
            // On success the answer arrives in onRequestPermissionsResult.
        }
    }

    private fun usePhoneMicrophoneOrDeny() {
        val host = runCatching { ProjectedContext.createHostDeviceContext(this) }.getOrNull()
        if (host != null && hasMicPermission(host)) {
            speechSource = appContainer.speechSource(host)
            permissionDenied = false
            startListening()
        } else {
            permissionDenied = true
        }
    }

    private fun initializeGlassesFeatures() {
        lifecycleScope.launch {
            runCatching {
                withTimeout(SERVICE_TIMEOUT_MS) {
                    ProjectedDeviceController.create(this@GlassesActivity).use { controller ->
                        isVisualUiSupported =
                            controller.capabilities.contains(ProjectedDeviceController.Capability.CAPABILITY_VISUAL_UI)
                    }
                }
            }.onFailure { Log.w(TAG, "Could not read glasses capabilities", it) }
            runCatching {
                withTimeout(SERVICE_TIMEOUT_MS) {
                    val controller = ProjectedDisplayController.create(this@GlassesActivity)
                    displayController = controller
                    controller.addLayoutParamsFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    controller.addPresentationModeChangedListener(ContextCompat.getMainExecutor(this@GlassesActivity)) { flags ->
                        areVisualsOn = flags.hasPresentationMode(ProjectedDisplayController.PresentationMode.VISUALS_ON)
                    }
                }
            }.onFailure { Log.w(TAG, "Could not attach to the glasses display controller", it) }
        }
    }

    private fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "GlassesActivity"
        const val MIC_PERMISSION_REQUEST = 1001
        const val SERVICE_TIMEOUT_MS = 5_000L
    }
}
