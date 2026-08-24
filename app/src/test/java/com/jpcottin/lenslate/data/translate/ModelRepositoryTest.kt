package com.jpcottin.lenslate.data.translate

import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeModelStore(
    val downloaded: MutableSet<String> = mutableSetOf(),
    var failDownloadWith: Throwable? = null,
    var failListing: Boolean = false,
    /** When set, the listing reports this instead of [downloaded] — a stale disk snapshot. */
    var listingOverride: Set<String>? = null,
) : TranslateModelStore {
    val downloadCalls = mutableListOf<String>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun downloadedLanguageCodes(): Set<String> {
        if (failListing) throw IllegalStateException("no play services")
        return listingOverride ?: downloaded.toSet()
    }

    override suspend fun download(code: String) {
        downloadCalls += code
        failDownloadWith?.let { throw it }
        downloaded += code
    }

    override suspend fun delete(code: String) {
        deleteCalls += code
        downloaded -= code
    }
}

class ModelRepositoryTest {
    @Test
    fun initialStatuses_areNotDownloaded() {
        val repo = ModelRepository(FakeModelStore())
        assertTrue(repo.statuses.value.values.all { it == ModelStatus.NotDownloaded })
        assertEquals(Language.entries.toSet(), repo.statuses.value.keys)
    }

    @Test
    fun refresh_marksDownloadedModels() = runTest {
        val repo = ModelRepository(FakeModelStore(downloaded = mutableSetOf("en", "ja")))
        repo.refresh()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.ENGLISH])
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.JAPANESE])
        assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[Language.FRENCH])
    }

    @Test
    fun refresh_survivesListingFailure() = runTest {
        val repo = ModelRepository(FakeModelStore(failListing = true))
        repo.refresh()
        assertTrue(repo.statuses.value.values.all { it == ModelStatus.NotDownloaded })
    }

    @Test
    fun download_success_endsDownloaded() = runTest {
        val store = FakeModelStore()
        val repo = ModelRepository(store)
        repo.download(Language.GERMAN)
        assertEquals(listOf("de"), store.downloadCalls)
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.GERMAN])
    }

    @Test
    fun download_failure_endsFailed_andSurvivesRefresh() = runTest {
        val store = FakeModelStore(failDownloadWith = IllegalStateException("No network"))
        val repo = ModelRepository(store)
        repo.download(Language.SPANISH)
        assertEquals(ModelStatus.Failed("No network"), repo.statuses.value[Language.SPANISH])
        repo.refresh()
        assertEquals(ModelStatus.Failed("No network"), repo.statuses.value[Language.SPANISH])
    }

    @Test
    fun delete_endsNotDownloaded() = runTest {
        val store = FakeModelStore(downloaded = mutableSetOf("fr"))
        val repo = ModelRepository(store)
        repo.refresh()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])
        repo.delete(Language.FRENCH)
        assertEquals(listOf("fr"), store.deleteCalls)
        assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[Language.FRENCH])
    }

    @Test
    fun refresh_doesNotDowngradeAJustDownloadedModel() = runTest {
        val store = FakeModelStore()
        val repo = ModelRepository(store)
        repo.download(Language.FRENCH)
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])

        // A refresh that started before the download finished sees a stale snapshot.
        store.listingOverride = emptySet()
        repo.refresh()

        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])
    }
}
