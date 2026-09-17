package crucible.lens.data.api

import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.model.InstrumentUpdateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.ProjectRelation
import crucible.lens.data.model.ProjectUpdateRequest
import crucible.lens.data.model.ReassignProjectResponse
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.TransferOwnershipRequest
import crucible.lens.data.model.TransferOwnershipResponse
import crucible.lens.data.model.resolvedInstrumentId
import crucible.lens.data.model.resolvedInstrumentName
import crucible.lens.data.model.resolvedInstrumentReference
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.model.resolvedProjectName
import crucible.lens.data.model.resolvedProjectReference
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
import kotlin.test.assertNull

class CrucibleApiServiceV3WriteTest {
    @Test
    fun updatePayloadsOmitFieldsMovedToDedicatedOperations() {
        val sampleJson = Json.encodeToString(SampleUpdateRequest(sampleName = "Updated"))
        val datasetJson = Json.encodeToString(DatasetUpdateRequest(datasetName = "Updated"))
        val projectJson = Json.encodeToString(ProjectUpdateRequest(title = "Updated"))
        val projectRenameJson = Json.encodeToString(ProjectUpdateRequest(projectId = "renamed-project"))
        val instrumentJson = Json.encodeToString(InstrumentUpdateRequest(instrumentId = "renamed-instrument"))

        assertFalse("project_id" in sampleJson)
        assertFalse("project_id" in datasetJson)
        assertFalse("instrument_name" in datasetJson)
        assertFalse("instrument_id" in datasetJson)
        assertFalse("project_lead" in projectJson)
        assertEquals("{\"project_id\":\"renamed-project\"}", projectRenameJson)
        assertFalse("owner" in instrumentJson)
        assertEquals("{\"instrument_id\":\"renamed-instrument\"}", instrumentJson)
    }

    @Test
    fun datasetModelOmitsRemovedSourceFolderField() {
        val datasetJson = Json.encodeToString(Dataset(uniqueId = "dataset-a", size = 1024))

        assertFalse("source_folder" in datasetJson)
    }

    @Test
    fun v3InstrumentReferencesDeserializeFromDatasetsAndInstruments() {
        val dataset = Json.decodeFromString<Dataset>(
            """{"unique_id":"dataset-a","instrument_id":"legacy-instrument","instrument_name":"Legacy instrument","project_id":"legacy-project","instrument":{"unique_id":"instrument-mfid","instrument_id":"instrument-a","instrument_name":"Current instrument"},"project":{"unique_id":"project-mfid","project_id":"project-a","title":"Current project"}}"""
        )
        val instrument = Json.decodeFromString<Instrument>(
            """{"unique_id":"instrument-mfid","instrument_id":"instrument-a","instrument_name":"Instrument","owner_orcid":"owner-a","owner":{"unique_id":"owner-a","username":"alice"},"status":"maintenance"}"""
        )

        assertEquals("legacy-instrument", dataset.instrumentId)
        assertEquals("Current instrument", dataset.resolvedInstrumentName)
        assertEquals("instrument-mfid", dataset.resolvedInstrumentReference)
        assertEquals("instrument-a", dataset.resolvedInstrumentId)
        assertEquals("Current project", dataset.resolvedProjectName)
        assertEquals("project-mfid", dataset.resolvedProjectReference)
        assertEquals("project-a", dataset.resolvedProjectId)
        assertEquals("instrument-a", instrument.instrumentId)
        assertEquals("owner-a", instrument.ownerOrcid)
        assertEquals("alice", instrument.owner?.username)
        assertEquals("maintenance", instrument.status)
    }

    @Test
    fun datasetReferenceAccessorsFallBackToLegacyFields() {
        val dataset = Json.decodeFromString<Dataset>(
            """{"unique_id":"dataset-a","instrument_id":"instrument-a","instrument_name":"Instrument","project_id":"project-a"}"""
        )

        assertEquals("Instrument", dataset.resolvedInstrumentName)
        assertEquals("instrument-a", dataset.resolvedInstrumentReference)
        assertEquals("instrument-a", dataset.resolvedInstrumentId)
        assertEquals("project-a", dataset.resolvedProjectName)
        assertEquals("project-a", dataset.resolvedProjectReference)
        assertEquals("project-a", dataset.resolvedProjectId)
    }

    @Test
    fun sampleProjectReferenceAndScopedRelationDeserialize() {
        val sample = Json.decodeFromString<crucible.lens.data.model.Sample>(
            """{"unique_id":"sample-a","project_id":"legacy-project","project":{"unique_id":"project-mfid","project_id":"project-a","title":"Current project"},"project_relation":"shared"}"""
        )

        assertEquals("Current project", sample.resolvedProjectName)
        assertEquals("project-mfid", sample.resolvedProjectReference)
        assertEquals("project-a", sample.resolvedProjectId)
        assertEquals(ProjectRelation.Shared, sample.projectRelation)
    }

    @Test
    fun resourcesWithoutCalculatedCapabilitiesRemainUsable() {
        val project = Json.decodeFromString<Project>(
            """{"unique_id":"project-a","project_id":"project-a"}"""
        )
        val instrument = Json.decodeFromString<Instrument>(
            """{"unique_id":"instrument-a","instrument_id":"instrument-a","capabilities":null}"""
        )

        assertNull(project.capabilities)
        assertNull(instrument.capabilities)
    }

    @Test
    fun projectMembershipWritesUseCanonicalV3Contract() = runTest {
        val requests = mutableListOf<Triple<HttpMethod, String, String?>>()
        val engine = MockEngine { request ->
            requests += Triple(request.method, request.url.encodedPath, request.url.parameters["role"])
            respond(
                content = """[{"unique_id":"user-a","username":"alice","role":"editor"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        apiClient.service.addProjectMember("project-mfid", "user-a", "editor")
        apiClient.service.updateProjectMemberRole("project-mfid", "user-a", "viewer")
        apiClient.service.removeProjectMember("project-mfid", "user-a")

        assertEquals(
            listOf(
                Triple(HttpMethod.Post, "/api/projects/project-mfid/users/user-a", "editor"),
                Triple(HttpMethod.Patch, "/api/projects/project-mfid/users/user-a", "viewer"),
                Triple(HttpMethod.Delete, "/api/projects/project-mfid/users/user-a", null)
            ),
            requests
        )
    }

    @Test
    fun instrumentLifecycleUsesDedicatedStatusRoute() = runTest {
        val requests = mutableListOf<Triple<HttpMethod, String, String?>>()
        val engine = MockEngine { request ->
            requests += Triple(request.method, request.url.encodedPath, request.url.parameters["status"])
            respond(
                content = """{"unique_id":"instrument-a","instrument_id":"instrument-a","status":"maintenance"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        val result = apiClient.service.updateInstrumentStatus("instrument-a", InstrumentStatus.Maintenance)

        assertEquals(
            listOf<Triple<HttpMethod, String, String?>>(
                Triple(HttpMethod.Post, "/api/instruments/instrument-a/status", "maintenance")
            ),
            requests
        )
        assertEquals("maintenance", assertIs<ApiResult.Success<Instrument>>(result).data.status)
    }

    @Test
    fun projectMoveUsesPreviewAndConfirmedV3Requests() = runTest {
        val confirmations = mutableListOf<String?>()
        val engine = MockEngine { request ->
            assertEquals("/api/resources/resource-a/project", request.url.encodedPath)
            confirmations += request.url.parameters["confirm"]
            respond(
                content = """{"resource_id":"resource-a","previous_project_id":"project-a","new_project_id":"project-b"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        val preview = apiClient.service.reassignResourceProject("resource-a", "project-b")
        val confirmed = apiClient.service.reassignResourceProject("resource-a", "project-b", confirm = true)

        assertEquals(listOf(null, "true"), confirmations)
        assertEquals("project-a", assertIs<ApiResult.Success<ReassignProjectResponse>>(preview).data.previousProjectId)
        assertEquals("project-b", assertIs<ApiResult.Success<ReassignProjectResponse>>(confirmed).data.newProjectId)
    }

    @Test
    fun ownershipTransferUsesPreviewAndConfirmedV3Requests() = runTest {
        val confirmations = mutableListOf<String?>()
        val engine = MockEngine { request ->
            assertEquals("/api/resources/project-mfid/transfer_ownership", request.url.encodedPath)
            confirmations += request.url.parameters["confirm"]
            respond(
                content = """{"resource_id":"project-mfid","previous_owner":{"unique_id":"owner-a","username":"alice"},"new_owner":{"unique_id":"owner-b","username":"bob"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val apiClient = ApiClient.withEngine(engine).apply {
            setApiKey("test-key")
            setBaseUrl("https://example.test/api/")
        }

        val requestJson = Json.encodeToString(TransferOwnershipRequest("owner-b"))
        val preview = apiClient.service.transferResourceOwnership("project-mfid", "owner-b")
        val confirmed = apiClient.service.transferResourceOwnership("project-mfid", "owner-b", confirm = true)

        assertEquals("{\"new_owner\":\"owner-b\"}", requestJson)
        assertEquals(listOf(null, "true"), confirmations)
        assertEquals("owner-a", assertIs<ApiResult.Success<TransferOwnershipResponse>>(preview).data.previousOwner?.uniqueId)
        assertEquals("owner-b", assertIs<ApiResult.Success<TransferOwnershipResponse>>(confirmed).data.newOwner.uniqueId)
    }
}
