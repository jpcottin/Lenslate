package com.jpcottin.lenslate.data.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.util.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ModelStatus {
    data object Downloaded : ModelStatus
    data object NotDownloaded : ModelStatus
    data object Downloading : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** Storage of offline translation models, keyed by ISO 639-1 code. */
interface TranslateModelStore {
    suspend fun downloadedLanguageCodes(): Set<String>
    suspend fun download(code: String)
    suspend fun delete(code: String)
}

/** [TranslateModelStore] backed by ML Kit's [RemoteModelManager]. */
class MlKitModelStore(
    private val manager: RemoteModelManager = RemoteModelManager.getInstance(),
) : TranslateModelStore {
    override suspend fun downloadedLanguageCodes(): Set<String> =
        manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()

    override suspend fun download(code: String) {
        manager.download(model(code), DownloadConditions.Builder().build()).await()
    }

    override suspend fun delete(code: String) {
        manager.deleteDownloadedModel(model(code)).await()
    }

    private fun model(code: String) = TranslateRemoteModel.Builder(code).build()
}

/** Manages the offline translation models from the phone's settings screen. */
class ModelRepository(private val store: TranslateModelStore = MlKitModelStore()) {
    private val _statuses = MutableStateFlow<Map<Language, ModelStatus>>(
        Language.entries.associateWith { ModelStatus.NotDownloaded }
    )
    val statuses: StateFlow<Map<Language, ModelStatus>> = _statuses.asStateFlow()

    suspend fun refresh() {
        val downloaded = runCatching { store.downloadedLanguageCodes() }.getOrDefault(emptySet())
        _statuses.update { old ->
            Language.entries.associateWith { language ->
                when {
                    language.code in downloaded -> ModelStatus.Downloaded
                    old[language] is ModelStatus.Downloading -> ModelStatus.Downloading
                    old[language] is ModelStatus.Failed -> old.getValue(language)
                    else -> ModelStatus.NotDownloaded
                }
            }
        }
    }

    suspend fun download(language: Language) {
        _statuses.update { it + (language to ModelStatus.Downloading) }
        runCatching { store.download(language.code) }
            .onSuccess { _statuses.update { it + (language to ModelStatus.Downloaded) } }
            .onFailure { e -> _statuses.update { it + (language to ModelStatus.Failed(e.message ?: "Download failed")) } }
        refresh()
    }

    suspend fun delete(language: Language) {
        runCatching { store.delete(language.code) }
        _statuses.update { it + (language to ModelStatus.NotDownloaded) }
        refresh()
    }
}
