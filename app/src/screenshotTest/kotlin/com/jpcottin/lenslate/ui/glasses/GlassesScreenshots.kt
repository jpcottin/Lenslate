package com.jpcottin.lenslate.ui.glasses

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.xr.glimmer.GlimmerTheme
import com.android.tools.screenshot.PreviewTest
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.Utterance
import com.jpcottin.lenslate.domain.UtteranceKind

/** The projected display of the Display AI Glasses emulator is 450×394 dp at 160 dpi. */
@Preview(name = "Glasses", device = "spec:width=450dp,height=394dp,dpi=160", showBackground = true, backgroundColor = 0xFF000000)
annotation class GlassesPreview

private fun glasses(live: LiveTranslationState, showSource: Boolean = true, permissionDenied: Boolean = false) = @Composable {
    GlimmerTheme {
        GlassesScreen(
            live = live,
            showSource = showSource,
            isVisualUiSupported = true,
            permissionDenied = permissionDenied,
            onToggleListening = {},
            onRead = {},
            onRetryPermission = {},
            onExit = {},
        )
    }
}

@PreviewTest
@GlassesPreview
@Composable
fun GlassesIdle() = glasses(LiveTranslationState(isListening = true))()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesTranslation() = glasses(
    LiveTranslationState(
        isListening = true,
        from = Language.FRENCH,
        to = Language.ENGLISH,
        utterances = listOf(Utterance(1, "Où est la gare, s'il vous plaît ?", "Where is the train station, please?")),
    )
)()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesPartialNoSource() = glasses(
    LiveTranslationState(
        isListening = true,
        from = Language.JAPANESE,
        to = Language.FRENCH,
        partialSource = "駅はどこですか",
        partialTranslation = "Où est la gare",
    ),
    showSource = false,
)()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesPaused() = glasses(
    LiveTranslationState(isListening = false, utterances = listOf(Utterance(1, "Hallo", "Hello")))
)()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesPermissionDenied() = glasses(LiveTranslationState(), permissionDenied = true)()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesReadResult() = glasses(
    LiveTranslationState(
        isListening = true,
        from = Language.FRENCH,
        to = Language.ENGLISH,
        utterances = listOf(Utterance(1, "SORTIE DE SECOURS Ne pas obstruer", "EMERGENCY EXIT Do not obstruct", kind = UtteranceKind.READ)),
    )
)()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesReading() = glasses(LiveTranslationState(isListening = true, isReading = true))()

@PreviewTest
@GlassesPreview
@Composable
fun GlassesCameraPermissionDenied() {
    GlimmerTheme {
        GlassesScreen(
            live = LiveTranslationState(),
            showSource = true,
            isVisualUiSupported = true,
            permissionDenied = false,
            cameraPermissionDenied = true,
            onToggleListening = {},
            onRead = {},
            onRetryPermission = {},
            onExit = {},
        )
    }
}
