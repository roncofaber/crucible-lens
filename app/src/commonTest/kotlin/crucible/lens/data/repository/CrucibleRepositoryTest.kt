package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Sample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrucibleRepositoryTest {

    @Test
    fun getCachedResourceReturnsNullWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.getCachedResource("unknown-uuid"))
    }

    @Test
    fun invalidateResourceIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        // Populating the cache first would require a real network call through
        // fetchResourceByUuid — no ApiClient mock exists in this project yet (see
        // Task 3 preamble). This test only confirms invalidate() doesn't throw on an
        // absent key; invalidate's actual removal behavior is covered directly by
        // ObservableCacheTest.invalidateRemovesEntry, which this method delegates to.
        repository.invalidateResource("unknown-uuid")
        assertNull(repository.getCachedResource("unknown-uuid"))
    }

    @Test
    fun resourceAgeMillisReturnsNullWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.resourceAgeMillis("unknown-uuid"))
    }

    @Test
    fun observeResourceEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeResource("unknown-uuid").first())
    }

    @Test
    fun observeProjectsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjects().first())
    }

    @Test
    fun observeInstrumentsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeInstruments().first())
    }

    @Test
    fun invalidateProjectsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateProjects()
    }

    @Test
    fun invalidateInstrumentsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateInstruments()
    }

    @Test
    fun observeProjectSamplesEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjectSamples("project-1").first())
    }

    @Test
    fun observeProjectDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjectDatasets("project-1").first())
    }

    @Test
    fun invalidateProjectDataIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateProjectData("project-1")
    }

    @Test
    fun observeInstrumentDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeInstrumentDatasets("Microscope A").first())
    }

    @Test
    fun invalidateInstrumentDatasetsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateInstrumentDatasets("Microscope A")
    }

    @Test
    fun observeThumbnailsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeThumbnails("dataset-uuid").first())
    }

    @Test
    fun invalidateThumbnailsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateThumbnails("dataset-uuid")
    }

    @Test
    fun fetchSiblingsReturnsSingleItemListWhenResourceHasNoProjectId() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        val sample = Sample(uniqueId = "s1", sampleName = "Sample 1", projectId = null)
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(listOf(sample), result)
    }

    @Test
    fun fetchSiblingsFallsBackToSingleItemWhenNetworkUnavailable() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        val sample = Sample(uniqueId = "s1", sampleName = "A", projectId = "p1", sampleType = "TypeX")
        // No project cache populated and no real network in this JVM unit test — getFilteredSamples
        // will throw or return an error; fetchSiblings must not propagate that as a crash, and must
        // still return a list containing at least the resource itself.
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(true, result.any { it.uniqueId == "s1" })
    }
}
