package crucible.lens.data.api

import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetFacetField
import crucible.lens.data.model.DatasetInstrumentAssignRequest
import crucible.lens.data.model.DatasetInstrumentAssignment
import crucible.lens.data.model.FacetBucket
import crucible.lens.data.model.HealthStatus
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.PlatformRole
import crucible.lens.data.model.ServiceAccountRoleUpdateRequest
import crucible.lens.data.model.ThumbnailUpdateRequest
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

        val ownerQuery = ResourceCollectionQuery(ownerId = "owner-mfid")
        service.getFilteredDatasets(DatasetCollectionQuery(resource = ownerQuery))
        service.getFilteredSamples(SampleCollectionQuery(resource = ownerQuery))

        parameters.forEach {
            assertEquals("owner-mfid", it["owner_id"])
            assertFalse("owner_orcid" in it)
        }
    }

    @Test
    fun resourceFiltersSendVisibilityAffiliationAndNullPredicates() = runTest {
        val parameters = mutableListOf<Map<String, String>>()
        val engine = MockEngine { request ->
            parameters += request.url.parameters.entries().associate { it.key to it.value.single() }
            respond("""{"limit":1000,"next_cursor":null,"items":[]}""", HttpStatusCode.OK, jsonHeaders())
        }
        val service = apiClient(engine).service

        service.getFilteredSamples(SampleCollectionQuery(
            resource = ResourceCollectionQuery(
                visibility = ResourceVisibility.Private,
                affiliation = ResourceAffiliation.Owner,
                projectMfidIsNull = true
            ),
            sampleTypeIsNull = true
        ))
        service.getFilteredDatasets(DatasetCollectionQuery(
            resource = ResourceCollectionQuery(
                visibility = ResourceVisibility.Public,
                affiliation = ResourceAffiliation.Owner,
                projectMfidIsNull = true
            ),
            measurementIsNull = true,
            instrumentMfidIsNull = true,
            dataFormatIsNull = true,
            sessionNameIsNull = true
        ))

        assertEquals("private", parameters[0]["visibility"])
        assertEquals("owner", parameters[0]["affiliation"])
        assertEquals("true", parameters[0]["sample_type_is_null"])
        assertEquals("public", parameters[1]["visibility"])
        listOf("measurement_is_null", "instrument_mfid_is_null", "data_format_is_null", "session_name_is_null", "project_mfid_is_null").forEach {
            assertEquals("true", parameters[1][it])
        }
    }

    @Test
    fun siblingPagesUseAnchoredExplicitOrdering() = runTest {
        val parameters = mutableListOf<Map<String, String>>()
        val engine = MockEngine { request ->
            parameters += request.url.parameters.entries().associate { it.key to it.value.single() }
            respond("""{"limit":40,"next_cursor":null,"items":[]}""", HttpStatusCode.OK, jsonHeaders())
        }
        val service = apiClient(engine).service

        service.getSampleSiblingPage(SampleSiblingQuery("sample-mfid", "project-id", PageDirection.Ascending, sampleType = "powder"))
        service.getDatasetSiblingPage(DatasetSiblingQuery("dataset-mfid", "project-id", PageDirection.Descending, measurement = "XAS"))

        assertEquals("sample-mfid", parameters[0]["anchor_mfid"])
        assertEquals("name", parameters[0]["sort"])
        assertEquals("asc", parameters[0]["direction"])
        assertEquals("powder", parameters[0]["sample_type"])
        assertEquals("dataset-mfid", parameters[1]["anchor_mfid"])
        assertEquals("desc", parameters[1]["direction"])
        assertEquals("XAS", parameters[1]["measurement"])
    }

    @Test
    fun serviceAccountAdministrationUsesDedicatedRoutes() = runTest {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val engine = MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val content = when {
                request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/service_accounts") ->
                    """{"total":1,"limit":1000,"offset":0,"items":[{"unique_id":"service-mfid","username":"robot","first_name":"","last_name":"","platform_role":"none"}]}"""
                request.method == HttpMethod.Get ->
                    """{"unique_id":"service-mfid","username":"robot","first_name":"","last_name":"","platform_role":"none","api_key_status":{"valid":true,"created_at":"2026-01-01T00:00:00Z","expires_at":"2027-01-01T00:00:00Z"}}"""
                request.method == HttpMethod.Patch ->
                    """{"unique_id":"service-mfid","username":"robot","first_name":"","last_name":"","platform_role":"contributor"}"""
                else -> """{"unique_id":"service-mfid","username":"robot","api_key":"secret"}"""
            }
            respond(content, HttpStatusCode.OK, jsonHeaders())
        }
        val service = apiClient(engine).service

        service.getServiceAccounts()
        service.getServiceAccountDetail("service-mfid")
        service.createServiceAccount("robot")
        service.updateServiceAccountRole("service-mfid", ServiceAccountRoleUpdateRequest(PlatformRole.Contributor))
        service.rotateServiceAccountKey("service-mfid")

        assertEquals(
            listOf(
                HttpMethod.Get to "/api/service_accounts",
                HttpMethod.Get to "/api/service_accounts/service-mfid",
                HttpMethod.Post to "/api/service_accounts",
                HttpMethod.Patch to "/api/service_accounts/service-mfid",
                HttpMethod.Post to "/api/service_accounts/service-mfid/rotate_key"
            ),
            requests
        )
    }

    @Test
    fun serviceAccountSearchAndThumbnailUpdateUseNewContract() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            val content = if (request.url.encodedPath.endsWith("/users/search")) "[]" else
                """{"id":4,"thumbnail_b64str":"updated","thumbnail_name":"Plot"}"""
            respond(content, HttpStatusCode.OK, jsonHeaders())
        }
        val service = apiClient(engine).service

        service.searchUsers("robot", isServiceAccount = true)
        service.updateThumbnail("dataset-mfid", 4, ThumbnailUpdateRequest(thumbnailName = "Plot"))

        assertEquals("true", requests[0].url.parameters["is_service_account"])
        assertEquals(HttpMethod.Patch, requests[1].method)
        assertEquals("/api/datasets/dataset-mfid/thumbnails/4", requests[1].url.encodedPath)
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
