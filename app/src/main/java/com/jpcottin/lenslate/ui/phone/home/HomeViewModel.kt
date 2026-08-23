package com.jpcottin.lenslate.ui.phone.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.di.AppContainer
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.SpeechSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val container: AppContainer,
    private val phoneSpeechSource: SpeechSource = container.phoneSpeechSource(),
) : ViewModel() {

    val live: StateFlow<LiveTranslationState> = container.liveTranslator.state
    val settings: StateFlow<Settings> = container.settings

    val glassesConnected: StateFlow<Boolean> = container.glassesConnected()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleListening() {
        if (live.value.isListening) container.liveTranslator.stop()
        else container.liveTranslator.start(phoneSpeechSource)
    }

    fun setLanguages(from: Language, to: Language) {
        viewModelScope.launch { container.settingsRepository.setLanguages(from, to) }
    }

    fun swapLanguages() = setLanguages(settings.value.to, settings.value.from)

    fun clearTranscript() = container.liveTranslator.clear()
}
