package io.github.jpcottin.lenslate.ui.phone.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.github.jpcottin.lenslate.R
import io.github.jpcottin.lenslate.data.settings.Settings
import io.github.jpcottin.lenslate.domain.EngineKind
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.LiveTranslationState
import io.github.jpcottin.lenslate.domain.Utterance
import io.github.jpcottin.lenslate.ui.preview.PreviewData
import io.github.jpcottin.lenslate.ui.theme.LenslateTheme

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
    onSetLanguages: (Language, Language) -> Unit,
    onSwapLanguages: () -> Unit,
    onClearTranscript: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchOnGlasses: () -> Unit,
    modifier: Modifier = Modifier,
    isWideWindow: Boolean = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
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
        },
    ) { innerPadding ->
        val controls: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassesCard(glassesConnected, launchError, onLaunchOnGlasses)
                LanguagePairCard(settings, onSetLanguages, onSwapLanguages)
                StatusBanner(live, settings, micPermissionDenied)
            }
        }
        if (isWideWindow) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                controls(Modifier.width(360.dp))
                Transcript(live, Modifier.weight(1f), bottomPadding = 88.dp)
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
                Transcript(live, Modifier.weight(1f), bottomPadding = 88.dp)
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
private fun StatusBanner(live: LiveTranslationState, settings: Settings, micPermissionDenied: Boolean) {
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
            live.error != null -> live.error
            else -> null
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Transcript(live: LiveTranslationState, modifier: Modifier = Modifier, bottomPadding: androidx.compose.ui.unit.Dp) {
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
        items(live.utterances, key = { it.id }) { utterance -> UtteranceItem(utterance) }
        if (live.partialSource.isNotEmpty()) {
            item(key = "partial") {
                UtteranceItem(Utterance(id = -1, source = live.partialSource, translation = live.partialTranslation), isPartial = true)
            }
        }
    }
}

@Composable
private fun UtteranceItem(utterance: Utterance, isPartial: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPartial) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                utterance.source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = if (isPartial) FontStyle.Italic else FontStyle.Normal,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
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
            onSetLanguages = { _, _ -> },
            onSwapLanguages = {},
            onClearTranscript = {},
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
            onSetLanguages = { _, _ -> },
            onSwapLanguages = {},
            onClearTranscript = {},
            onOpenSettings = {},
            onLaunchOnGlasses = {},
            isWideWindow = true,
        )
    }
}
