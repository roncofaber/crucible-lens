package crucible.lens.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.ProjectCreateRequest
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.User
import crucible.lens.ui.common.MetadataWrite
import kotlinx.serialization.json.JsonObject
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.data.util.SEARCH_DEBOUNCE_MS
import crucible.lens.data.util.SEARCH_MIN_QUERY_LENGTH
import crucible.lens.platform.PlatformBase64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: SampleCreateRequest, projectId: String?, metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.createSample(request)) {
                    is ApiResult.Success -> {
                        var sample = resp.data
                        repository.cacheResource(sample.uniqueId, sample)
                        projectId?.let { repository.invalidateProjectData(it) }
                        var metadataWarning: String? = null
                        if (!metadata.isNullOrEmpty()) {
                            when (val metaResp = apiClient.service.postResourceMetadata(sample.uniqueId, metadata)) {
                                is ApiResult.Success -> {
                                    sample = sample.copy(scientificMetadata = metaResp.data)
                                    repository.cacheResource(sample.uniqueId, sample)
                                }
                                is ApiResult.Error -> metadataWarning = "Sample created, but metadata failed to save (${metaResp.code})"
                            }
                        }
                        _saveState.value = SaveState.Success(sample.uniqueId, uploadWarning = metadataWarning)
                    }
                    is ApiResult.Error -> _saveState.value = SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── CreateDatasetViewModel ────────────────────────────────────────────────────

class CreateDatasetViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: DatasetCreateRequest, files: List<Pair<ByteArray, Boolean>> = emptyList(), metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                val createResp = apiClient.service.createDataset(request)
                if (createResp !is ApiResult.Success) {
                    val code = (createResp as? ApiResult.Error)?.code ?: -1
                    _saveState.value = SaveState.Error("Could not create dataset ($code)")
                    return@launch
                }
                var newDataset = createResp.data
                val newUuid = newDataset.uniqueId
                repository.cacheResource(newUuid, newDataset)
                request.projectId?.let { repository.invalidateProjectData(it) }

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

                var metadataFailed = false
                if (!metadata.isNullOrEmpty()) {
                    when (val metaResp = apiClient.service.postResourceMetadata(newUuid, metadata)) {
                        is ApiResult.Success -> {
                            newDataset = newDataset.copy(scientificMetadata = metaResp.data)
                            repository.cacheResource(newUuid, newDataset)
                        }
                        is ApiResult.Error -> metadataFailed = true
                    }
                }

                val problems = buildList {
                    if (uploadFailures > 0) add("$uploadFailures file upload(s) failed")
                    if (thumbnailFailures > 0) add("$thumbnailFailures thumbnail(s) failed to upload")
                    if (metadataFailed) add("metadata failed to save")
                }
                val warning = if (problems.isEmpty()) null else "Dataset created, but " + problems.joinToString(" and ")
                _saveState.value = SaveState.Success(newUuid, uploadWarning = warning)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── CreateProjectViewModel ────────────────────────────────────────────────────

data class CreateProjectFormState(
    val title: String = "",
    val projectId: String = "",
    // Once the user edits the ID field directly, it stops following the title - same behavior as
    // GitHub/Linear/Notion's slug-from-title auto-fill, so a manual correction never gets clobbered
    // by the next keystroke in the name field.
    val projectIdEditedManually: Boolean = false,
    val organization: String = "",
    val leadUsername: String = "",
    val leadSearch: List<User> = emptyList(),
    val isLeadSearching: Boolean = false
)

// GitHub-repo-name convention: lowercase, runs of anything non-alphanumeric collapse to a single
// dash, no leading/trailing dash.
private fun slugify(input: String): String =
    input.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

class CreateProjectViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository,
    prefs: AppPreferences
) : ViewModel() {

    // Defaults the lead to whoever is creating the project - the common case by far - while still
    // leaving it a normal, editable SearchPickerField for the rare project led by someone else.
    // leadSearch is seeded with the same user, not left emptyList() - SearchPickerField derives
    // "resolved" by matching leadUsername against leadSearch, so an empty list here would render
    // the default lead as an unresolved/not-found field the moment the screen opens.
    private val _formState = MutableStateFlow(
        prefs.userProfile.value?.let { CreateProjectFormState(leadUsername = it.username ?: "", leadSearch = listOf(it)) }
            ?: CreateProjectFormState()
    )
    val formState: StateFlow<CreateProjectFormState> = _formState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private var leadSearchJob: Job? = null

    fun onTitleChanged(value: String) {
        _formState.value = _formState.value.let { s ->
            s.copy(title = value, projectId = if (s.projectIdEditedManually) s.projectId else slugify(value))
        }
    }

    fun onProjectIdChanged(value: String) {
        _formState.value = _formState.value.copy(projectId = value, projectIdEditedManually = true)
    }

    fun onOrganizationChanged(value: String) { _formState.value = _formState.value.copy(organization = value) }

    fun onLeadUsernameChanged(value: String) {
        leadSearchJob?.cancel()
        _formState.value = _formState.value.copy(leadUsername = value, leadSearch = emptyList(), isLeadSearching = false)
        if (value.length < SEARCH_MIN_QUERY_LENGTH) return
        _formState.value = _formState.value.copy(isLeadSearching = true)
        leadSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = (apiClient.service.searchUsers(value) as? ApiResult.Success)?.data ?: emptyList()
            _formState.value = _formState.value.copy(leadSearch = results, isLeadSearching = false)
        }
    }

    fun selectLeadUser(user: User) {
        leadSearchJob?.cancel()
        // Keeps the picked user as a singleton list, not emptyList() - SearchPickerField derives
        // "resolved" by matching the current query against `results`, so clearing it would
        // immediately un-resolve the field right after picking.
        _formState.value = _formState.value.copy(leadUsername = user.username ?: "", leadSearch = listOf(user), isLeadSearching = false)
    }

    fun create() {
        val draft = _formState.value
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.createProject(
                    ProjectCreateRequest(
                        projectId = draft.projectId.trim(),
                        title = draft.title.trim(),
                        organization = draft.organization.trim(),
                        projectLeadUsername = draft.leadUsername.trim().ifBlank { null }
                    )
                )) {
                    is ApiResult.Success -> {
                        repository.invalidateProjects()
                        _saveState.value = SaveState.Success(resp.data.projectId)
                    }
                    is ApiResult.Error -> _saveState.value = SaveState.Error(
                        when (resp.code) {
                            400 -> "A project lead is required"
                            404 -> "Couldn't find a user with that username"
                            409 -> "That project ID is already taken"
                            else -> "Save failed (${resp.code})"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}

// ── EditResourceViewModel ─────────────────────────────────────────────────────

class EditResourceViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // metadataWrite: null = unchanged or blank, skip the API call. Merge -> PATCH (only the
    // changed keys, so a concurrent edit to any other key survives). Replace -> POST ?overwrite=true
    // (only needed when a key was deleted in the editor, since PATCH can't express a delete).
    fun updateSample(uuid: String, request: SampleUpdateRequest, metadataWrite: MetadataWrite? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.updateSample(uuid, request)) {
                    is ApiResult.Success -> {
                        var sample = resp.data
                        if (metadataWrite != null) {
                            val metaResp = when (metadataWrite) {
                                is MetadataWrite.Merge -> apiClient.service.patchResourceMetadata(uuid, metadataWrite.updates)
                                is MetadataWrite.Replace -> apiClient.service.postResourceMetadata(uuid, metadataWrite.full, overwrite = true)
                            }
                            when (metaResp) {
                                is ApiResult.Success -> sample = sample.copy(scientificMetadata = metaResp.data)
                                is ApiResult.Error -> {
                                    repository.cacheResource(uuid, sample)
                                    _saveState.value = SaveState.Error("Saved, but metadata failed to save (${metaResp.code})")
                                    return@launch
                                }
                            }
                        }
                        repository.cacheResource(uuid, sample)
                        _saveState.value = SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> _saveState.value = SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun updateDataset(uuid: String, request: DatasetUpdateRequest, metadataWrite: MetadataWrite? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.updateDataset(uuid, request)) {
                    is ApiResult.Success -> {
                        var dataset = resp.data
                        if (metadataWrite != null) {
                            val metaResp = when (metadataWrite) {
                                is MetadataWrite.Merge -> apiClient.service.patchResourceMetadata(uuid, metadataWrite.updates)
                                is MetadataWrite.Replace -> apiClient.service.postResourceMetadata(uuid, metadataWrite.full, overwrite = true)
                            }
                            when (metaResp) {
                                is ApiResult.Success -> dataset = dataset.copy(scientificMetadata = metaResp.data)
                                is ApiResult.Error -> {
                                    repository.cacheResource(uuid, dataset)
                                    _saveState.value = SaveState.Error("Saved, but metadata failed to save (${metaResp.code})")
                                    return@launch
                                }
                            }
                        }
                        repository.cacheResource(uuid, dataset)
                        _saveState.value = SaveState.Success(uuid)
                    }
                    is ApiResult.Error -> _saveState.value = SaveState.Error("Save failed (${resp.code})")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error — check your network")
            }
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }
}
