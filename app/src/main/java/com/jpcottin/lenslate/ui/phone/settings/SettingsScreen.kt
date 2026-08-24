package com.jpcottin.lenslate.ui.phone.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jpcottin.lenslate.R
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.data.translate.ModelStatus
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.ui.preview.PreviewData
import com.jpcottin.lenslate.ui.theme.LenslateTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    models: Map<Language, ModelStatus>,
    onBack: () -> Unit,
    onEngineChange: (EngineKind) -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiModelChange: (String) -> Unit,
    onSpeakTranslationsChange: (Boolean) -> Unit,
    onShowSourceOnGlassesChange: (Boolean) -> Unit,
    onDownloadModel: (Language) -> Unit,
    onDeleteModel: (Language) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Readable line length on wide windows.
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(stringResource(R.string.settings_engine_title))
                EngineOption(
                    title = stringResource(R.string.settings_engine_on_device),
                    summary = stringResource(R.string.settings_engine_on_device_summary),
                    selected = settings.engine == EngineKind.ON_DEVICE,
                    onClick = { onEngineChange(EngineKind.ON_DEVICE) },
                )
                EngineOption(
                    title = stringResource(R.string.settings_engine_gemini),
                    summary = stringResource(R.string.settings_engine_gemini_summary),
                    selected = settings.engine == EngineKind.GEMINI,
                    onClick = { onEngineChange(EngineKind.GEMINI) },
                )
                if (settings.engine == EngineKind.GEMINI) {
                    GeminiFields(settings, onGeminiApiKeyChange, onGeminiModelChange)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.settings_speech_title))
                SwitchRow(
                    title = stringResource(R.string.settings_speak_translations),
                    summary = stringResource(R.string.settings_speak_translations_summary),
                    checked = settings.speakTranslations,
                    onCheckedChange = onSpeakTranslationsChange,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_show_source_on_glasses),
                    summary = stringResource(R.string.settings_show_source_on_glasses_summary),
                    checked = settings.showSourceOnGlasses,
                    onCheckedChange = onShowSourceOnGlassesChange,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.settings_models_title))
                Text(
                    stringResource(R.string.settings_models_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Language.entries.forEach { language ->
                    ModelRow(
                        language = language,
                        status = models[language] ?: ModelStatus.NotDownloaded,
                        onDownload = { onDownloadModel(language) },
                        onDelete = { onDeleteModel(language) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun EngineOption(title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GeminiFields(
    settings: Settings,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiModelChange: (String) -> Unit,
) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 36.dp)) {
        OutlinedTextField(
            value = settings.geminiApiKey,
            onValueChange = onGeminiApiKeyChange,
            label = { Text(stringResource(R.string.settings_gemini_api_key)) },
            supportingText = {
                Text(
                    stringResource(
                        if (settings.isGeminiConfigured) R.string.settings_gemini_api_key_hint
                        else R.string.settings_gemini_missing_key
                    )
                )
            },
            isError = !settings.isGeminiConfigured,
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.geminiModel,
            onValueChange = onGeminiModelChange,
            label = { Text(stringResource(R.string.settings_gemini_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelRow(language: Language, status: ModelStatus, onDownload: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("${language.nativeName} (${language.shortLabel})", style = MaterialTheme.typography.bodyLarge)
            val statusText = when (status) {
                ModelStatus.Downloaded -> stringResource(R.string.model_downloaded)
                ModelStatus.NotDownloaded -> stringResource(R.string.model_not_downloaded)
                ModelStatus.Downloading -> stringResource(R.string.model_downloading)
                is ModelStatus.Failed -> status.message
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is ModelStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (status) {
            ModelStatus.Downloading -> CircularProgressIndicator(Modifier.size(24.dp))
            ModelStatus.Downloaded -> TextButton(onClick = onDelete) { Text(stringResource(R.string.model_delete)) }
            else -> OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.model_download)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
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
