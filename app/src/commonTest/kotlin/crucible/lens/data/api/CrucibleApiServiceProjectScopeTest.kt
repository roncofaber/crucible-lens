package crucible.lens.data.api

import crucible.lens.data.model.ProjectRelation
import crucible.lens.data.model.ProjectScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrucibleApiServiceProjectScopeTest {
    @Test
    fun projectCollectionsUseCanonicalScopeAndDecodeContext() = runTest {
        val requests = mutableListOf<Triple<String, String?, String?>>()
        val engine = MockEngine { request ->
            requests += Triple(
                request.url.encodedPath,
                request.url.parameters["project_mfid"],
                request.url.parameters["project_scope"]
            )
            val item = if (request.url.encodedPath.endsWith("/samples")) {
                """{"unique_id":"sample-a","sample_name":"Sample","project":{"unique_id":"other-project-mfid","project_id":"other-project","title":"Other project"},"project_relation":"shared"}"""
            } else {
                """{"unique_id":"dataset-a","public":false,"project":{"unique_id":"other-project-mfid","project_id":"other-project","title":"Other project"},"project_relation":"shared"}"""
            }
            respond(
                content = """{"total":1,"limit":1000,"next_cursor":null,"items":[$item]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }.service

        val samples = assertIs<ApiResult.Success<List<crucible.lens.data.model.Sample>>>(
            service.getSamplesByProject("project-mfid", ProjectScope.Shared)
        )
        val datasets = assertIs<ApiResult.Success<List<crucible.lens.data.model.Dataset>>>(
            service.getDatasetsByProject("project-mfid", ProjectScope.Shared)
        )
        service.getSamplesByProject("assigned-project-mfid")

        assertEquals(ProjectRelation.Shared, samples.data.single().projectRelation)
        assertEquals("other-project-mfid", datasets.data.single().project?.uniqueId)
        assertEquals(
            listOf<Triple<String, String?, String?>>(
                Triple("/api/samples", "project-mfid", "shared"),
                Triple("/api/datasets", "project-mfid", "shared"),
                Triple("/api/samples", "assigned-project-mfid", "assigned")
            ),
            requests
        )
    }

    @Test
    fun projectSearchUsesCanonicalScopeWithoutPaginationParameters() = runTest {
        val requests = mutableListOf<Map<String, String?>>()
        val engine = MockEngine { request ->
            requests += mapOf(
                "path" to request.url.encodedPath,
                "q" to request.url.parameters["q"],
                "project_id" to request.url.parameters["project_id"],
                "project_mfid" to request.url.parameters["project_mfid"],
                "project_scope" to request.url.parameters["project_scope"],
                "limit" to request.url.parameters["limit"],
                "cursor" to request.url.parameters["cursor"],
                "offset" to request.url.parameters["offset"]
            )
            respond(
                content = """{"limit":6,"items":[{"unique_id":"resource-a","project_relation":"shared"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }.service

        val samples = assertIs<ApiResult.Success<List<crucible.lens.data.model.Sample>>>(
            service.searchSamples(
                q = "wafer",
                projectMfid = "project-mfid",
                projectScope = ProjectScope.Shared,
                limit = 6
            )
        )
        service.searchDatasets(
            q = "xrd",
            projectId = "project-slug",
            projectScope = ProjectScope.All,
            limit = 6
        )

        assertEquals(ProjectRelation.Shared, samples.data.single().projectRelation)
        assertEquals(
            listOf(
                mapOf(
                    "path" to "/api/samples/search",
                    "q" to "wafer",
                    "project_id" to null,
                    "project_mfid" to "project-mfid",
                    "project_scope" to "shared",
                    "limit" to "6",
                    "cursor" to null,
                    "offset" to null
                ),
                mapOf(
                    "path" to "/api/datasets/search",
                    "q" to "xrd",
                    "project_id" to "project-slug",
                    "project_mfid" to null,
                    "project_scope" to "all",
                    "limit" to "6",
                    "cursor" to null,
                    "offset" to null
                )
            ),
            requests
        )
    }
}
