package com.jpcottin.lenslate.ui.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.IconButton
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.TitleChip
import androidx.xr.glimmer.TitleChipDefaults
import com.jpcottin.lenslate.R
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.UtteranceKind
import com.jpcottin.lenslate.ui.preview.PreviewData

/**
 * The lens UI, built with Jetpack Compose Glimmer.
 *
 * Follows the Display AI Glasses guidance: pure black root (renders transparent on the additive
 * display), a single bottom-aligned card showing one thing at a time — the latest translation —
 * with a title chip for the language pair. The card itself is the primary target: it holds the
 * initial focus, so a bare tap on the touchpad pauses/resumes listening. A camera icon button
 * under the card (one swipe away) — or the glasses' hardware camera button — triggers Read. The
 * card is always composed, even while the lens is off: the activity speaks translations aloud in
 * that case, and the UI is simply there when the display comes back.
 */
@Composable
fun GlassesScreen(
    live: LiveTranslationState,
    showSource: Boolean,
    isVisualUiSupported: Boolean,
    permissionDenied: Boolean,
    onToggleListening: () -> Unit,
    onRead: () -> Unit,
    onRetryPermission: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** When the denied permission is the camera (Read mode) rather than the microphone. */
    cameraPermissionDenied: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when {
            permissionDenied || cameraPermissionDenied -> PermissionCard(
                message = stringResource(
                    if (cameraPermissionDenied) R.string.glasses_camera_permission_needed
                    else R.string.glasses_permission_needed
                ),
                onRetry = onRetryPermission,
                onExit = onExit,
            )
            !isVisualUiSupported -> Text(
                stringResource(R.string.glasses_audio_only),
                style = GlimmerTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            else -> TranslationCard(live, showSource, onToggleListening, onRead)
        }
    }
}

@Composable
private fun TranslationCard(
    live: LiveTranslationState,
    showSource: Boolean,
    onToggleListening: () -> Unit,
    onRead: () -> Unit,
) {
    val speaking = live.partialSource.isNotEmpty()
    val translation = when {
        speaking -> live.partialTranslation.ifEmpty { "…" }
        else -> live.latest?.translation ?: live.latest?.error ?: ""
    }
    val source = if (speaking) live.partialSource else live.latest?.source.orEmpty()
    val status = when {
        live.isReading -> stringResource(R.string.reading)
        live.isListening && live.isPreparing -> stringResource(R.string.models_downloading, "${live.from.shortLabel} → ${live.to.shortLabel}")
        live.isListening -> stringResource(R.string.glasses_listening)
        else -> stringResource(R.string.glasses_paused)
    }
    val error = when (live.error) {
        null -> null
        LiveTranslator.NO_TEXT_FOUND -> stringResource(R.string.read_no_text)
        else -> live.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleChip(
            leadingIcon = {
                Icon(
                    imageVector = when {
                        live.isReading || live.latest?.kind == UtteranceKind.READ && !speaking -> Icons.Rounded.PhotoCamera
                        live.isListening -> Icons.Rounded.Mic
                        else -> Icons.Rounded.MicOff
                    },
                    contentDescription = status,
                )
            },
        ) {
            Text("${live.from.shortLabel} → ${live.to.shortLabel}", maxLines = 1)
        }
        Spacer(Modifier.height(TitleChipDefaults.associatedContentSpacing))
        Card(
            onClick = onToggleListening,
            modifier = Modifier.fillMaxWidth(),
            title = { Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (translation.isEmpty() && source.isEmpty()) {
                    Text(
                        stringResource(if (live.isListening) R.string.glasses_welcome else R.string.glasses_tap_to_listen),
                        style = GlimmerTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        translation,
                        style = GlimmerTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showSource && source.isNotEmpty()) {
                        Text(
                            source,
                            style = GlimmerTheme.typography.caption,
                            color = GlimmerTheme.colors.outline,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (error != null && (!live.isListening || translation.isEmpty())) {
                    Text(
                        stringResource(R.string.glasses_error_prefix, error),
                        style = GlimmerTheme.typography.caption,
                        color = GlimmerTheme.colors.negative,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = onRead, enabled = !live.isReading) {
            Icon(imageVector = Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.glasses_read))
        }
    }
}

@Composable
private fun PermissionCard(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(message, style = GlimmerTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.glasses_retry)) }
            Button(onClick = onExit) { Text(stringResource(R.string.glasses_exit)) }
        }
    }
}

/** The projected display of the Display AI Glasses emulator is 450×394 dp at 160 dpi. */
@Preview(name = "Glasses", device = "spec:width=450dp,height=394dp,dpi=160", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun GlassesScreenPreview() {
    GlimmerTheme {
        GlassesScreen(
            live = PreviewData.glassesTranslation,
            showSource = true,
            isVisualUiSupported = true,
            permissionDenied = false,
            onToggleListening = {},
            onRead = {},
            onRetryPermission = {},
            onExit = {},
        )
    }
}
