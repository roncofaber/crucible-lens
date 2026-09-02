package crucible.lens.data.api

import crucible.lens.data.model.User
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

class CrucibleApiServiceUserLookupTest {
    @Test
    fun exactUsernameLookupUsesCanonicalCollectionFilter() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/users", request.url.encodedPath)
            assertEquals("faber", request.url.parameters["username"])
            assertEquals("2", request.url.parameters["limit"])
            assertEquals("0", request.url.parameters["offset"])
            respond(
                content = """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"user-a","username":"faber"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val result = client(engine).service.getUserByUsername("faber")

        assertEquals("user-a", assertIs<ApiResult.Success<User>>(result).data.uniqueId)
    }

    @Test
    fun exactUsernameLookupReportsNoMatch() = runTest {
        val engine = responseEngine("""{"total":0,"limit":2,"offset":0,"items":[]}""")

        val result = client(engine).service.getUserByUsername("missing")

        assertEquals(404, assertIs<ApiResult.Error>(result).code)
    }

    @Test
    fun exactUsernameLookupRejectsMultipleMatches() = runTest {
        val engine = responseEngine(
            """{"total":2,"limit":2,"offset":0,"items":[{"unique_id":"user-a"},{"unique_id":"user-b"}]}"""
        )

        val result = client(engine).service.getUserByUsername("duplicate")

        assertEquals(500, assertIs<ApiResult.Error>(result).code)
    }

    private fun responseEngine(content: String) = MockEngine {
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }

    private fun client(engine: MockEngine) = ApiClient.withEngine(engine).apply {
        setApiKey("test-key")
        setBaseUrl("https://example.test/api/")
    }
}
