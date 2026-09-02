package crucible.lens.data.api

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
import kotlin.test.assertTrue

class CrucibleApiServiceInstrumentLookupTest {
    @Test
    fun exactInstrumentSlugLookupReportsNoMatch() = runTest {
        val result = client("""{"total":0,"limit":2,"offset":0,"items":[]}""")
            .service.getInstrument("missing-instrument")

        assertEquals(404, assertIs<ApiResult.Error>(result).code)
    }

    @Test
    fun exactInstrumentSlugLookupRejectsMultipleMatches() = runTest {
        val result = client(
            """{"total":2,"limit":2,"offset":0,"items":[{"unique_id":"instrument-a","instrument_id":"duplicate"},{"unique_id":"instrument-b","instrument_id":"duplicate"}]}"""
        ).service.getInstrument("duplicate")

        assertEquals(500, assertIs<ApiResult.Error>(result).code)
    }

    @Test
    fun exactInstrumentSlugLookupPreservesCapabilities() = runTest {
        val result = client(
            """{"total":1,"limit":2,"offset":0,"items":[{"unique_id":"instrument-a","instrument_id":"instrument-a","capabilities":{"can_edit":true,"can_manage_access":false,"can_change_status":false,"can_transfer":false,"max_grant_role":null}}]}"""
        ).service.getInstrument("instrument-a")

        assertTrue(assertIs<ApiResult.Success<crucible.lens.data.model.Instrument>>(result).data.capabilities?.canEdit == true)
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
