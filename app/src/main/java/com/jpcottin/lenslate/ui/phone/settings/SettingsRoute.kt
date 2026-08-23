package com.jpcottin.lenslate.ui.phone.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.lenslate.appContainer

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
