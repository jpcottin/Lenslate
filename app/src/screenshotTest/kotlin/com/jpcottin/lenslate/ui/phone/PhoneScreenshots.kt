package com.jpcottin.lenslate.ui.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.ui.phone.home.HomeScreen
import com.jpcottin.lenslate.ui.phone.read.ReadScreen
import com.jpcottin.lenslate.ui.phone.settings.SettingsScreen
import com.jpcottin.lenslate.ui.preview.PreviewData
import com.jpcottin.lenslate.ui.theme.LenslateTheme

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true)
annotation class FormFactorPreviews

private val transcript = PreviewData.transcript

private fun homeScreen(live: LiveTranslationState, connected: Boolean, wide: Boolean) = @Composable {
    LenslateTheme(dynamicColor = false) {
        HomeScreen(
            live = live,
            settings = Settings(),
            glassesConnected = connected,
            micPermissionDenied = false,
            launchError = null,
            onToggleListening = {},
            onRead = {},
            onSetLanguages = { _, _ -> },
            onSwapLanguages = {},
            onConversationModeChange = {},
            onClearTranscript = {},
            onShareTranscript = {},
            onCopyUtterance = {},
            onOpenSettings = {},
            onLaunchOnGlasses = {},
            isWideWindow = wide,
        )
    }
}

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun HomeEmptyPhone() = homeScreen(LiveTranslationState(), connected = false, wide = false)()

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun HomeListeningPhone() = homeScreen(transcript, connected = true, wide = false)()

@PreviewTest
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun HomeListeningWide() = homeScreen(transcript, connected = true, wide = true)()

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun HomeError() = homeScreen(
    LiveTranslationState(isListening = true, isPreparing = true, error = "Network error"),
    connected = true,
    wide = false,
)()

@PreviewTest
@FormFactorPreviews
@Composable
fun SettingsGemini() {
    LenslateTheme(dynamicColor = false) {
        SettingsScreen(
            settings = PreviewData.geminiSettings,
            models = PreviewData.models,
            onBack = {},
            onEngineChange = {},
            onGeminiApiKeyChange = {},
            onGeminiModelChange = {},
            onSpeakTranslationsChange = {},
            onShowSourceOnGlassesChange = {},
            onDownloadModel = {},
            onDeleteModel = {},
        )
    }
}

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun ReadScreenIdle() {
    LenslateTheme(dynamicColor = false) {
        ReadScreen(surfaceRequest = null, isReading = false, error = null, onCapture = {}, onBack = {})
    }
}

@PreviewTest
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun ReadScreenTablet() {
    LenslateTheme(dynamicColor = false) {
        ReadScreen(surfaceRequest = null, isReading = true, error = null, onCapture = {}, onBack = {})
    }
}

@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
fun ReadScreenNoText() {
    LenslateTheme(dynamicColor = false) {
        ReadScreen(surfaceRequest = null, isReading = false, error = "No text found. Try getting closer or improving the light.", onCapture = {}, onBack = {})
    }
}
