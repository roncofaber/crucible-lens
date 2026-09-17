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

class CrucibleApiServiceInstrumentDatasetsTest {
    @Test
    fun filteredDatasetsAcceptCanonicalInstrumentMfid() = runTest {
        var instrumentMfid: String? = null
        val engine = MockEngine { request ->
            instrumentMfid = request.url.parameters["instrument_mfid"]
            respond(
                content = """{"total":0,"limit":1000,"next_cursor":null,"items":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        assertIs<ApiResult.Success<List<crucible.lens.data.model.Dataset>>>(
            client.service.getFilteredDatasets(DatasetCollectionQuery(instrumentMfid = "instrument-mfid"))
        )

        assertEquals("instrument-mfid", instrumentMfid)
    }

    @Test
    fun instrumentDatasetPageUsesCanonicalMfidAndBoundedCursorPagination() = runTest {
        val requests = mutableListOf<Map<String, String>>()
        var requestCount = 0
        val engine = MockEngine { request ->
            requests += request.url.parameters.entries().associate { it.key to it.value.single() }
            requestCount += 1
            val body = if (requestCount == 1) {
                """{"total":2,"limit":100,"next_cursor":"dataset-1","items":[{"unique_id":"dataset-2"}]}"""
            } else {
                """{"limit":100,"next_cursor":null,"items":[{"unique_id":"dataset-1"}]}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        val first = assertIs<ApiResult.Success<crucible.lens.data.model.PaginatedResponse<crucible.lens.data.model.Dataset>>>(
            client.service.getInstrumentDatasetsPage("instrument-mfid", 100)
        )
        val second = assertIs<ApiResult.Success<crucible.lens.data.model.PaginatedResponse<crucible.lens.data.model.Dataset>>>(
            client.service.getInstrumentDatasetsPage("instrument-mfid", 100, first.data.nextCursor)
        )

        assertEquals(listOf("dataset-2"), first.data.items.map { it.uniqueId })
        assertEquals(listOf("dataset-1"), second.data.items.map { it.uniqueId })
        assertEquals("instrument-mfid", requests[0]["instrument_mfid"])
        assertEquals("true", requests[0]["include_owner"])
        assertEquals("100", requests[0]["limit"])
        assertEquals(null, requests[0]["cursor"])
        assertEquals("dataset-1", requests[1]["cursor"])
    }
}
