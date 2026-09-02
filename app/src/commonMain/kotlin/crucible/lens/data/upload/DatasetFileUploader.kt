package crucible.lens.data.upload

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.AssociatedFile
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.model.UploadInitiateResponse
import kotlinx.serialization.json.JsonObject

data class DatasetFileAttachment(
    val bytes: ByteArray,
    val filename: String,
    val asThumbnail: Boolean
)

data class DatasetFileUploadCheckpoint(
    val fileMfid: String? = null,
    val ingestionRequested: Boolean = false,
    val thumbnailCreated: Boolean = false
)

data class DatasetFileUploadRequest(
    val datasetUuid: String,
    val filename: String,
    val bytes: ByteArray,
    val sha256: String,
    val asThumbnail: Boolean,
    val thumbnailBase64: String,
    val checkpoint: DatasetFileUploadCheckpoint = DatasetFileUploadCheckpoint()
)

enum class DatasetFileUploadStage(val displayName: String) {
    Initiation("upload initiation"),
    Transfer("file transfer"),
    Completion("upload completion"),
    Ingestion("ingestion"),
    Thumbnail("thumbnail creation")
}

sealed interface DatasetFileUploadResult {
    data class Success(val checkpoint: DatasetFileUploadCheckpoint) : DatasetFileUploadResult
    data class Failure(
        val stage: DatasetFileUploadStage,
        val message: String,
        val checkpoint: DatasetFileUploadCheckpoint
    ) : DatasetFileUploadResult
}

data class DatasetFileUploadSummary(
    val successfulCount: Int,
    val failures: List<DatasetFileUploadResult.Failure>
) {
    val failedCount: Int get() = failures.size
    val allSucceeded: Boolean get() = failures.isEmpty()

    fun userMessage(): String = when {
        allSucceeded && successfulCount == 1 -> "File uploaded"
        allSucceeded -> "$successfulCount files uploaded"
        successfulCount == 0 -> {
            val firstFailure = failures.first()
            "Upload failed during ${firstFailure.stage.displayName}: ${firstFailure.message}"
        }
        else -> "$successfulCount uploaded, $failedCount failed. Retry remaining."
    }
}

fun summarizeDatasetFileUploads(results: List<DatasetFileUploadResult>): DatasetFileUploadSummary =
    DatasetFileUploadSummary(
        successfulCount = results.count { it is DatasetFileUploadResult.Success },
        failures = results.filterIsInstance<DatasetFileUploadResult.Failure>()
    )

internal interface DatasetFileUploadApi {
    suspend fun initiate(
        datasetUuid: String,
        filename: String,
        size: Long,
        sha256: String
    ): ApiResult<UploadInitiateResponse>

    suspend fun transfer(resumableUri: String, bytes: ByteArray, chunkSizeHint: Int): ApiResult<Unit>
    suspend fun complete(datasetUuid: String, uploadId: String, sha256: String): ApiResult<AssociatedFile>
    suspend fun requestIngestion(fileMfid: String): ApiResult<JsonObject>
    suspend fun addThumbnail(datasetUuid: String, request: ThumbnailCreateRequest): ApiResult<Thumbnail>
}

private class ApiDatasetFileUploadApi(private val apiClient: ApiClient) : DatasetFileUploadApi {
    override suspend fun initiate(
        datasetUuid: String,
        filename: String,
        size: Long,
        sha256: String
    ) = apiClient.service.initiateUpload(datasetUuid, filename, size, sha256)

    override suspend fun transfer(
        resumableUri: String,
        bytes: ByteArray,
        chunkSizeHint: Int
    ) = apiClient.service.uploadChunksToGCS(resumableUri, bytes, chunkSizeHint)

    override suspend fun complete(
        datasetUuid: String,
        uploadId: String,
        sha256: String
    ) = apiClient.service.completeUpload(datasetUuid, uploadId, sha256)

    override suspend fun requestIngestion(fileMfid: String) = apiClient.service.requestIngestion(fileMfid)

    override suspend fun addThumbnail(
        datasetUuid: String,
        request: ThumbnailCreateRequest
    ) = apiClient.service.addThumbnail(datasetUuid, request)
}

class DatasetFileUploader private constructor(private val api: DatasetFileUploadApi) {
    constructor(apiClient: ApiClient) : this(ApiDatasetFileUploadApi(apiClient))

    suspend fun upload(request: DatasetFileUploadRequest): DatasetFileUploadResult {
        var checkpoint = request.checkpoint

        if (checkpoint.fileMfid == null) {
            val session = when (val result = api.initiate(
                request.datasetUuid,
                request.filename,
                request.bytes.size.toLong(),
                request.sha256
            )) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> return failure(DatasetFileUploadStage.Initiation, result, checkpoint)
            }

            val existingFile = session.existingFile
            if (existingFile != null) {
                checkpoint = checkpoint.copy(fileMfid = existingFile.mfid)
            } else {
                val resumableUri = session.resumableUri
                    ?: return invalidResponse(DatasetFileUploadStage.Initiation, checkpoint)
                val uploadId = session.uploadId
                    ?: return invalidResponse(DatasetFileUploadStage.Initiation, checkpoint)

                when (val result = api.transfer(resumableUri, request.bytes, session.chunkSizeHint)) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> return failure(DatasetFileUploadStage.Transfer, result, checkpoint)
                }

                when (val result = api.complete(request.datasetUuid, uploadId, request.sha256)) {
                    is ApiResult.Success -> checkpoint = checkpoint.copy(fileMfid = result.data.mfid)
                    is ApiResult.Error -> return failure(DatasetFileUploadStage.Completion, result, checkpoint)
                }
            }
        }

        val fileMfid = requireNotNull(checkpoint.fileMfid)
        if (!checkpoint.ingestionRequested) {
            when (val result = api.requestIngestion(fileMfid)) {
                is ApiResult.Success -> checkpoint = checkpoint.copy(ingestionRequested = true)
                is ApiResult.Error -> return failure(DatasetFileUploadStage.Ingestion, result, checkpoint)
            }
        }

        if (request.asThumbnail && !checkpoint.thumbnailCreated) {
            val thumbnail = ThumbnailCreateRequest(
                thumbnailName = request.filename,
                thumbnailB64str = request.thumbnailBase64
            )
            when (val result = api.addThumbnail(request.datasetUuid, thumbnail)) {
                is ApiResult.Success -> checkpoint = checkpoint.copy(thumbnailCreated = true)
                is ApiResult.Error -> return failure(DatasetFileUploadStage.Thumbnail, result, checkpoint)
            }
        }

        return DatasetFileUploadResult.Success(checkpoint)
    }

    private fun failure(
        stage: DatasetFileUploadStage,
        result: ApiResult.Error,
        checkpoint: DatasetFileUploadCheckpoint
    ) = DatasetFileUploadResult.Failure(stage, result.message, checkpoint)

    private fun invalidResponse(
        stage: DatasetFileUploadStage,
        checkpoint: DatasetFileUploadCheckpoint
    ) = DatasetFileUploadResult.Failure(stage, "Server returned an incomplete upload session", checkpoint)

    internal companion object {
        fun createForTest(api: DatasetFileUploadApi) = DatasetFileUploader(api)
    }
}
