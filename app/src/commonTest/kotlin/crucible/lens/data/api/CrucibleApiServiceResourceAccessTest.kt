package crucible.lens.data.api

import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessGrantWrite
import crucible.lens.data.model.AccessPermission
import crucible.lens.data.model.AccessPrincipalKind
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.model.Sample
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrucibleApiServiceResourceAccessTest {
    @Test
    fun sampleAndDatasetDetailsDecodeCapabilities() {
        val sample = Json.decodeFromString<Sample>(
            """{"unique_id":"sample-a","capabilities":{"can_manage_access":true,"max_grant_role":"editor"}}"""
        )
        val dataset = Json.decodeFromString<Dataset>(
            """{"unique_id":"dataset-a","capabilities":{"can_manage_access":false,"can_change_status":false}}"""
        )

        val sampleCapabilities = requireNotNull(sample.capabilities)
        val datasetCapabilities = requireNotNull(dataset.capabilities)
        assertTrue(sampleCapabilities.canManageAccess)
        assertEquals(ResourceGrantRole.Editor, sampleCapabilities.maxGrantRole)
        assertFalse(datasetCapabilities.canManageAccess)
        assertFalse(datasetCapabilities.canChangeStatus)
    }

    @Test
    fun accessGrantContractDecodesCanonicalFields() {
        val grant = Json.decodeFromString<AccessGrant>(
            """{"principal_id":"project-a","principal_type":"project","permission":"contributor","slug":"beamline","display_name":"Beamline Project"}"""
        )

        assertEquals(AccessPrincipalType.Project, grant.principalType)
        assertEquals(AccessPermission.Contributor, grant.permission)
        assertEquals("beamline", grant.slug)
        assertEquals("Beamline Project", grant.displayName)
        assertEquals("{\"permission\":\"admin\"}", Json.encodeToString(AccessGrantWrite(ResourceGrantRole.Admin)))
    }

    @Test
    fun resourceAccessUsesCanonicalRoutes() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            val content = when {
                request.method == HttpMethod.Get ->
                    """[{"principal_id":"owner-a","principal_type":"user","permission":"owner","display_name":"Owner"}]"""
                request.method == HttpMethod.Delete -> ""
                request.url.encodedPath.endsWith("/public") ->
                    """{"principal_id":"public","principal_type":"public","permission":"viewer","display_name":"Public"}"""
                else ->
                    """{"principal_id":"user-a","principal_type":"user","permission":"editor","display_name":"Alice"}"""
            }
            respond(
                content = content,
                status = if (request.method == HttpMethod.Delete) HttpStatusCode.NoContent else HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = client(engine).service

        assertIs<ApiResult.Success<List<AccessGrant>>>(service.getResourceAccess("resource-a"))
        assertIs<ApiResult.Success<AccessGrant>>(
            service.setResourceAccess("resource-a", AccessPrincipalKind.Users, "user-a", ResourceGrantRole.Editor)
        )
        assertIs<ApiResult.Success<Boolean>>(
            service.revokeResourceAccess("resource-a", AccessPrincipalKind.Projects, "project-a")
        )
        assertIs<ApiResult.Success<AccessGrant>>(service.publishResource("resource-a"))
        assertIs<ApiResult.Success<Boolean>>(service.unpublishResource("resource-a"))

        assertEquals(
            listOf(
                HttpMethod.Get to "/api/resources/resource-a/access",
                HttpMethod.Put to "/api/resources/resource-a/access/users/user-a",
                HttpMethod.Delete to "/api/resources/resource-a/access/projects/project-a",
                HttpMethod.Put to "/api/resources/resource-a/access/public",
                HttpMethod.Delete to "/api/resources/resource-a/access/public"
            ),
            requests
        )
    }

    private fun client(engine: MockEngine): ApiClient = ApiClient.withEngine(engine).apply {
        setApiKey("test-key")
        setBaseUrl("https://example.test/api/")
    }
}
