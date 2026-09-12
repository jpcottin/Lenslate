package com.jpcottin.lenslate.ui.phone.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.lenslate.R
import com.jpcottin.lenslate.appContainer
import com.jpcottin.lenslate.domain.TranscriptFormatter
import com.jpcottin.lenslate.ui.glasses.GlassesLauncher

/** Stateful wrapper: wires [HomeViewModel], the mic permission and the glasses launcher to [HomeScreen]. */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenRead: () -> Unit,
    viewModel: HomeViewModel = homeViewModel(),
) {
    val context = LocalContext.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val glassesConnected by viewModel.glassesConnected.collectAsStateWithLifecycle()
    var micDenied by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }
    // Resolved in composition so they follow configuration (locale) changes; see LocalContextGetResourceValueCall.
    val shareHeader = stringResource(R.string.share_transcript_header, live.from.shortLabel, live.to.shortLabel)
    val shareTitle = stringResource(R.string.share_transcript)
    val appName = stringResource(R.string.app_name)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) viewModel.toggleListening()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) onOpenRead()
    }

    HomeScreen(
        live = live,
        settings = settings,
        glassesConnected = glassesConnected,
        micPermissionDenied = micDenied,
        cameraPermissionDenied = cameraDenied,
        launchError = launchError,
        onToggleListening = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) micDenied = false // granted later in system settings: drop the stale banner
            if (granted || live.isListening) viewModel.toggleListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onRead = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                cameraDenied = false
                onOpenRead()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onSetLanguages = viewModel::setLanguages,
        onSwapLanguages = viewModel::swapLanguages,
        onConversationModeChange = viewModel::setConversationMode,
        onClearTranscript = viewModel::clearTranscript,
        onShareTranscript = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareHeader + "\n\n" + TranscriptFormatter.format(live.utterances))
                putExtra(Intent.EXTRA_SUBJECT, shareHeader)
            }
            context.startActivity(Intent.createChooser(send, shareTitle))
        },
        onCopyUtterance = { utterance ->
            // The system shows its own "copied" confirmation, no snackbar needed.
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText(appName, TranscriptFormatter.copyText(utterance)))
        },
        onOpenSettings = onOpenSettings,
        onLaunchOnGlasses = {
            launchError = GlassesLauncher.launch(context).exceptionOrNull()?.message
        },
    )
}

@Composable
private fun homeViewModel(): HomeViewModel {
    val container = LocalContext.current.appContainer
    return viewModel { HomeViewModel(container) }
}
