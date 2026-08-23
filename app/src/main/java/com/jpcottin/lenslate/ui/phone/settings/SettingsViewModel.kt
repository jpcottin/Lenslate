package com.jpcottin.lenslate.ui.phone.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.data.translate.ModelStatus
import com.jpcottin.lenslate.di.AppContainer
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings> = container.settings
    val models: StateFlow<Map<Language, ModelStatus>> = container.modelRepository.statuses

    init {
        viewModelScope.launch { container.modelRepository.refresh() }
    }

    fun setEngine(engine: EngineKind) = viewModelScope.launch { container.settingsRepository.setEngine(engine) }
    fun setGeminiApiKey(key: String) = viewModelScope.launch { container.settingsRepository.setGeminiApiKey(key) }
    fun setGeminiModel(model: String) = viewModelScope.launch { container.settingsRepository.setGeminiModel(model) }
    fun setSpeakTranslations(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setSpeakTranslations(enabled) }
    fun setShowSourceOnGlasses(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setShowSourceOnGlasses(enabled) }
    fun downloadModel(language: Language) = viewModelScope.launch { container.modelRepository.download(language) }
    fun deleteModel(language: Language) = viewModelScope.launch { container.modelRepository.delete(language) }
}
