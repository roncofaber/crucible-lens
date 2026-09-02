package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.AccessPrincipalType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CrucibleRepositoryNetworkTest {
    @Test
    fun instrumentLookupUsesCanonicalMfidReference() = runTest {
        val mfid = "01k4abcdefghjkmnpqrstvwxyz"
        val engine = MockEngine { request ->
            assertEquals("/api/instruments/$mfid", request.url.encodedPath)
            respondJson("""{"unique_id":"$mfid","instrument_id":"instrument-a","instrument_name":"Instrument"}""")
        }
        val repository = repositoryWith(engine)

        val result = repository.fetchInstrument(mfid)

        assertEquals(mfid, assertIs<ApiResult.Success<Instrument>>(result).data.uniqueId)
        assertEquals("instrument-a", repository.getCachedInstrument(mfid)?.instrumentId)
    }

    @Test
    fun instrumentSlugLookupUsesExactFilterAndCachesOnlyByMfid() = runTest {
        val mfid = "01k4abcdefghjkmnpqrstvwxyz"
        val engine = MockEngine { request ->
            assertEquals("/api/instruments", request.url.encodedPath)
            assertEquals("instrument-a", request.url.parameters["instrument_id"])
            assertEquals("2", request.url.parameters["limit"])
            assertEquals("0", request.url.parameters["offset"])
            respondJson(
                """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"$mfid","instrument_id":"instrument-a","instrument_name":"Instrument"}]}"""
            )
        }
        val repository = repositoryWith(engine)

        val result = repository.fetchInstrument("instrument-a")

        assertEquals(mfid, assertIs<ApiResult.Success<Instrument>>(result).data.uniqueId)
        assertEquals("instrument-a", repository.getCachedInstrument(mfid)?.instrumentId)
        assertNull(repository.getCachedInstrument("instrument-a"))
    }

    @Test
    fun instrumentListsAreCachedSeparatelyByStatus() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            val status = request.url.parameters["status"] ?: "missing"
            respondJson(
                """{"total":1,"limit":1000,"offset":0,"items":[{"unique_id":"$status-instrument","instrument_id":"$status-instrument","status":"$status"}]}"""
            )
        }
        val repository = repositoryWith(engine)

        repository.fetchInstruments(status = InstrumentStatus.Active)
        repository.fetchInstruments(status = InstrumentStatus.Maintenance)
        repository.fetchInstruments(status = InstrumentStatus.Active)
        repository.fetchInstruments(status = InstrumentStatus.Maintenance)

        assertEquals(2, calls)
        assertEquals("active-instrument", repository.getCachedInstruments(InstrumentStatus.Active)?.single()?.uniqueId)
        assertEquals("maintenance-instrument", repository.getCachedInstruments(InstrumentStatus.Maintenance)?.single()?.uniqueId)
    }

    @Test
    fun projectLookupUsesCanonicalMfidReference() = runTest {
        val mfid = "01k4abcdefghjkmnpqrstvwxyz"
        val engine = MockEngine { request ->
            assertEquals("/api/projects/$mfid", request.url.encodedPath)
            respondJson("""{"unique_id":"$mfid","project_id":"project-a","title":"Project"}""")
        }
        val repository = repositoryWith(engine)

        val result = repository.fetchProject(mfid)

        assertEquals(mfid, assertIs<ApiResult.Success<Project>>(result).data.uniqueId)
        assertEquals("project-a", repository.getCachedProject(mfid)?.projectId)
    }

    @Test
    fun projectSlugLookupUsesExactFilterAndCachesOnlyByMfid() = runTest {
        val mfid = "01k4abcdefghjkmnpqrstvwxyz"
        val engine = MockEngine { request ->
            assertEquals("/api/projects", request.url.encodedPath)
            assertEquals("project-a", request.url.parameters["project_id"])
            assertEquals("2", request.url.parameters["limit"])
            assertEquals("0", request.url.parameters["offset"])
            respondJson(
                """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"$mfid","project_id":"project-a","title":"Project"}]}"""
            )
        }
        val repository = repositoryWith(engine)

        val result = repository.fetchProject("project-a")

        assertEquals(mfid, assertIs<ApiResult.Success<Project>>(result).data.uniqueId)
        assertEquals("project-a", repository.getCachedProject(mfid)?.projectId)
        assertNull(repository.getCachedProject("project-a"))
    }

    @Test
    fun responseFromPreviousScopeIsReturnedButNotCached() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MockEngine {
            started.complete(Unit)
            release.await()
            respondJson(projectsPage("old-project"))
        }
        val repository = repositoryWith(engine)
        repository.activateCacheScope("account-a")

        val request = async { repository.fetchProjects(forceRefresh = true) }
        started.await()
        repository.activateCacheScope("account-b")
        release.complete(Unit)

        assertIs<ApiResult.Success<*>>(request.await())
        assertNull(repository.getCachedProjects())
    }

    @Test
    fun concurrentForcedProjectRequestsUseOneHttpCall() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val engine = MockEngine {
            calls++
            started.complete(Unit)
            release.await()
            respondJson(projectsPage("project-a"))
        }
        val repository = repositoryWith(engine)
        repository.activateCacheScope("account-a")

        val requests = List(20) { async { repository.fetchProjects(forceRefresh = true) } }
        started.await()
        repeat(20) { yield() }
        release.complete(Unit)
        val results = requests.awaitAll()

        assertEquals(1, calls)
        assertEquals(20, results.count { it is ApiResult.Success })
        assertEquals("project-a", repository.getCachedProjects()?.single()?.projectId)
        assertEquals("mfid-project-a", repository.getCachedProjects()?.single()?.uniqueId)
    }

    @Test
    fun publicAccessMutationUpdatesAclAndPreservesResourceDetail() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/access") -> respondJson(
                    """[{"principal_id":"owner-a","principal_type":"user","permission":"owner"}]"""
                )
                request.method == io.ktor.http.HttpMethod.Put -> respondJson(
                    """{"principal_id":"public","principal_type":"public","permission":"viewer","display_name":"Public"}"""
                )
                else -> respondJson("", HttpStatusCode.NoContent)
            }
        }
        val repository = repositoryWith(engine)
        repository.cacheResource(
            "sample-a",
            Sample(
                uniqueId = "sample-a",
                sampleName = "Sample",
                isPublic = false,
                links = listOf(crucible.lens.data.model.ResourceLink("dataset-a", "dataset"))
            )
        )
        repository.fetchResourceAccess("sample-a")

        assertIs<ApiResult.Success<*>>(repository.setResourcePublic("sample-a", true))
        val published = assertIs<Sample>(repository.getCachedResource("sample-a"))
        assertEquals(true, published.isPublic)
        assertEquals("dataset-a", published.links?.single()?.uniqueId)
        assertEquals(
            1,
            repository.getCachedResourceAccess("sample-a")?.count { it.principalType == AccessPrincipalType.Public }
        )

        assertIs<ApiResult.Success<*>>(repository.setResourcePublic("sample-a", false))
        val unpublished = assertIs<Sample>(repository.getCachedResource("sample-a"))
        assertEquals(false, unpublished.isPublic)
        assertEquals("dataset-a", unpublished.links?.single()?.uniqueId)
        assertEquals(
            0,
            repository.getCachedResourceAccess("sample-a")?.count { it.principalType == AccessPrincipalType.Public }
        )
    }

    private fun repositoryWith(engine: MockEngine): CrucibleRepository {
        val apiClient = ApiClient.withEngine(engine)
        apiClient.setApiKey("test-key")
        apiClient.setBaseUrl("https://example.test/api/")
        return CrucibleRepository(apiClient)
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
)

private fun projectsPage(projectId: String): String =
    """{"total":1,"limit":1000,"offset":0,"items":[{"unique_id":"mfid-$projectId","project_id":"$projectId","title":"Project"}]}"""
