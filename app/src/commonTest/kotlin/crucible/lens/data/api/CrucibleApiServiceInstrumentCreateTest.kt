package crucible.lens.data.api

import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentCreateRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CrucibleApiServiceInstrumentCreateTest {
    @Test
    fun createInstrumentUsesV3RouteAndResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/instruments", request.url.encodedPath)
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"unique_id":"instrument-mfid","instrument_id":"xrd-1","instrument_name":"XRD 1","location":"Building 1","status":"active"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        val result = apiClient.service.createInstrument(
            InstrumentCreateRequest("xrd-1", "XRD 1", "Building 1")
        )

        assertEquals("instrument-mfid", assertIs<ApiResult.Success<Instrument>>(result).data.uniqueId)
    }

    @Test
    fun createInstrumentPayloadUsesRequiredV3FieldsAndOmitsOwner() {
        val payload = Json.encodeToString(
            InstrumentCreateRequest(
                instrumentId = "xrd-1",
                instrumentName = "XRD 1",
                location = "Building 1",
                manufacturer = "Acme"
            )
        )

        assertEquals(
            """{"instrument_id":"xrd-1","instrument_name":"XRD 1","location":"Building 1","manufacturer":"Acme"}""",
            payload
        )
        assertFalse("owner" in payload)
        assertFalse("owner_orcid" in payload)
    }
}
