package com.jpcottin.lenslate.ui.phone.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.jpcottin.lenslate.R
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.UtteranceKind
import com.jpcottin.lenslate.domain.Utterance
import com.jpcottin.lenslate.ui.preview.PreviewData
import com.jpcottin.lenslate.ui.theme.LenslateTheme

/**
 * Stateless phone home screen. Adapts to the window width: controls and transcript stack
 * vertically on phones and sit side by side on tablets, unfolded foldables and desktop windows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    live: LiveTranslationState,
    settings: Settings,
    glassesConnected: Boolean,
    micPermissionDenied: Boolean,
    launchError: String?,
    onToggleListening: () -> Unit,
    onRead: () -> Unit,
    onSetLanguages: (Language, Language) -> Unit,
    onSwapLanguages: () -> Unit,
    onConversationModeChange: (Boolean) -> Unit,
    onClearTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
    onCopyUtterance: (Utterance) -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchOnGlasses: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPermissionDenied: Boolean = false,
    /** Overrides the layout choice (used by tests/previews); null measures the pane itself. */
    isWideWindow: Boolean? = null,
) {
    BoxWithConstraints(modifier) {
    // The screen can share the window with the Settings supporting pane, so decide the layout
    // from this pane's own width, not from the window size class.
    val isWide = isWideWindow ?: (maxWidth >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onShareTranscript, enabled = live.utterances.isNotEmpty()) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_transcript))
                    }
                    IconButton(onClick = onClearTranscript, enabled = live.utterances.isNotEmpty()) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.clear_transcript))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val readLabel = stringResource(R.string.read)
                SmallFloatingActionButton(
                    onClick = onRead,
                    modifier = Modifier.semantics { contentDescription = readLabel },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    if (live.isReading) CircularProgressIndicator(Modifier.size(20.dp))
                    else Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                }
                ExtendedFloatingActionButton(
                    onClick = onToggleListening,
                    icon = {
                        Icon(
                            if (live.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(if (live.isListening) R.string.stop_listening else R.string.listen)) },
                )
            }
        },
    ) { innerPadding ->
        val controls: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassesCard(glassesConnected, launchError, onLaunchOnGlasses)
                LanguagePairCard(settings, onSetLanguages, onSwapLanguages, onConversationModeChange)
                StatusBanner(live, settings, micPermissionDenied, cameraPermissionDenied)
            }
        }
        if (isWide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                controls(Modifier.width(360.dp))
                Transcript(live, onCopyUtterance, Modifier.weight(1f), bottomPadding = 88.dp)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                controls(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Transcript(live, onCopyUtterance, Modifier.weight(1f), bottomPadding = 88.dp)
            }
        }
    }
    }
}

@Composable
private fun GlassesCard(connected: Boolean, launchError: String?, onLaunch: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (connected) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (connected) R.string.glasses_connected else R.string.glasses_disconnected),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    launchError ?: stringResource(R.string.launch_on_glasses_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (launchError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onLaunch, enabled = connected) {
                Text(stringResource(R.string.launch_on_glasses))
            }
        }
    }
}

@Composable
private fun LanguagePairCard(
    settings: Settings,
    onSetLanguages: (Language, Language) -> Unit,
    onSwap: () -> Unit,
    onConversationModeChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LanguageDropdown(
                label = stringResource(R.string.from_language),
                selected = settings.from,
                onSelect = { onSetLanguages(it, settings.to) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.swap_languages))
            }
            LanguageDropdown(
                label = stringResource(R.string.to_language),
                selected = settings.to,
                onSelect = { onSetLanguages(settings.from, it) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.conversation_mode), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.conversation_mode_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.conversationMode, onCheckedChange = onConversationModeChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.nativeName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.nativeName} (${language.shortLabel})") },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    live: LiveTranslationState,
    settings: Settings,
    micPermissionDenied: Boolean,
    cameraPermissionDenied: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResource(
                            if (settings.engine == EngineKind.GEMINI) R.string.engine_gemini else R.string.engine_on_device
                        )
                    )
                },
            )
            if (live.isListening) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.listening), style = MaterialTheme.typography.labelLarge)
            }
            if (live.isReading) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.reading), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (live.isPreparing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.models_downloading, "${live.from.shortLabel} → ${live.to.shortLabel}"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val error = when {
            micPermissionDenied -> stringResource(R.string.mic_permission_denied)
            cameraPermissionDenied -> stringResource(R.string.camera_permission_denied)
            live.error == LiveTranslator.NO_TEXT_FOUND -> stringResource(R.string.read_no_text)
            live.error != null -> live.error
            else -> null
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Transcript(
    live: LiveTranslationState,
    onCopyUtterance: (Utterance) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val listState = rememberLazyListState()
    val itemCount = live.utterances.size + if (live.partialSource.isNotEmpty()) 1 else 0
    LaunchedEffect(itemCount, live.partialTranslation) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    if (live.utterances.isEmpty() && live.partialSource.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.transcript_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
    ) {
        items(live.utterances, key = { it.id }) { utterance ->
            UtteranceItem(utterance, onCopy = { onCopyUtterance(utterance) })
        }
        if (live.partialSource.isNotEmpty()) {
            item(key = "partial") {
                UtteranceItem(
                    Utterance(id = -1, source = live.partialSource, translation = live.partialTranslation),
                    onCopy = {},
                    isPartial = true,
                )
            }
        }
    }
}

@Composable
private fun UtteranceItem(utterance: Utterance, onCopy: () -> Unit, isPartial: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPartial) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    utterance.source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (utterance.kind == UtteranceKind.READ) Icons.Rounded.PhotoCamera else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = if (utterance.kind == UtteranceKind.READ) stringResource(R.string.read) else null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(16.dp),
                    )
                    when {
                        utterance.error != null -> Text(utterance.error, color = MaterialTheme.colorScheme.error)
                        utterance.translation.isNullOrEmpty() -> Text("…", style = MaterialTheme.typography.titleMedium)
                        else -> Text(utterance.translation, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (!isPartial) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy_translation),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "Phone", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LenslateTheme(dynamicColor = false) {
        HomeScreen(
            live = PreviewData.transcript,
            settings = Settings(),
            glassesConnected = true,
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
            isWideWindow = false,
        )
    }
}

@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun HomeScreenWidePreview() {
    LenslateTheme(dynamicColor = false) {
        HomeScreen(
            live = PreviewData.transcript,
            settings = Settings(),
            glassesConnected = false,
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
            isWideWindow = true,
        )
    }
}
