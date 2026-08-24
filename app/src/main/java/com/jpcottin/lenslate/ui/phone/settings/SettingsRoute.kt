package com.jpcottin.lenslate.ui.phone.settings

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.lenslate.appContainer

@OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = settingsViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        models = models,
        // Side by side with Home there is nothing to go "back" from. Uses the same pane
        // directive as the navigation scene, so the arrow disappears exactly when (and only
        // when) the supporting pane actually shows Home next to Settings.
        showBack = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).maxHorizontalPartitions <= 1,
        onBack = onBack,
        onEngineChange = viewModel::setEngine,
        onGeminiApiKeyChange = viewModel::setGeminiApiKey,
        onGeminiModelChange = viewModel::setGeminiModel,
        onSpeakTranslationsChange = viewModel::setSpeakTranslations,
        onShowSourceOnGlassesChange = viewModel::setShowSourceOnGlasses,
        onDownloadModel = viewModel::downloadModel,
        onDeleteModel = viewModel::deleteModel,
    )
}

@Composable
private fun settingsViewModel(): SettingsViewModel {
    val container = LocalContext.current.appContainer
    return viewModel { SettingsViewModel(container) }
}
