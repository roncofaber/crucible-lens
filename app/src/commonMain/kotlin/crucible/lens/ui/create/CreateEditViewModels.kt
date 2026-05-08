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
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.platform.PlatformBase64
import kotlinx.serialization.json.jsonPrimitive
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

class CreateSampleViewModel : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: SampleCreateRequest, projectId: String?) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = ApiClient.service.createSample(request)) {
                    is ApiResult.Success -> {
                        val sample = resp.data
                        CacheManager.cacheResource(sample.uniqueId, sample)
                        projectId?.let { CacheManager.clearProjectDetail(it) }
                        SaveState.Success(sample.uniqueId)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── CreateDatasetViewModel ────────────────────────────────────────────────────

class CreateDatasetViewModel : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: DatasetCreateRequest, files: List<Pair<ByteArray, Boolean>> = emptyList()) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                val createResp = ApiClient.service.createDataset(request)
                if (createResp !is ApiResult.Success) {
                    val code = (createResp as? ApiResult.Error)?.code ?: -1
                    return@launch run { _saveState.value = SaveState.Error("Could not create dataset ($code)") }
                }
                val newDataset = createResp.data
                val newUuid = newDataset.uniqueId
                CacheManager.cacheResource(newUuid, newDataset)
                request.projectId?.let { CacheManager.clearProjectDetail(it) }

                var uploadFailures = 0
                var thumbnailFailures = 0
                files.forEachIndexed { index, (bytes, asThumbnail) ->
                    val filename = "file_${newUuid}_$index.jpg"
                    val sha256 = PlatformCrypto.sha256Hex(bytes)
                    // Step 1: initiate GCS resumable session
                    val initiateResp = ApiClient.service.initiateUpload(newUuid, filename, bytes.size.toLong())
                    if (initiateResp is ApiResult.Success) {
                        val session = initiateResp.data
                        // Step 2: upload chunks directly to GCS
                        val chunkResp = ApiClient.service.uploadChunksToGCS(
                            resumableUri = session.resumableUri,
                            bytes = bytes,
                            chunkSizeHint = session.chunkSizeHint
                        )
                        if (chunkResp is ApiResult.Success) {
                            // Step 3: finalize — server registers as AssociatedFile
                            val completeResp = ApiClient.service.completeUpload(newUuid, session.uploadId, sha256)
                            // Step 4: trigger ingestion worker
                            if (completeResp is ApiResult.Success) {
                                val storedFilename = completeResp.data["filename"]?.jsonPrimitive?.content
                                if (storedFilename != null) {
                                    ApiClient.service.requestIngestion(newUuid, storedFilename, bytes.size.toLong())
                                }
                            }
                        } else {
                            uploadFailures++
                        }
                    } else {
                        uploadFailures++
                    }
                    if (asThumbnail) {
                        val thumbResp = ApiClient.service.addThumbnail(
                            newUuid,
                            ThumbnailCreateRequest(thumbnailName = filename, thumbnailB64str = PlatformBase64.encode(bytes))
                        )
                        if (thumbResp is ApiResult.Error) thumbnailFailures++
                    }
                }

                val warning = when {
                    uploadFailures > 0 && thumbnailFailures > 0 ->
                        "Dataset created, but $uploadFailures file upload(s) and $thumbnailFailures thumbnail(s) failed"
                    uploadFailures > 0 -> "Dataset created, but $uploadFailures file upload(s) failed"
                    thumbnailFailures > 0 -> "Dataset created, but $thumbnailFailures thumbnail(s) failed to upload"
                    else -> null
                }
                SaveState.Success(newUuid, uploadWarning = warning)
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── EditResourceViewModel ─────────────────────────────────────────────────────

class EditResourceViewModel : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun updateSample(uuid: String, request: SampleUpdateRequest) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = ApiClient.service.updateSample(uuid, request)) {
                    is ApiResult.Success -> {
                        CacheManager.cacheResource(uuid, resp.data)
                        SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun updateDataset(uuid: String, request: DatasetUpdateRequest) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = try {
                when (val resp = ApiClient.service.updateDataset(uuid, request)) {
                    is ApiResult.Success -> {
                        CacheManager.cacheResource(uuid, resp.data)
                        SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> SaveState.Error("Save failed (${resp.code})")
                }
            } catch (_: Exception) {
                SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}
