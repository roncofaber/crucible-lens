package crucible.lens.data.upload

import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.AssociatedFile
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.model.UploadInitiateResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DatasetFileUploaderTest {
    @Test
    fun completesEveryUploadStage() = runTest {
        val api = FakeDatasetFileUploadApi()
        val result = DatasetFileUploader.createForTest(api).upload(request())

        val success = assertIs<DatasetFileUploadResult.Success>(result)
        assertEquals("file-id", success.checkpoint.fileMfid)
        assertEquals(true, success.checkpoint.ingestionRequested)
        assertEquals(true, success.checkpoint.thumbnailCreated)
        assertEquals(listOf("initiate", "transfer", "complete", "ingestion", "thumbnail"), api.calls)
    }

    @Test
    fun returnsInitiationFailureWithoutReportingSuccess() = runTest {
        val api = FakeDatasetFileUploadApi(
            initiateResult = ApiResult.Error(503, "Service unavailable")
        )
        val result = DatasetFileUploader.createForTest(api).upload(request())

        val failure = assertIs<DatasetFileUploadResult.Failure>(result)
        assertEquals(DatasetFileUploadStage.Initiation, failure.stage)
        assertEquals(listOf("initiate"), api.calls)
        assertEquals(
            "Upload failed during upload initiation: Service unavailable",
            summarizeDatasetFileUploads(listOf(result)).userMessage()
        )
    }

    @Test
    fun existingFileSkipsTransferAndCompletion() = runTest {
        val api = FakeDatasetFileUploadApi(
            initiateResult = ApiResult.Success(
                UploadInitiateResponse(
                    existingFile = AssociatedFile(mfid = "existing-id", filename = "image.jpg")
                )
            )
        )
        val result = DatasetFileUploader.createForTest(api).upload(request())

        val success = assertIs<DatasetFileUploadResult.Success>(result)
        assertEquals("existing-id", success.checkpoint.fileMfid)
        assertEquals(listOf("initiate", "ingestion", "thumbnail"), api.calls)
    }

    @Test
    fun retryResumesAfterCompletedIngestion() = runTest {
        val api = FakeDatasetFileUploadApi(
            thumbnailResult = ApiResult.Error(500, "Thumbnail failed")
        )
        val uploader = DatasetFileUploader.createForTest(api)
        val firstResult = assertIs<DatasetFileUploadResult.Failure>(uploader.upload(request()))

        api.calls.clear()
        api.thumbnailResult = ApiResult.Success(Thumbnail(thumbnailB64 = "encoded"))
        val retryResult = uploader.upload(request(checkpoint = firstResult.checkpoint))

        assertIs<DatasetFileUploadResult.Success>(retryResult)
        assertEquals(listOf("thumbnail"), api.calls)
    }

    @Test
    fun summarizesPartialSuccess() {
        val failure = DatasetFileUploadResult.Failure(
            DatasetFileUploadStage.Transfer,
            "Network error",
            DatasetFileUploadCheckpoint()
        )
        val summary = summarizeDatasetFileUploads(
            listOf(
                DatasetFileUploadResult.Success(DatasetFileUploadCheckpoint(fileMfid = "file-id")),
                failure
            )
        )

        assertEquals(1, summary.successfulCount)
        assertEquals(1, summary.failedCount)
        assertEquals("1 uploaded, 1 failed. Retry remaining.", summary.userMessage())
    }

    private fun request(
        checkpoint: DatasetFileUploadCheckpoint = DatasetFileUploadCheckpoint()
    ) = DatasetFileUploadRequest(
        datasetUuid = "dataset-id",
        filename = "image.jpg",
        bytes = byteArrayOf(1, 2, 3),
        sha256 = "hash",
        asThumbnail = true,
        thumbnailBase64 = "encoded",
        checkpoint = checkpoint
    )
}

private class FakeDatasetFileUploadApi(
    private val initiateResult: ApiResult<UploadInitiateResponse> = ApiResult.Success(
        UploadInitiateResponse(uploadId = "upload-id", resumableUri = "https://upload.example")
    ),
    private val transferResult: ApiResult<Unit> = ApiResult.Success(Unit),
    private val completeResult: ApiResult<AssociatedFile> = ApiResult.Success(
        AssociatedFile(mfid = "file-id", filename = "image.jpg")
    ),
    private val ingestionResult: ApiResult<JsonObject> = ApiResult.Success(JsonObject(emptyMap())),
    var thumbnailResult: ApiResult<Thumbnail> = ApiResult.Success(Thumbnail(thumbnailB64 = "encoded"))
) : DatasetFileUploadApi {
    val calls = mutableListOf<String>()

    override suspend fun initiate(
        datasetUuid: String,
        filename: String,
        size: Long,
        sha256: String
    ): ApiResult<UploadInitiateResponse> {
        calls += "initiate"
        return initiateResult
    }

    override suspend fun transfer(
        resumableUri: String,
        bytes: ByteArray,
        chunkSizeHint: Int
    ): ApiResult<Unit> {
        calls += "transfer"
        return transferResult
    }

    override suspend fun complete(
        datasetUuid: String,
        uploadId: String,
        sha256: String
    ): ApiResult<AssociatedFile> {
        calls += "complete"
        return completeResult
    }

    override suspend fun requestIngestion(fileMfid: String): ApiResult<JsonObject> {
        calls += "ingestion"
        return ingestionResult
    }

    override suspend fun addThumbnail(
        datasetUuid: String,
        request: ThumbnailCreateRequest
    ): ApiResult<Thumbnail> {
        calls += "thumbnail"
        return thumbnailResult
    }
}
