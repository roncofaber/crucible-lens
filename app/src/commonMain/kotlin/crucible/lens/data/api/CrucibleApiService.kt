package crucible.lens.data.api

import crucible.lens.data.model.AccountResponse
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.MetadataSearchResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.ResourceType
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.model.UserLead
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

class CrucibleApiService(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String
) {
    private fun String.withAuth(): String = this

    private suspend inline fun <reified T> get(endpoint: String): T =
        client.get("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiKey")
        }.body()

    private suspend inline fun <reified T> post(
        endpoint: String,
        body: Any? = null
    ): T = client.post("$baseUrl$endpoint") {
        header("Authorization", "Bearer $apiKey")
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.body()

    private suspend inline fun <reified T> patch(
        endpoint: String,
        body: Any? = null
    ): T = client.patch("$baseUrl$endpoint") {
        header("Authorization", "Bearer $apiKey")
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.body()

    private suspend fun delete(endpoint: String): Boolean =
        client.delete("$baseUrl$endpoint") {
            header("Authorization", "Bearer $apiKey")
        }.status.value in 200..299

    // ── Read ─────────────────────────────────────────────────────────────────

    suspend fun getAccount(): ApiResult<AccountResponse> = safeCall {
        get("account")
    }

    suspend fun getResourceType(uuid: String): ApiResult<ResourceType> = safeCall {
        get("idtype/$uuid")
    }

    suspend fun getSample(
        uuid: String,
        includeLinks: Boolean = true
    ): ApiResult<Sample> = safeCall {
        client.get("${baseUrl}samples/$uuid") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("include_links", includeLinks.toString())
        }.body()
    }

    suspend fun getDataset(
        uuid: String,
        includeLinks: Boolean = true,
        includeMetadata: Boolean? = null
    ): ApiResult<Dataset> = safeCall {
        client.get("${baseUrl}datasets/$uuid") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("include_links", includeLinks.toString())
            if (includeMetadata != null) {
                url.parameters.append("include_metadata", includeMetadata.toString())
            }
        }.body()
    }

    suspend fun getDatasetScientificMetadata(uuid: String): ApiResult<JsonObject> = safeCall {
        // API returns { id, unique_id, scientific_metadata: {...} } — extract the inner field
        val wrapper: JsonObject = get("datasets/$uuid/scientific_metadata")
        wrapper["scientific_metadata"] as? JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
    }

    suspend fun getThumbnails(uuid: String): ApiResult<List<Thumbnail>> = safeCall {
        get("datasets/$uuid/thumbnails")
    }

    suspend fun getInstruments(): ApiResult<List<Instrument>> = safeCall {
        get("instruments")
    }

    suspend fun getInstrument(id: String): ApiResult<Instrument> = safeCall {
        get("instruments/$id")
    }

    suspend fun getDatasetsByInstrument(
        instrumentName: String,
        limit: Int = 100000
    ): ApiResult<List<Dataset>> = safeCall {
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("instrument_name", instrumentName)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getProjects(): ApiResult<List<Project>> = safeCall {
        get("projects")
    }

    suspend fun getSamplesByProject(
        projectId: String,
        limit: Int = 100000
    ): ApiResult<List<Sample>> = safeCall {
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getDatasetsByProject(
        projectId: String,
        limit: Int = 100000
    ): ApiResult<List<Dataset>> = safeCall {
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getFilteredDatasets(
        projectId: String? = null,
        measurement: String? = null,
        instrumentName: String? = null,
        dataFormat: String? = null,
        sessionName: String? = null,
        ownerOrcid: String? = null,
        creationTimeGte: String? = null,
        creationTimeLte: String? = null,
        limit: Int = 100000
    ): ApiResult<List<Dataset>> = safeCall {
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            if (projectId != null) url.parameters.append("project_id", projectId)
            if (measurement != null) url.parameters.append("measurement", measurement)
            if (instrumentName != null) url.parameters.append("instrument_name", instrumentName)
            if (dataFormat != null) url.parameters.append("data_format", dataFormat)
            if (sessionName != null) url.parameters.append("session_name", sessionName)
            if (ownerOrcid != null) url.parameters.append("owner_orcid", ownerOrcid)
            if (creationTimeGte != null) url.parameters.append("creation_time_gte", creationTimeGte)
            if (creationTimeLte != null) url.parameters.append("creation_time_lte", creationTimeLte)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    suspend fun getFilteredSamples(
        projectId: String? = null,
        sampleType: String? = null,
        ownerOrcid: String? = null,
        creationTimeGte: String? = null,
        creationTimeLte: String? = null,
        limit: Int = 100000
    ): ApiResult<List<Sample>> = safeCall {
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            if (projectId != null) url.parameters.append("project_id", projectId)
            if (sampleType != null) url.parameters.append("sample_type", sampleType)
            if (ownerOrcid != null) url.parameters.append("owner_orcid", ownerOrcid)
            if (creationTimeGte != null) url.parameters.append("creation_time_gte", creationTimeGte)
            if (creationTimeLte != null) url.parameters.append("creation_time_lte", creationTimeLte)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    // ── Write ────────────────────────────────────────────────────────────────

    suspend fun createSample(request: SampleCreateRequest): ApiResult<Sample> = safeCall {
        post("samples", request)
    }

    suspend fun createDataset(request: DatasetCreateRequest): ApiResult<Dataset> = safeCall {
        post("datasets", request)
    }

    suspend fun addThumbnail(
        uuid: String,
        request: ThumbnailCreateRequest
    ): ApiResult<Thumbnail> = safeCall {
        post("datasets/$uuid/thumbnails", request)
    }

    suspend fun requestDeletion(
        resourceId: String,
        reason: String? = null
    ): ApiResult<Unit> = safeCall {
        client.post("${baseUrl}deletion_requests") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("resource_id", resourceId)
            if (reason != null) url.parameters.append("reason", reason)
        }
    }

    suspend fun getProjectUsers(projectId: String): ApiResult<List<UserLead>> = safeCall {
        get("projects/$projectId/users")
    }

    suspend fun searchScientificMetadata(
        query: String,
        limit: Int = 50
    ): ApiResult<List<MetadataSearchResult>> = safeCall {
        client.get("${baseUrl}scientific_metadata/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", query)
            url.parameters.append("limit", limit.toString())
        }.body()
    }

    // ── Linking ──────────────────────────────────────────────────────────────

    suspend fun linkSamples(
        parentUuid: String,
        childUuid: String
    ): ApiResult<Unit> = safeCall {
        post<Unit>("samples/$parentUuid/children/$childUuid")
    }

    suspend fun linkDatasets(
        parentUuid: String,
        childUuid: String
    ): ApiResult<Unit> = safeCall {
        post<Unit>("datasets/$parentUuid/children/$childUuid")
    }

    suspend fun linkDatasetSample(
        datasetUuid: String,
        sampleUuid: String
    ): ApiResult<Unit> = safeCall {
        post<Unit>("datasets/$datasetUuid/samples/$sampleUuid")
    }

    suspend fun updateSample(
        uuid: String,
        request: SampleUpdateRequest
    ): ApiResult<Sample> = safeCall {
        patch("samples/$uuid", request)
    }

    suspend fun updateDataset(
        uuid: String,
        request: DatasetUpdateRequest
    ): ApiResult<Dataset> = safeCall {
        patch("datasets/$uuid", request)
    }

    // ── Unlinking ─────────────────────────────────────────────────────────────

    suspend fun unlinkSamples(
        parentUuid: String,
        childUuid: String
    ): ApiResult<Unit> = safeCall {
        delete("samples/$parentUuid/children/$childUuid")
        Unit
    }

    suspend fun unlinkDatasets(
        parentUuid: String,
        childUuid: String
    ): ApiResult<Unit> = safeCall {
        delete("datasets/$parentUuid/children/$childUuid")
        Unit
    }

    suspend fun unlinkDatasetSample(
        datasetUuid: String,
        sampleUuid: String
    ): ApiResult<Unit> = safeCall {
        delete("datasets/$datasetUuid/samples/$sampleUuid")
        Unit
    }
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}

suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: Exception) {
    ApiResult.Error(-1, e.message ?: "Unknown error")
}
