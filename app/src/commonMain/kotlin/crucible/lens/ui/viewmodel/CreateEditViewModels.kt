package crucible.lens.ui.viewmodel

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Shared result type ────────────────────────────────────────────────────────

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val uuid: String) : SaveState()
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

                files.forEachIndexed { index, (bytes, asThumbnail) ->
                    val filename = "file_${newUuid}_$index.jpg"
                    val uploadResp = ApiClient.service.uploadFileToDataset(newUuid, bytes, filename)
                    if (uploadResp is ApiResult.Success) {
                        val cloudPath = uploadResp.data.replace("./mnt/gcs", "crucible-uploads")
                        ApiClient.service.addAssociatedFile(newUuid, cloudPath, bytes.size, PlatformCrypto.sha256Hex(bytes))
                    }
                    if (asThumbnail) {
                        ApiClient.service.addThumbnail(
                            newUuid,
                            ThumbnailCreateRequest(thumbnailName = filename, thumbnailB64str = PlatformBase64.encode(bytes))
                        )
                    }
                    // All post-creation steps are non-fatal — dataset was already created.
                }

                SaveState.Success(newUuid)
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
