package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CachedProjectContent
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.ResourceLink
import crucible.lens.data.model.Sample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrucibleRepositoryTest {

    @Test
    fun getCachedResourceReturnsNullWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.getCachedResource("unknown-uuid"))
    }

    @Test
    fun invalidateResourceIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
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
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.resourceAgeMillis("unknown-uuid"))
    }

    @Test
    fun observeResourceEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeResource("unknown-uuid").first())
    }

    @Test
    fun observeProjectsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeProjects().first())
    }

    @Test
    fun observeInstrumentsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeInstruments().first())
    }

    @Test
    fun invalidateProjectsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        repository.invalidateProjects()
    }

    @Test
    fun persistedProjectSummariesRemainAvailableAfterMemoryInvalidation() {
        val repository = CrucibleRepository(ApiClient())
        val project = Project(uniqueId = "mfid-project-1", projectId = "project-1", title = "Offline project")

        repository.seedPersistedProjects(listOf(project))
        repository.invalidateProjects()

        assertEquals(listOf(project), repository.getCachedProjects())

        repository.invalidateAll()
        assertNull(repository.getCachedProjects())
    }

    @Test
    fun invalidateInstrumentsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        repository.invalidateInstruments()
    }

    @Test
    fun observeProjectSamplesEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeProjectSamples("project-1").first())
    }

    @Test
    fun observeProjectDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeProjectDatasets("project-1").first())
    }

    @Test
    fun invalidateProjectDataIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        repository.invalidateProjectData("project-1")
    }

    @Test
    fun persistedProjectDataSeedsOfflineListsAndResources() {
        val repository = CrucibleRepository(ApiClient())
        val sample = Sample(uniqueId = "sample-1", projectId = "project-1")
        val dataset = Dataset(uniqueId = "dataset-1", projectId = "project-1")

        repository.seedPersistedProjectData(
            CachedProjectContent("mfid-project-1", "project-1", listOf(sample), listOf(dataset), cachedAt = 1234L)
        )

        assertEquals(listOf(sample), repository.getCachedProjectSamples("project-1"))
        assertEquals(listOf(dataset), repository.getCachedProjectDatasets("project-1"))
        assertEquals(sample, repository.getCachedResource("sample-1"))
        assertEquals(dataset, repository.getCachedResource("dataset-1"))
        assertTrue(repository.hasPersistedProjectData("mfid-project-1"))

        repository.removePersistedProjectData("mfid-project-1")

        assertNull(repository.getCachedProjectSamples("project-1"))
        assertNull(repository.getCachedProjectDatasets("project-1"))
    }

    @Test
    fun persistedSummariesDoNotReplaceDetailedResource() = runTest {
        val repository = CrucibleRepository(ApiClient())
        val link = ResourceLink("dataset-1", "dataset", relationship = "associated")
        val detail = Sample(uniqueId = "sample-1", sampleName = "Detailed", links = listOf(link))
        repository.cacheResource(detail.uniqueId, detail)

        repository.seedPersistedProjectData(
            CachedProjectContent(
                projectMfid = "mfid-project-1",
                projectSlug = "project-1",
                samples = listOf(Sample(uniqueId = "sample-1", sampleName = "Summary", projectId = "project-1")) +
                    (2..100).map { Sample(uniqueId = "sample-$it", projectId = "project-1") },
                datasets = emptyList(),
                cachedAt = 1234L
            )
        )

        assertEquals(detail, repository.getCachedResource("sample-1"))
        assertEquals(detail, repository.observeResource("sample-1").first())
    }

    @Test
    fun resourceMutationResponsePreservesExistingDetailFields() {
        val repository = CrucibleRepository(ApiClient())
        val link = ResourceLink("dataset-1", "dataset", relationship = "associated")
        val detail = Sample(uniqueId = "sample-1", sampleName = "Before", links = listOf(link))
        repository.cacheResource(detail.uniqueId, detail)

        repository.cacheResource(
            detail.uniqueId,
            Sample(uniqueId = "sample-1", sampleName = "After", links = null)
        )

        assertEquals("After", (repository.getCachedResource("sample-1") as Sample).sampleName)
        assertEquals(listOf(link), (repository.getCachedResource("sample-1") as Sample).links)
    }

    @Test
    fun liveProjectSummariesTakePrecedenceUnderCanonicalMfidKey() = runTest {
        val repository = CrucibleRepository(ApiClient())
        val projectMfid = "01k4abcdefghjkmnpqrstvwxyz"
        val persisted = Project(uniqueId = projectMfid, projectId = "project-1", title = "Persisted")
        val live = Project(uniqueId = projectMfid, projectId = "project-1", title = "Live")

        repository.seedPersistedProjects(listOf(persisted))
        repository.seedProjects(listOf(live))

        assertNull(repository.getCachedProject("project-1"))
        assertEquals(live, repository.getCachedProject(projectMfid))
        assertEquals(live, repository.observeProject(projectMfid).first())
    }

    @Test
    fun stalePersistenceRestoreCannotCrossCacheScope() {
        val repository = CrucibleRepository(ApiClient())
        val staleEpoch = repository.captureCacheEpoch()

        repository.invalidateAll()
        repository.seedPersistedProjects(listOf(Project(uniqueId = "mfid-stale", projectId = "stale")), staleEpoch)
        repository.seedPersistedProjectData(
            CachedProjectContent("mfid-stale", "stale", listOf(Sample(uniqueId = "stale-sample")), emptyList(), 1L),
            staleEpoch
        )

        assertNull(repository.getCachedProjects())
        assertNull(repository.getCachedResource("stale-sample"))
    }

    @Test
    fun staleMutationResponseCannotCrossCacheScope() {
        val repository = CrucibleRepository(ApiClient())
        val staleEpoch = repository.captureCacheEpoch()

        repository.invalidateAll()
        repository.cacheResource("stale-sample", Sample(uniqueId = "stale-sample"), staleEpoch)
        repository.cacheMyJoinRequests(emptyList())
        repository.updateCachedMyJoinRequest(
            JoinRequest(1, "stale-project", "stale-user", status = "pending"),
            staleEpoch
        )

        assertNull(repository.getCachedResource("stale-sample"))
        assertEquals(emptyList(), repository.getCachedMyJoinRequests())
    }

    @Test
    fun cacheScopeIsStableUntilAccountOrServerChanges() {
        val apiClient = ApiClient()
        val repository = CrucibleRepository(apiClient)
        val project = Project(uniqueId = "mfid-project-a", projectId = "project-a")

        repository.activateCacheScope("account-a")
        repository.seedProjects(listOf(project))
        repository.activateCacheScope("account-a")
        assertEquals(listOf(project), repository.getCachedProjects())

        repository.activateCacheScope("account-b")
        assertNull(repository.getCachedProjects())

        repository.seedProjects(listOf(project))
        apiClient.setBaseUrl("https://other.test/api/")
        repository.activateCacheScope("account-b")
        assertNull(repository.getCachedProjects())
    }

    @Test
    fun observeInstrumentDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeInstrumentDatasets("instrument-mfid").first())
    }

    @Test
    fun invalidateInstrumentDatasetsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        repository.invalidateInstrumentDatasets("instrument-mfid")
    }

    @Test
    fun instrumentDatasetPagesAppendWithoutDuplicates() {
        val existing = Dataset(uniqueId = "dataset-2")
        val duplicate = Dataset(uniqueId = "dataset-2", datasetName = "Updated")
        val added = Dataset(uniqueId = "dataset-1")

        val result = mergeInstrumentDatasetPage(
            current = InstrumentDatasetPage(listOf(existing), "dataset-2"),
            incoming = listOf(duplicate, added),
            nextCursor = null,
            append = true
        )

        assertEquals(listOf("dataset-2", "dataset-1"), result.datasets.map { it.uniqueId })
        assertNull(result.nextCursor)
    }

    @Test
    fun observeThumbnailsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient())
        assertNull(repository.observeThumbnails("dataset-uuid").first())
    }

    @Test
    fun invalidateThumbnailsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient())
        repository.invalidateThumbnails("dataset-uuid")
    }

    @Test
    fun cachedJoinRequestsAreObservableAndUpdatedAfterSubmission() = runTest {
        val repository = CrucibleRepository(ApiClient())
        val existing = JoinRequest(1, "project-1", "user-1", status = "pending")
        val submitted = JoinRequest(2, "project-2", "user-1", status = "pending")

        repository.cacheMyJoinRequests(listOf(existing))
        repository.updateCachedMyJoinRequest(submitted)

        assertEquals(listOf(existing, submitted), repository.observeMyJoinRequests().first())
    }

    @Test
    fun accountCacheInvalidationClearsJoinRequests() = runTest {
        val repository = CrucibleRepository(ApiClient())
        repository.cacheMyJoinRequests(listOf(JoinRequest(1, "project-1", "user-1", status = "pending")))

        repository.invalidateAll()

        assertNull(repository.getCachedMyJoinRequests())
        assertNull(repository.observeMyJoinRequests().first())
    }

    @Test
    fun submittedJoinRequestDoesNotCreatePartialCache() {
        val repository = CrucibleRepository(ApiClient())

        repository.updateCachedMyJoinRequest(JoinRequest(1, "project-1", "user-1", status = "pending"))

        assertNull(repository.getCachedMyJoinRequests())
    }

    @Test
    fun fetchSiblingsReturnsSingleItemListWhenResourceHasNoProjectId() = runTest {
        val repository = CrucibleRepository(ApiClient())
        val sample = Sample(uniqueId = "s1", sampleName = "Sample 1", projectId = null)
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(listOf(sample), result)
    }

    @Test
    fun fetchSiblingsFallsBackToSingleItemWhenNetworkUnavailable() = runTest {
        val repository = CrucibleRepository(ApiClient())
        val sample = Sample(uniqueId = "s1", sampleName = "A", projectId = "p1", sampleType = "TypeX")
        // No project cache populated and no real network in this JVM unit test — getFilteredSamples
        // will throw or return an error; fetchSiblings must not propagate that as a crash, and must
        // still return a list containing at least the resource itself.
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(true, result.any { it.uniqueId == "s1" })
    }
}
