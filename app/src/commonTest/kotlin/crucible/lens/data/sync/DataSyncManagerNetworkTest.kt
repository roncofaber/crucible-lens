package crucible.lens.data.sync

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CachedProjectContent
import crucible.lens.data.cache.ProjectCacheOwner
import crucible.lens.data.cache.ProjectContentDelta
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DataSyncManagerNetworkTest {
    private val target = ProjectSyncTarget("mfid-project-a", "project-a")

    @Test
    fun partialFailurePreservesReplicaAndRetryReplacesItAtomically() = runTest {
        var failDatasets = true
        val engine = projectContentEngine(failDatasets = { failDatasets })
        val repository = repositoryWith(engine)
        val manager = DataSyncManager(repository)
        val store = InMemoryProjectCacheStore()
        val owner = repository.projectCacheOwner("account-a")
        val previous = cachedContent("old-sample", "old-dataset")
        store.saveContent(owner, previous)
        repository.seedPersistedProjectData(previous)

        assertFailsWith<IllegalStateException> {
            manager.syncProject(store, "account-a", target, forceRefresh = true)
        }

        assertEquals(previous, store.loadContent(owner, target.projectMfid))
        assertEquals(listOf("old-sample"), repository.getCachedProjectSamples("project-a")?.map(Sample::uniqueId))
        assertEquals(listOf("old-dataset"), repository.getCachedProjectDatasets("project-a")?.map(Dataset::uniqueId))
        assertIs<ProjectSyncState.Failed>(manager.projectStates.value[target.projectMfid])

        failDatasets = false
        val refreshed = manager.syncProject(store, "account-a", target, forceRefresh = true)

        assertEquals(listOf("new-sample"), refreshed.samples.map(Sample::uniqueId))
        assertEquals(listOf("new-dataset"), refreshed.datasets.map(Dataset::uniqueId))
        assertEquals(refreshed, store.loadContent(owner, target.projectMfid))
        assertIs<ProjectSyncState.Ready>(manager.projectStates.value[target.projectMfid])
    }

    @Test
    fun markedReplicaRejectsDeltaAndForcesAuthoritativeRefresh() = runTest {
        var sampleCalls = 0
        var datasetCalls = 0
        val engine = projectContentEngine(
            failDatasets = { false },
            onSamples = { sampleCalls++ },
            onDatasets = { datasetCalls++ }
        )
        val repository = repositoryWith(engine)
        val manager = DataSyncManager(repository)
        val store = InMemoryProjectCacheStore()
        val owner = repository.projectCacheOwner("account-a")
        val previous = cachedContent("old-sample", "old-dataset")
        store.saveContent(owner, previous)
        repository.seedPersistedProjectData(previous)

        manager.markProjectForFullRefresh(store, "account-a", target.projectMfid)

        assertFailsWith<IllegalStateException> {
            manager.applyProjectDelta(
                store,
                "account-a",
                ProjectContentDelta(
                    projectMfid = target.projectMfid,
                    projectSlug = target.projectSlug,
                    synchronizedAt = previous.cachedAt + 1L,
                    samples = listOf(Sample(uniqueId = "delta-sample", projectId = "project-a"))
                )
            )
        }

        val refreshed = manager.syncProject(store, "account-a", target)

        assertEquals(1, sampleCalls)
        assertEquals(1, datasetCalls)
        assertEquals(listOf("new-sample"), refreshed.samples.map(Sample::uniqueId))
        assertEquals(listOf("new-dataset"), refreshed.datasets.map(Dataset::uniqueId))
        assertTrue(!refreshed.replica.requiresFullRefresh)
    }

    @Test
    fun changedProjectSlugForcesAuthoritativeRefresh() = runTest {
        var sampleCalls = 0
        var datasetCalls = 0
        val repository = repositoryWith(
            projectContentEngine(
                failDatasets = { false },
                onSamples = { sampleCalls++ },
                onDatasets = { datasetCalls++ }
            )
        )
        val manager = DataSyncManager(repository)
        val store = InMemoryProjectCacheStore()
        val owner = repository.projectCacheOwner("account-a")
        val previous = cachedContent("old-sample", "old-dataset", projectSlug = "previous-project-a")
        store.saveContent(owner, previous)
        repository.seedPersistedProjectData(previous)

        val refreshed = manager.syncProject(store, "account-a", target)

        assertEquals(1, sampleCalls)
        assertEquals(1, datasetCalls)
        assertEquals(target.projectSlug, refreshed.projectSlug)
        assertEquals(listOf("new-sample"), refreshed.samples.map(Sample::uniqueId))
        assertEquals(listOf("new-dataset"), refreshed.datasets.map(Dataset::uniqueId))
        assertEquals(null, repository.getCachedProjectSamples("previous-project-a"))
    }

    private fun repositoryWith(engine: MockEngine): CrucibleRepository {
        val apiClient = ApiClient.withEngine(engine)
        apiClient.setApiKey("test-key")
        apiClient.setBaseUrl("https://example.test/api/")
        return CrucibleRepository(apiClient).also { it.activateCacheScope("account-a") }
    }

    private fun cachedContent(
        sampleId: String,
        datasetId: String,
        projectSlug: String = target.projectSlug
    ) = CachedProjectContent(
        projectMfid = target.projectMfid,
        projectSlug = projectSlug,
        samples = listOf(Sample(uniqueId = sampleId, projectId = projectSlug)),
        datasets = listOf(Dataset(uniqueId = datasetId, projectId = projectSlug)),
        cachedAt = 100L
    )
}

private fun projectContentEngine(
    failDatasets: () -> Boolean,
    onSamples: () -> Unit = {},
    onDatasets: () -> Unit = {}
) = MockEngine { request ->
    when (request.url.encodedPath.substringAfterLast('/')) {
        "samples" -> {
            onSamples()
            respondJson(page("""{"unique_id":"new-sample","project_id":"project-a"}"""))
        }
        "datasets" -> {
            onDatasets()
            if (failDatasets()) {
                respondJson("{}", HttpStatusCode.InternalServerError)
            } else {
                respondJson(page("""{"unique_id":"new-dataset","project_id":"project-a"}"""))
            }
        }
        else -> respondJson("{}", HttpStatusCode.NotFound)
    }
}

private fun MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

private fun page(item: String): String =
    """{"total":1,"limit":1000,"next_cursor":null,"items":[$item]}"""

private class InMemoryProjectCacheStore : ProjectCacheStore {
    private val projects = mutableMapOf<ProjectCacheOwner, List<Project>>()
    private val contents = mutableMapOf<ProjectCacheOwner, MutableMap<String, CachedProjectContent>>()

    override suspend fun saveProjects(owner: ProjectCacheOwner, projects: List<Project>) {
        this.projects[owner] = projects
    }

    override suspend fun loadProjects(owner: ProjectCacheOwner): List<Project>? = projects[owner]

    override suspend fun saveContent(owner: ProjectCacheOwner, content: CachedProjectContent) {
        contents.getOrPut(owner) { mutableMapOf() }[content.projectMfid] = content
    }

    override suspend fun loadContent(owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent? =
        contents[owner]?.get(projectMfid)

    override suspend fun loadContents(owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent> =
        contents[owner].orEmpty().filterKeys { it in projectMfids }.values.toList()

    override suspend fun removeContent(owner: ProjectCacheOwner, projectMfid: String) {
        contents[owner]?.remove(projectMfid)
    }

    override suspend fun retainContents(owner: ProjectCacheOwner, projectMfids: Set<String>) {
        contents[owner]?.keys?.retainAll(projectMfids)
    }
}
