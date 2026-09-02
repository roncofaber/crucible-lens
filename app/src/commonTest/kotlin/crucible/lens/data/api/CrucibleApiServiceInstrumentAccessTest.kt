package crucible.lens.data.api

import crucible.lens.data.model.User
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrucibleApiServiceInstrumentAccessTest {
    @Test
    fun serviceAccountBindingUsesInstrumentScopedRoutes() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            respond(
                content = """[{"unique_id":"service-account-a","username":"operator","is_service_account":true,"role":"admin"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = client(engine)

        val listed = apiClient.service.getInstrumentServiceAccounts("instrument-a")
        val added = apiClient.service.addInstrumentServiceAccount("instrument-a", "service-account-a")
        val removed = apiClient.service.removeInstrumentServiceAccount("instrument-a", "service-account-a")

        assertEquals(
            listOf(
                HttpMethod.Get to "/api/instruments/instrument-a/service_accounts",
                HttpMethod.Post to "/api/instruments/instrument-a/service_accounts/service-account-a",
                HttpMethod.Delete to "/api/instruments/instrument-a/service_accounts/service-account-a"
            ),
            requests
        )
        assertEquals("operator", assertIs<ApiResult.Success<List<User>>>(listed).data.single().username)
        assertIs<ApiResult.Success<List<User>>>(added)
        assertIs<ApiResult.Success<List<User>>>(removed)
    }

    @Test
    fun exactLookupFiltersByServiceAccountType() = runTest {
        val queries = mutableListOf<Map<String, String>>()
        val engine = MockEngine { request ->
            queries += request.url.parameters.entries().associate { it.key to it.value.single() }
            respond(
                content = """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"01arz3ndektsv4rrffq69g5fav","username":"operator"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = client(engine)

        val byUsername = apiClient.service.getServiceAccount("operator")
        val byMfid = apiClient.service.getServiceAccount("01arz3ndektsv4rrffq69g5fav")

        assertEquals("operator", queries[0]["username"])
        assertEquals("01arz3ndektsv4rrffq69g5fav", queries[1]["unique_id"])
        assertTrue(queries.all { it["is_service_account"] == "true" })
        assertTrue(assertIs<ApiResult.Success<User>>(byUsername).data.isServiceAccount)
        assertTrue(assertIs<ApiResult.Success<User>>(byMfid).data.isServiceAccount)
    }

    private fun client(engine: MockEngine): ApiClient = ApiClient.withEngine(engine).apply {
        setApiKey("test-key")
        setBaseUrl("https://example.test/api/")
    }
}
