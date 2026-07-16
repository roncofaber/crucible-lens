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
import crucible.lens.data.model.UploadInitiateRequest
import crucible.lens.data.model.UploadInitiateResponse
import crucible.lens.data.model.AssociatedFile
import crucible.lens.data.model.UploadCompleteRequest
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
import crucible.lens.data.model.User
import crucible.lens.data.model.UserSearchResult
import crucible.lens.data.model.ProfileUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.ceil
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
    private val gcsClient: HttpClient,
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

    suspend fun getProfile(): ApiResult<User> = safeCall {
        get("account/profile")
    }

    suspend fun updateProfile(
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null,
        username: String? = null
    ): ApiResult<User> = safeCall {
        patch("account/profile", ProfileUpdateRequest(
            firstName = firstName?.ifBlank { null },
            lastName = lastName?.ifBlank { null },
            email = email?.ifBlank { null },
            username = username?.ifBlank { null }
        ))
    }

    suspend fun checkUsernameAvailability(username: String, currentUniqueId: String): ApiResult<Boolean> = safeCall {
        val results = client.get("${baseUrl}users/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", username.lowercase())
            url.parameters.append("limit", "5")
        }.body<List<UserSearchResult>>()
        val taken = results.any {
            it.username?.lowercase() == username.lowercase() && it.uniqueId != currentUniqueId
        }
        !taken
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
    ): ApiResult<List<Dataset>> = fetchAllPagesCursor { limit, cursor ->
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("instrument_name", instrumentName)
            url.parameters.append("limit", limit.toString())
            if (cursor != null) url.parameters.append("cursor", cursor)
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
    ): ApiResult<List<Sample>> = fetchAllPagesCursor(onTotalKnown = onTotalKnown) { limit, cursor ->
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
            if (cursor != null) url.parameters.append("cursor", cursor)
        }.body<PaginatedResponse<Sample>>()
    }

    suspend fun getDatasetsByProject(
        projectId: String,
        onTotalKnown: (suspend (Int) -> Unit)? = null
    ): ApiResult<List<Dataset>> = fetchAllPagesCursor(onTotalKnown = onTotalKnown) { limit, cursor ->
        client.get("${baseUrl}datasets") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
            if (cursor != null) url.parameters.append("cursor", cursor)
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
    ): ApiResult<List<Dataset>> = fetchAllPagesCursor { limit, cursor ->
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
            if (cursor != null) url.parameters.append("cursor", cursor)
        }.body<PaginatedResponse<Dataset>>()
    }

    suspend fun getFilteredSamples(
        projectId: String? = null,
        sampleType: String? = null,
        ownerOrcid: String? = null,
        creationTimeGte: String? = null,
        creationTimeLte: String? = null
    ): ApiResult<List<Sample>> = fetchAllPagesCursor { limit, cursor ->
        client.get("${baseUrl}samples") {
            header("Authorization", "Bearer $apiKey")
            if (projectId != null) url.parameters.append("project_id", projectId)
            if (sampleType != null) url.parameters.append("sample_type", sampleType)
            if (ownerOrcid != null) url.parameters.append("owner_orcid", ownerOrcid)
            if (creationTimeGte != null) url.parameters.append("creation_time_gte", creationTimeGte)
            if (creationTimeLte != null) url.parameters.append("creation_time_lte", creationTimeLte)
            url.parameters.append("limit", limit.toString())
            if (cursor != null) url.parameters.append("cursor", cursor)
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

    suspend fun getDatasetFiles(uuid: String): ApiResult<List<crucible.lens.data.model.AssociatedFile>> = safeCall {
        get("datasets/$uuid/files")
    }

    suspend fun getFileDownloadLink(fileId: String): ApiResult<crucible.lens.data.model.FileDownloadLinkResponse> = safeCall {
        get("files/$fileId/download_link")
    }

    suspend fun getDownloadLinks(uuid: String): ApiResult<Map<String, String>> = safeCall {
        val obj: JsonObject = get("datasets/$uuid/download_links")
        // Filter to only genuine signed URLs — the server may return HTTP 200 with
        // {"detail": "No files found..."} instead of a proper 404 in some cases.
        obj.entries
            .filter { (_, v) -> runCatching { v.jsonPrimitive.content.startsWith("https://") }.getOrElse { false } }
            .associate { (k, v) -> k to v.jsonPrimitive.content }
    }

    // ── GCS resumable upload ──────────────────────────────────────────────────

    suspend fun initiateUpload(
        uuid: String,
        filename: String,
        size: Long,
        sha256Hash: String
    ): ApiResult<UploadInitiateResponse> = safeCall {
        post("datasets/$uuid/upload/initiate", UploadInitiateRequest(filename = filename, size = size, sha256Hash = sha256Hash))
    }

    suspend fun completeUpload(
        uuid: String,
        uploadId: String,
        sha256Hash: String
    ): ApiResult<AssociatedFile> = safeCall {
        post("datasets/$uuid/upload/complete", UploadCompleteRequest(uploadId = uploadId, sha256Hash = sha256Hash))
    }

    // Triggers the ingestion worker for a registered AssociatedFile.
    // `fileMfid` is the mfid string returned in the completeUpload response.
    // `ingestionClass` selects the ingestor — defaults to "ApiUploadIngestor" for standard file uploads.
    suspend fun requestIngestion(
        fileMfid: String,
        ingestionClass: String? = "ApiUploadIngestor"
    ): ApiResult<JsonObject> = safeCall {
        client.post("${baseUrl}files/$fileMfid/ingest") {
            header("Authorization", "Bearer $apiKey")
            if (ingestionClass != null) url.parameters.append("ingestion_class", ingestionClass)
        }.body()
    }

    // Uploads bytes to a GCS resumable URI in chunks. Retries each chunk up to 3 times,
    // probing the GCS session for the resume offset on failure.
    // The resumable URI is pre-authenticated — no Authorization header is sent to GCS.
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun uploadChunksToGCS(
        resumableUri: String,
        bytes: ByteArray,
        chunkSizeHint: Int
    ): ApiResult<Unit> = safeCall {
        val total = bytes.size.toLong()
        val alignedChunkSize = maxOf(chunkSizeHint, 256 * 1024)
            .let { it - (it % (256 * 1024)) }
            .coerceAtLeast(256 * 1024)
        val crc32c = crucible.lens.data.util.crc32cBase64(bytes)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + alignedChunkSize, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val isLast = end == bytes.size
            var chunkDone = false
            repeat(3) { attempt ->
                if (chunkDone) return@repeat
                val response = gcsClient.put(resumableUri) {
                    header("Content-Range", "bytes $offset-${end - 1}/$total")
                    if (isLast) header("X-Goog-Hash", "crc32c=$crc32c")
                    contentType(io.ktor.http.ContentType.Application.OctetStream)
                    setBody(chunk)
                }
                when (response.status.value) {
                    308 -> { offset = end; chunkDone = true }
                    200, 201 -> return@safeCall Unit
                    else -> {
                        // Probe session state to find safe resume offset
                        val probe = gcsClient.put(resumableUri) {
                            header("Content-Range", "bytes */$total")
                        }
                        when (probe.status.value) {
                            200, 201 -> return@safeCall Unit
                            308 -> {
                                val range = probe.headers["Range"]
                                if (range != null) {
                                    offset = range.substringAfter('-').toIntOrNull()?.plus(1) ?: offset
                                }
                                chunkDone = true
                            }
                            else -> if (attempt == 2) error("GCS upload failed after 3 attempts: ${response.status}")
                        }
                    }
                }
            }
        }
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
        client.post("${baseUrl}resources/$resourceId/delete") {
            header("Authorization", "Bearer $apiKey")
            if (reason != null) url.parameters.append("reason", reason)
        }
    }

    suspend fun getProjectUsers(projectId: String): ApiResult<List<User>> = fetchAllPages { limit, offset ->
        client.get("${baseUrl}projects/$projectId/users") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("limit", limit.toString())
            url.parameters.append("offset", offset.toString())
        }.body<PaginatedResponse<User>>()
    }

    suspend fun searchSamples(q: String, projectId: String? = null, limit: Int = 20): ApiResult<List<Sample>> = safeCall {
        client.get("${baseUrl}samples/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", q)
            if (projectId != null) url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
        }.body<PaginatedResponse<Sample>>().items
    }

    suspend fun searchDatasets(q: String, projectId: String? = null, limit: Int = 20): ApiResult<List<Dataset>> = safeCall {
        client.get("${baseUrl}datasets/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", q)
            if (projectId != null) url.parameters.append("project_id", projectId)
            url.parameters.append("limit", limit.toString())
        }.body<PaginatedResponse<Dataset>>().items
    }

    suspend fun searchProjects(q: String, limit: Int = 20): ApiResult<List<Project>> = safeCall {
        client.get("${baseUrl}projects/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", q)
            url.parameters.append("limit", limit.toString())
        }.body<PaginatedResponse<Project>>().items
    }

    suspend fun searchInstruments(q: String, limit: Int = 20): ApiResult<List<Instrument>> = safeCall {
        client.get("${baseUrl}instruments/search") {
            header("Authorization", "Bearer $apiKey")
            url.parameters.append("q", q)
            url.parameters.append("limit", limit.toString())
        }.body<PaginatedResponse<Instrument>>().items
    }

    suspend fun searchScientificMetadata(
        query: String,
        limit: Int = 50
    ): ApiResult<List<MetadataSearchResult>> = safeCall {
        client.get("${baseUrl}resources/metadata/search") {
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
     * Fetches all pages of an offset-paginated endpoint in parallel.
     * Fetches page 0 first to get [total], then fires all remaining pages
     * concurrently. Results are concatenated in order.
     */
    private suspend inline fun <reified T> fetchAllPages(
        pageSize: Int = 1000,
        noinline onTotalKnown: (suspend (Int) -> Unit)? = null,
        crossinline page: suspend (limit: Int, offset: Int) -> PaginatedResponse<T>
    ): ApiResult<List<T>> = safeCall {
        coroutineScope {
            val first = page(pageSize, 0)
            val total = first.total ?: first.items.size
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

    /**
     * Fetches all pages of a keyset-paginated endpoint sequentially by following [next_cursor].
     * [total] is available only on the first page and forwarded to [onTotalKnown] when present.
     */
    private suspend inline fun <reified T> fetchAllPagesCursor(
        pageSize: Int = 1000,
        noinline onTotalKnown: (suspend (Int) -> Unit)? = null,
        crossinline page: suspend (limit: Int, cursor: String?) -> PaginatedResponse<T>
    ): ApiResult<List<T>> = safeCall {
        val result = mutableListOf<T>()
        var cursor: String? = null
        var isFirst = true
        do {
            val response = page(pageSize, cursor)
            if (isFirst) {
                response.total?.let { onTotalKnown?.invoke(it) }
                isFirst = false
            }
            result.addAll(response.items)
            cursor = response.nextCursor
        } while (cursor != null)
        result
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
