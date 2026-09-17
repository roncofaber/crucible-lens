package crucible.lens.data.api

import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetFacetField
import crucible.lens.data.model.DatasetInstrumentAssignRequest
import crucible.lens.data.model.DatasetInstrumentAssignment
import crucible.lens.data.model.FacetBucket
import crucible.lens.data.model.HealthStatus
import crucible.lens.data.model.SampleCreateRequest
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CrucibleApiServiceCurrentContractTest {
    @Test
    fun ownerFiltersUseStableOwnerId() = runTest {
        val parameters = mutableListOf<Map<String, String>>()
        val engine = MockEngine { request ->
            parameters += request.url.parameters.entries().associate { it.key to it.value.single() }
            respond(
                content = """{"limit":1000,"next_cursor":null,"items":[]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val service = apiClient(engine).service

        service.getFilteredDatasets(ownerId = "owner-mfid")
        service.getFilteredSamples(ownerId = "owner-mfid")

        parameters.forEach {
            assertEquals("owner-mfid", it["owner_id"])
            assertFalse("owner_orcid" in it)
        }
    }

    @Test
    fun facetValuesFollowOpaqueCursor() = runTest {
        val cursors = mutableListOf<String?>()
        val engine = MockEngine { request ->
            assertEquals("/api/datasets/facets", request.url.encodedPath)
            assertEquals("measurement", request.url.parameters["field"])
            assertEquals("label", request.url.parameters["sort"])
            assertEquals("asc", request.url.parameters["direction"])
            cursors += request.url.parameters["cursor"]
            val content = if (cursors.size == 1) {
                """{"field":"measurement","limit":1000,"next_cursor":"next-page","items":[{"value":"XAS","label":"XAS","count":4}]}"""
            } else {
                """{"field":"measurement","limit":1000,"next_cursor":null,"items":[{"value":"XRD","label":"XRD","count":2}]}"""
            }
            respond(content, HttpStatusCode.OK, jsonHeaders())
        }

        val result = apiClient(engine).service.getDatasetFacetValues(DatasetFacetField.Measurement)

        assertEquals(listOf(null, "next-page"), cursors)
        assertEquals(
            listOf("XAS", "XRD"),
            assertIs<ApiResult.Success<List<FacetBucket>>>(result).data.map { it.value }
        )
    }

    @Test
    fun datasetInstrumentAssignmentUsesDedicatedRoute() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/datasets/dataset-mfid/instrument", request.url.encodedPath)
            respond(
                content = """{"dataset_mfid":"dataset-mfid","instrument":{"unique_id":"instrument-mfid","instrument_id":"xrd","instrument_name":"XRD"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val result = apiClient(engine).service.assignDatasetInstrument("dataset-mfid", "instrument-mfid")

        assertEquals(
            "{\"instrument_mfid\":\"instrument-mfid\"}",
            Json.encodeToString(DatasetInstrumentAssignRequest("instrument-mfid"))
        )
        assertEquals(
            "instrument-mfid",
            assertIs<ApiResult.Success<DatasetInstrumentAssignment>>(result).data.instrument.uniqueId
        )
    }

    @Test
    fun createPayloadsPreferCanonicalProjectAndInstrumentIds() {
        val sample = Json.encodeToString(
            SampleCreateRequest(sampleName = "Sample", projectMfid = "project-mfid")
        )
        val dataset = Json.encodeToString(
            DatasetCreateRequest(
                datasetName = "Dataset",
                projectMfid = "project-mfid",
                instrumentMfid = "instrument-mfid"
            )
        )

        assertEquals("project-mfid", Json.parseToJsonElement(sample).jsonObject["project_mfid"]?.jsonPrimitive?.content)
        assertEquals("project-mfid", Json.parseToJsonElement(dataset).jsonObject["project_mfid"]?.jsonPrimitive?.content)
        assertEquals("instrument-mfid", Json.parseToJsonElement(dataset).jsonObject["instrument_mfid"]?.jsonPrimitive?.content)
        assertFalse("owner_orcid" in sample)
        assertFalse("owner_orcid" in dataset)
    }

    @Test
    fun degradedReadinessResponseStillProvidesDiagnostics() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":"degraded","build":{"api_version":"3.0.0","git_commit":"abc123","branch":"main"},"database":{"status":"error","latency_ms":12.5,"schema_revisions":["revision-a"]}}""",
                status = HttpStatusCode.ServiceUnavailable,
                headers = jsonHeaders()
            )
        }

        val result = apiClient(engine).service.checkHealth("https://example.test/api/")
        val health = assertIs<ApiResult.Success<HealthStatus>>(result).data

        assertEquals("degraded", health.status)
        assertEquals("3.0.0", health.build.apiVersion)
        assertEquals("error", health.database.status)
        assertEquals(12.5, health.database.latencyMs)
        assertEquals(listOf("revision-a"), health.database.schemaRevisions)
    }

    private fun apiClient(engine: MockEngine) = ApiClient.withEngine(engine).apply {
        setApiKey("test-key")
        setBaseUrl("https://example.test/api/")
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
