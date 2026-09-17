package crucible.lens.data.api

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import crucible.lens.data.model.InstrumentStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class CrucibleApiServiceOwnerExpansionTest {
    @Test
    fun sampleReadsSkipEmbeddedDatasetsAndRetainLinks() = runTest {
        val requests = mutableListOf<Triple<String, String?, String?>>()
        val engine = MockEngine { request ->
            requests += Triple(
                request.url.encodedPath,
                request.url.parameters["include_datasets"],
                request.url.parameters["include_links"]
            )
            respond(
                content = """{"unique_id":"sample-mfid","resource_type":"sample","datasets":null,"links":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }.service

        service.getSample("sample-mfid")
        service.getResource("sample-mfid")

        assertEquals(
            listOf<Triple<String, String?, String?>>(
                Triple("/api/samples/sample-mfid", "false", "true"),
                Triple("/api/resources/sample-mfid", "false", "true")
            ),
            requests
        )
    }

    @Test
    fun typedResourceReadsRequestExpandedOwners() = runTest {
        val mfid = "01k4abcdefghjkmnpqrstvwxyz"
        val requests = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to request.url.parameters["include_owner"]
            val content = if (request.url.encodedPath.endsWith("/$mfid")) {
                when {
                    request.url.encodedPath.contains("/samples/") -> """{"unique_id":"$mfid"}"""
                    request.url.encodedPath.contains("/datasets/") -> """{"unique_id":"$mfid"}"""
                    else -> """{"unique_id":"$mfid","instrument_name":"Instrument"}"""
                }
            } else {
                """{"total":0,"limit":1000,"offset":0,"items":[]}"""
            }
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }.service

        service.getSample(mfid)
        service.getDataset(mfid)
        service.getInstrument(mfid)
        service.getInstruments()
        service.getSamplesByProject("project-a")
        service.getDatasetsByProject("project-a")

        assertEquals(6, requests.size)
        requests.forEach { (_, includeOwner) -> assertEquals("true", includeOwner) }
    }

    @Test
    fun instrumentListAndSearchSendStatusFilters() = runTest {
        val statuses = mutableListOf<Pair<String, String?>>()
        val engine = MockEngine { request ->
            statuses += request.url.encodedPath to request.url.parameters["status"]
            respond(
                content = """{"total":0,"limit":1000,"offset":0,"items":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val service = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }.service

        service.getInstruments(InstrumentStatus.Maintenance)
        service.searchInstruments("beamline", status = InstrumentStatus.Active)

        assertEquals(
            listOf<Pair<String, String?>>(
                "/api/instruments" to "maintenance",
                "/api/instruments/search" to "active"
            ),
            statuses
        )
    }
}
