package crucible.lens.data.api

import crucible.lens.data.model.Project
import crucible.lens.data.model.ResourceGrantRole
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CrucibleApiServiceProjectLookupTest {
    @Test
    fun exactProjectSlugLookupReportsNoMatch() = runTest {
        val result = client("""{"total":0,"limit":2,"offset":0,"items":[]}""")
            .service.getProject("missing-project")

        assertEquals(404, assertIs<ApiResult.Error>(result).code)
    }

    @Test
    fun exactProjectSlugLookupRejectsMultipleMatches() = runTest {
        val result = client(
            """{"total":2,"limit":2,"offset":0,"items":[{"unique_id":"project-a","project_id":"duplicate"},{"unique_id":"project-b","project_id":"duplicate"}]}"""
        ).service.getProject("duplicate")

        assertEquals(500, assertIs<ApiResult.Error>(result).code)
    }

    @Test
    fun exactProjectSlugLookupPreservesCapabilities() = runTest {
        val result = client(
            """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"project-a","project_id":"project-a","capabilities":{"can_edit":true,"can_manage_access":true,"can_change_status":true,"can_transfer":false,"max_grant_role":"editor"}}]}"""
        ).service.getProject("project-a")

        val project = assertIs<ApiResult.Success<Project>>(result).data
        val capabilities = assertNotNull(project.capabilities)
        assertTrue(capabilities.canEdit)
        assertEquals(ResourceGrantRole.Editor, capabilities.maxGrantRole)
    }

    private fun client(content: String): ApiClient {
        val engine = MockEngine {
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }
    }
}
