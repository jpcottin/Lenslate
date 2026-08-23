package io.github.jpcottin.lenslate.ui.phone.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jpcottin.lenslate.appContainer
import io.github.jpcottin.lenslate.ui.glasses.GlassesLauncher

/** Stateful wrapper: wires [HomeViewModel], the mic permission and the glasses launcher to [HomeScreen]. */
@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = homeViewModel(),
) {
    val context = LocalContext.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val glassesConnected by viewModel.glassesConnected.collectAsStateWithLifecycle()
    var micDenied by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) viewModel.toggleListening()
    }

    HomeScreen(
        live = live,
        settings = settings,
        glassesConnected = glassesConnected,
        micPermissionDenied = micDenied,
        launchError = launchError,
        onToggleListening = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted || live.isListening) viewModel.toggleListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onSetLanguages = viewModel::setLanguages,
        onSwapLanguages = viewModel::swapLanguages,
        onClearTranscript = viewModel::clearTranscript,
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
