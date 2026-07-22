package crucible.lens.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import kotlinx.serialization.json.JsonObject
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.platform.PlatformBase64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Shared result type ────────────────────────────────────────────────────────

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val uuid: String, val uploadWarning: String? = null) : SaveState()
    data class Error(val message: String) : SaveState()
}

// ── CreateSampleViewModel ─────────────────────────────────────────────────────

class CreateSampleViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: SampleCreateRequest, projectId: String?, metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = apiClient.service.createSample(request)) {
                    is ApiResult.Success -> {
                        val sample = resp.data
                        cacheManager.cacheResource(sample.uniqueId, sample)
                        projectId?.let { cacheManager.clearProjectDetail(it) }
                        if (!metadata.isNullOrEmpty()) {
                            apiClient.service.postResourceMetadata(sample.uniqueId, metadata)
                        }
                        SaveState.Success(sample.uniqueId)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── CreateDatasetViewModel ────────────────────────────────────────────────────

class CreateDatasetViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: DatasetCreateRequest, files: List<Pair<ByteArray, Boolean>> = emptyList(), metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                val createResp = apiClient.service.createDataset(request)
                if (createResp !is ApiResult.Success) {
                    val code = (createResp as? ApiResult.Error)?.code ?: -1
                    return@launch run { _saveState.value = SaveState.Error("Could not create dataset ($code)") }
                }
                val newDataset = createResp.data
                val newUuid = newDataset.uniqueId
                cacheManager.cacheResource(newUuid, newDataset)
                request.projectId?.let { cacheManager.clearProjectDetail(it) }

                var uploadFailures = 0
                var thumbnailFailures = 0
                files.forEachIndexed { index, (bytes, asThumbnail) ->
                    val filename = "file_${newUuid}_$index.jpg"
                    val sha256 = PlatformCrypto.sha256Hex(bytes)
                    // Step 1: initiate GCS resumable session (sha256 enables server-side deduplication)
                    val initiateResp = apiClient.service.initiateUpload(newUuid, filename, bytes.size.toLong(), sha256)
                    if (initiateResp is ApiResult.Success) {
                        val session = initiateResp.data
                        val fileMfid: String?
                        if (session.existingFile != null) {
                            // Server detected duplicate — skip upload and completion
                            fileMfid = session.existingFile.mfid
                        } else {
                            // Step 2: upload chunks directly to GCS
                            val chunkResp = apiClient.service.uploadChunksToGCS(
                                resumableUri = session.resumableUri ?: error("Missing resumable URI"),
                                bytes = bytes,
                                chunkSizeHint = session.chunkSizeHint
                            )
                            if (chunkResp is ApiResult.Success) {
                                // Step 3: finalize — server registers as AssociatedFile
                                val completeResp = apiClient.service.completeUpload(newUuid, session.uploadId ?: error("Missing upload ID"), sha256)
                                fileMfid = if (completeResp is ApiResult.Success) completeResp.data.mfid else null
                                if (fileMfid == null) uploadFailures++
                            } else {
                                fileMfid = null
                                uploadFailures++
                            }
                        }
                        if (fileMfid != null) {
                            // Step 4: trigger ingestion worker
                            apiClient.service.requestIngestion(fileMfid)
                            // Step 5: optional thumbnail — only if file is available
                            if (asThumbnail) {
                                val thumbResp = apiClient.service.addThumbnail(
                                    newUuid,
                                    ThumbnailCreateRequest(thumbnailName = filename, thumbnailB64str = PlatformBase64.encode(bytes))
                                )
                                if (thumbResp is ApiResult.Error) thumbnailFailures++
                            }
                        }
                    } else {
                        uploadFailures++
                    }
                }

                val warning = when {
                    uploadFailures > 0 && thumbnailFailures > 0 ->
                        "Dataset created, but $uploadFailures file upload(s) and $thumbnailFailures thumbnail(s) failed"
                    uploadFailures > 0 -> "Dataset created, but $uploadFailures file upload(s) failed"
                    thumbnailFailures > 0 -> "Dataset created, but $thumbnailFailures thumbnail(s) failed to upload"
                    else -> null
                }
                if (!metadata.isNullOrEmpty()) {
                    apiClient.service.postResourceMetadata(newUuid, metadata)
                }
                SaveState.Success(newUuid, uploadWarning = warning)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── EditResourceViewModel ─────────────────────────────────────────────────────

class EditResourceViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // metadata: null = unchanged or empty, skip the API call. Non-null = changed and non-empty, post it.
    fun updateSample(uuid: String, request: SampleUpdateRequest, metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = apiClient.service.updateSample(uuid, request)) {
                    is ApiResult.Success -> {
                        cacheManager.cacheResource(uuid, resp.data)
                        if (metadata != null) {
                            apiClient.service.postResourceMetadata(uuid, metadata, overwrite = true)
                        }
                        SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun updateDataset(uuid: String, request: DatasetUpdateRequest, metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = apiClient.service.updateDataset(uuid, request)) {
                    is ApiResult.Success -> {
                        cacheManager.cacheResource(uuid, resp.data)
                        if (metadata != null) {
                            apiClient.service.postResourceMetadata(uuid, metadata, overwrite = true)
                        }
                        SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}
