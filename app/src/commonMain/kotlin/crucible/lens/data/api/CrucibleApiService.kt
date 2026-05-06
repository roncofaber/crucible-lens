package crucible.lens.data.api

import crucible.lens.data.model.AccountResponse
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.MetadataSearchResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.AssociatedFileRequest
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.model.ExtractMetadataRequest
import crucible.lens.data.model.AnthropicContentBlock
import crucible.lens.data.model.AnthropicImageSource
import crucible.lens.data.model.AnthropicMessage
import crucible.lens.data.model.AnthropicMessagesRequest
import crucible.lens.data.model.AnthropicMessagesResponse
import crucible.lens.data.model.MetadataImageData
import crucible.lens.data.model.HealthStatus
import crucible.lens.data.model.PaginatedResponse
import crucible.lens.data.model.UserLead
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.ceil
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class CrucibleApiService(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
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

    /**
     * Fetches any resource by UUID in a single call — the API resolves the type
     * and returns the full record with links and metadata included.
     * Replaces the previous two-step /idtype + typed fetch.
     */
    suspend fun getResource(
        uuid: String,
        includeLinks: Boolean = true,
        includeMetadata: Boolean = true
    ): ApiResult<CrucibleResource> = safeCall {
        val obj: JsonObject = client.get("${baseUrl}resources/$uuid") {
            header("Authorization", "Bearer $apiKey")
            if (includeLinks) url.parameters.append("include_links", "true")
            if (includeMetadata) url.parameters.append("include_metadata", "true")
        }.body()
        val type = obj["resource_type"]?.jsonPrimitive?.content?.lowercase()
        when (type) {
            "sample"     -> json.decodeFromJsonElement<Sample>(obj) as CrucibleResource
            "dataset"    -> json.decodeFromJsonElement<Dataset>(obj) as CrucibleResource
            else         -> throw IllegalStateException("Unknown resource_type: $type")
        }
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

    suspend fun getResourceMetadata(uuid: String): ApiResult<JsonObject> = safeCall {
        // Returns { unique_id, scientific_metadata: {...} } — extract the inner field
        val wrapper: JsonObject = get("resources/$uuid/metadata")
        wrapper["scientific_metadata"] as? JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
    }

    suspend fun getThumbnails(uuid: String): ApiResult<List<Thumbnail>> = safeCall {
        get("datasets/$uuid/thumbnails")
    }

    suspend fun getInstruments(): ApiResult<List<Instrument>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}instruments") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Instrument>>()
    }

    suspend fun getInstrument(id: String): ApiResult<Instrument> = safeCall {
        get("instruments/$id")
    }

    suspend fun getDatasetsByInstrument(
        instrumentName: String
    ): ApiResult<List<Dataset>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("instrument_name", instrumentName)
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Dataset>>()
    }

    suspend fun getProjects(): ApiResult<List<Project>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}projects") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Project>>()
    }

    suspend fun getSamplesByProject(
        projectId: String,
        onTotalKnown: (suspend (Int) -> Unit)? = null
    ): ApiResult<List<Sample>> = fetchAllPages(onTotalKnown = onTotalKnown) { limit, offset ->
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Sample>>()
    }

    suspend fun getDatasetsByProject(
        projectId: String,
        onTotalKnown: (suspend (Int) -> Unit)? = null
    ): ApiResult<List<Dataset>> = fetchAllPages(onTotalKnown = onTotalKnown) { limit, offset ->
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Dataset>>()
    }

    suspend fun getFilteredDatasets(
        projectId: String? = null,
        measurement: String? = null,
        instrumentName: String? = null,
        dataFormat: String? = null,
        sessionName: String? = null,
        ownerOrcid: String? = null,
        creationTimeGte: String? = null,
        creationTimeLte: String? = null
    ): ApiResult<List<Dataset>> = fetchAllPages { limit, offset ->
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
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Dataset>>()
    }

    suspend fun getFilteredSamples(
        projectId: String? = null,
        sampleType: String? = null,
        ownerOrcid: String? = null,
        creationTimeGte: String? = null,
        creationTimeLte: String? = null
    ): ApiResult<List<Sample>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            if (projectId != null) url.parameters.append("project_id", projectId)
            if (sampleType != null) url.parameters.append("sample_type", sampleType)
            if (ownerOrcid != null) url.parameters.append("owner_orcid", ownerOrcid)
            if (creationTimeGte != null) url.parameters.append("creation_time_gte", creationTimeGte)
            if (creationTimeLte != null) url.parameters.append("creation_time_lte", creationTimeLte)
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<Sample>>()
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

    suspend fun deleteThumbnail(uuid: String, thumbnailId: Int): ApiResult<Boolean> = safeCall {
        delete("datasets/$uuid/thumbnails/$thumbnailId")
    }

    suspend fun getDownloadLinks(uuid: String): ApiResult<Map<String, String>> = safeCall {
        val obj: JsonObject = get("datasets/$uuid/download_links")
        // Filter to only genuine signed URLs — the server may return HTTP 200 with
        // {"detail": "No files found..."} instead of a proper 404 in some cases.
        obj.entries
            .filter { (_, v) -> runCatching { v.jsonPrimitive.content.startsWith("https://") }.getOrElse { false } }
            .associate { (k, v) -> k to v.jsonPrimitive.content }
    }

    suspend fun uploadFileToDataset(uuid: String, bytes: ByteArray, filename: String): ApiResult<String> = safeCall {
        client.post("${baseUrl}datasets/$uuid/upload") {
            header("Authorization", "Bearer $apiKey")
            setBody(MultiPartFormDataContent(formData {
                append("files", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"files\"; filename=\"$filename\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }))
        }.body()
    }

    suspend fun addAssociatedFile(
        uuid: String,
        cloudPath: String,
        size: Int,
        sha256Hash: String,
        ingestionClass: String? = null
    ): ApiResult<JsonObject> = safeCall {
        client.post("${baseUrl}datasets/$uuid/associated_files") {
            header("Authorization", "Bearer $apiKey")
            if (ingestionClass != null) url.parameters.append("ingestion_class", ingestionClass)
            contentType(ContentType.Application.Json)
            setBody(AssociatedFileRequest(filename = cloudPath, size = size, sha256Hash = sha256Hash))
        }.body()
    }

    suspend fun extractMetadata(request: ExtractMetadataRequest): ApiResult<JsonObject> = safeCall {
        post("extract_metadata", request.copy(
            apiKey = ApiClient.aiApiKey?.ifBlank { null } ?: apiKey,
            apiUrl = ApiClient.aiApiUrl
        ))
    }

    suspend fun extractMetadataDirect(
        images: List<MetadataImageData>,
        context: String?,
        aiApiKey: String,
        aiApiUrl: String
    ): ApiResult<JsonObject> = safeCall {
        val contentBlocks = images.map { img ->
            AnthropicContentBlock(
                type = "image",
                source = AnthropicImageSource(type = "base64", mediaType = img.mediaType, data = img.data)
            )
        } + AnthropicContentBlock(
            type = "text",
            text = "Extract metadata from these lab notebook page(s) as a JSON object." +
                if (!context.isNullOrBlank()) " Additional context: $context" else ""
        )

        val request = AnthropicMessagesRequest(
            model = "claude-haiku-4-5",
            maxTokens = 2048,
            system = ANTHROPIC_SYSTEM_PROMPT,
            messages = listOf(AnthropicMessage(role = "user", content = contentBlocks))
        )

        val responseObj = client.post("$aiApiUrl/v1/messages") {
            header("Authorization", "Bearer $aiApiKey")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<JsonObject>()

        // Handle Anthropic-style error bodies returned as 2xx (e.g. auth errors from some proxies)
        if (responseObj["type"]?.jsonPrimitive?.content == "error") {
            val msg = responseObj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: "Unknown AI error"
            throw Exception(msg)
        }

        var raw = responseObj["content"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content?.trim()
            ?: throw Exception("No content in AI response: $responseObj")

        // Strip markdown code fences if present (mirrors server-side logic)
        if (raw.startsWith("```")) {
            raw = raw.split("\n", limit = 2).getOrElse(1) { "" }
            raw = raw.substringBeforeLast("```").trim()
        }

        json.parseToJsonElement(raw).jsonObject
    }

    companion object {
        const val ANTHROPIC_SYSTEM_PROMPT =
            "You are a scientific metadata extractor specializing in laboratory notebooks. " +
            "Analyze the provided image(s) and extract all relevant scientific metadata as a single JSON object. " +
            "Include fields such as sample identifiers, dates, measurements, conditions, instrument settings, " +
            "observations, and any other structured information visible in the notebook. " +
            "Return only valid JSON with no additional text or markdown."
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

    suspend fun getProjectUsers(projectId: String): ApiResult<List<UserLead>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}projects/$projectId/users") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<UserLead>>()
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

    // ── Pagination ───────────────────────────────────────────────────────────

    /**
     * Checks server health at the given base URL without authentication.
     * Uses GET {baseUrl}health/ready — returns ok/degraded + DB latency.
     * Pass baseUrl explicitly so callers can test an unsaved candidate URL.
     */
    suspend fun checkHealth(baseUrl: String): ApiResult<HealthStatus> = safeCall {
        val normalizedUrl = baseUrl.trim().trimEnd('/') + "/"
        client.get("${normalizedUrl}health/ready").body()
    }

    /**
     * Fetches all pages of a paginated envelope endpoint in parallel.
     * Fetches page 0 first to get [total], then fires all remaining pages
     * concurrently with limit=1000. Results are concatenated in order.
     */
    private suspend inline fun <reified T> fetchAllPages(
        pageSize: Int = 1000,
        noinline onTotalKnown: (suspend (Int) -> Unit)? = null,
        crossinline page: suspend (limit: Int, offset: Int) -> PaginatedResponse<T>
    ): ApiResult<List<T>> = safeCall {
        coroutineScope {
            val first = page(pageSize, 0)
            val total = first.total
            onTotalKnown?.invoke(total)
            val remaining = total - first.items.size
            if (remaining <= 0) return@coroutineScope first.items

            val extraPages = ceil(remaining / pageSize.toDouble()).toInt()
            val rest = (1..extraPages)
                .map { pageIndex -> async { page(pageSize, pageIndex * pageSize).items } }
                .flatMap { it.await() }

            first.items + rest
        }
    }
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}

suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: ResponseException) {
    ApiResult.Error(e.response.status.value, "HTTP ${e.response.status.value}: ${e.response.status.description}")
} catch (e: Exception) {
    ApiResult.Error(-1, e.message ?: "Unknown error")
}
