package com.jpcottin.lenslate.data.translate

import com.jpcottin.lenslate.domain.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    /** When set, a download stays in flight until this completes. */
    var downloadGate: CompletableDeferred<Unit>? = null,
) : TranslateModelStore {
    val downloadCalls = mutableListOf<String>()
    val deleteCalls = mutableListOf<String>()

    override suspend fun downloadedLanguageCodes(): Set<String> {
        if (failListing) throw IllegalStateException("no play services")
        return listingOverride ?: downloaded.toSet()
    }

    override suspend fun download(code: String) {
        downloadCalls += code
        downloadGate?.await()
        failDownloadWith?.let { throw it }
        downloaded += code
    }

    override suspend fun delete(code: String) {
        deleteCalls += code
        downloaded -= code
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelRepositoryTest {
    private fun TestScope.repository(store: TranslateModelStore) = ModelRepository(this, store)

    @Test
    fun initialStatuses_areNotDownloaded() = runTest {
        val repo = repository(FakeModelStore())
        assertTrue(repo.statuses.value.values.all { it == ModelStatus.NotDownloaded })
        assertEquals(Language.entries.toSet(), repo.statuses.value.keys)
    }

    @Test
    fun refresh_marksDownloadedModels() = runTest {
        val repo = repository(FakeModelStore(downloaded = mutableSetOf("en", "ja")))
        repo.refresh()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.ENGLISH])
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.JAPANESE])
        assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[Language.FRENCH])
    }

    @Test
    fun refresh_survivesListingFailure() = runTest {
        val repo = repository(FakeModelStore(failListing = true))
        repo.refresh()
        assertTrue(repo.statuses.value.values.all { it == ModelStatus.NotDownloaded })
    }

    @Test
    fun download_success_endsDownloaded() = runTest {
        val store = FakeModelStore()
        val repo = repository(store)
        repo.download(Language.GERMAN).join()
        assertEquals(listOf("de"), store.downloadCalls)
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.GERMAN])
    }

    @Test
    fun download_failure_endsFailed_andSurvivesRefresh() = runTest {
        val store = FakeModelStore(failDownloadWith = IllegalStateException("No network"))
        val repo = repository(store)
        repo.download(Language.SPANISH).join()
        assertEquals(ModelStatus.Failed("No network"), repo.statuses.value[Language.SPANISH])
        repo.refresh()
        assertEquals(ModelStatus.Failed("No network"), repo.statuses.value[Language.SPANISH])
    }

    @Test
    fun delete_endsNotDownloaded() = runTest {
        val store = FakeModelStore(downloaded = mutableSetOf("fr"))
        val repo = repository(store)
        repo.refresh()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])
        repo.delete(Language.FRENCH)
        assertEquals(listOf("fr"), store.deleteCalls)
        assertEquals(ModelStatus.NotDownloaded, repo.statuses.value[Language.FRENCH])
    }

    @Test
    fun refresh_doesNotDowngradeAJustDownloadedModel() = runTest {
        val store = FakeModelStore()
        val repo = repository(store)
        repo.download(Language.FRENCH).join()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])

        // A refresh that started before the download finished sees a stale snapshot.
        store.listingOverride = emptySet()
        repo.refresh()

        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.FRENCH])
    }

    @Test
    fun download_isReportedWhileInFlight_andReturnsAtOnce() = runTest {
        val store = FakeModelStore(downloadGate = CompletableDeferred())
        val repo = repository(store)

        val download = repo.download(Language.GERMAN)
        assertEquals(ModelStatus.Downloading, repo.statuses.value[Language.GERMAN])
        advanceUntilIdle()
        assertEquals(ModelStatus.Downloading, repo.statuses.value[Language.GERMAN])

        store.downloadGate?.complete(Unit)
        download.join()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.GERMAN])
    }

    @Test
    fun download_outlivesTheScreenThatStartedIt() = runTest {
        val store = FakeModelStore(downloadGate = CompletableDeferred())
        val repo = repository(store)
        // The settings screen starts the download from its own scope, then the user leaves.
        val screen = CoroutineScope(coroutineContext + Job())
        screen.launch { repo.download(Language.GERMAN) }
        advanceUntilIdle()
        screen.cancel()
        advanceUntilIdle()

        assertEquals(ModelStatus.Downloading, repo.statuses.value[Language.GERMAN])

        store.downloadGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(ModelStatus.Downloaded, repo.statuses.value[Language.GERMAN])
    }

    @Test
    fun download_cancelledWithTheApp_isNotReportedAsAFailure() = runTest {
        val store = FakeModelStore(downloadGate = CompletableDeferred())
        val app = CoroutineScope(coroutineContext + Job())
        val repo = ModelRepository(app, store)
        repo.download(Language.GERMAN)
        advanceUntilIdle()

        app.cancel()
        advanceUntilIdle()

        assertEquals(ModelStatus.Downloading, repo.statuses.value[Language.GERMAN])
    }
}
