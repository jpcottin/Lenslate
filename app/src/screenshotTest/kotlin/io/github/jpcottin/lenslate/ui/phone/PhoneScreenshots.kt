package io.github.jpcottin.lenslate.ui.phone

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.jpcottin.lenslate.data.settings.Settings
import io.github.jpcottin.lenslate.domain.LiveTranslationState
import io.github.jpcottin.lenslate.ui.phone.home.HomeScreen
import io.github.jpcottin.lenslate.ui.phone.settings.SettingsScreen
import io.github.jpcottin.lenslate.ui.preview.PreviewData
import io.github.jpcottin.lenslate.ui.theme.LenslateTheme

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
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
            onSetLanguages = { _, _ -> },
            onSwapLanguages = {},
            onClearTranscript = {},
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
