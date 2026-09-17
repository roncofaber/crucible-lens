package crucible.lens.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ProjectCreateRequest
import crucible.lens.data.model.ReassignProjectResponse
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.User
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.ui.common.MetadataWrite
import kotlinx.serialization.json.JsonObject
import crucible.lens.data.upload.DatasetFileUploadRequest
import crucible.lens.data.upload.DatasetFileUploadResult
import crucible.lens.data.upload.DatasetFileUploader
import crucible.lens.data.upload.DatasetFileAttachment
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

    fun create(request: SampleCreateRequest, metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val writeEpoch = repository.captureCacheEpoch()
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.createSample(request)) {
                    is ApiResult.Success -> {
                        var sample = resp.data
                        repository.cacheResource(sample.uniqueId, sample, writeEpoch)
                        sample.resolvedProjectId?.let { repository.invalidateProjectData(it, writeEpoch) }
                        var metadataWarning: String? = null
                        if (!metadata.isNullOrEmpty()) {
                            when (val metaResp = apiClient.service.postResourceMetadata(sample.uniqueId, metadata)) {
                                is ApiResult.Success -> {
                                    sample = sample.copy(scientificMetadata = metaResp.data)
                                    repository.cacheResource(sample.uniqueId, sample, writeEpoch)
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

    private val fileUploader = DatasetFileUploader(apiClient)

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun create(request: DatasetCreateRequest, files: List<DatasetFileAttachment> = emptyList(), metadata: JsonObject? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val writeEpoch = repository.captureCacheEpoch()
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
                repository.cacheResource(newUuid, newDataset, writeEpoch)
                newDataset.resolvedProjectId?.let { repository.invalidateProjectData(it, writeEpoch) }

                val fileFailures = mutableListOf<DatasetFileUploadResult.Failure>()
                files.forEach { file ->
                    val sha256 = PlatformCrypto.sha256Hex(file.bytes)
                    val result = fileUploader.upload(
                        DatasetFileUploadRequest(
                            datasetUuid = newUuid,
                            filename = file.filename,
                            bytes = file.bytes,
                            sha256 = sha256,
                            asThumbnail = file.asThumbnail,
                            thumbnailBase64 = if (file.asThumbnail) PlatformBase64.encode(file.bytes) else ""
                        )
                    )
                    if (result is DatasetFileUploadResult.Failure) {
                        fileFailures += result
                    }
                }

                var metadataFailed = false
                if (!metadata.isNullOrEmpty()) {
                    when (val metaResp = apiClient.service.postResourceMetadata(newUuid, metadata)) {
                        is ApiResult.Success -> {
                            newDataset = newDataset.copy(scientificMetadata = metaResp.data)
                            repository.cacheResource(newUuid, newDataset, writeEpoch)
                        }
                        is ApiResult.Error -> metadataFailed = true
                    }
                }

                val problems = buildList {
                    if (fileFailures.isNotEmpty()) {
                        val stages = fileFailures.map { it.stage.displayName }.distinct().joinToString()
                        add("${fileFailures.size} file operation(s) failed during $stages")
                    }
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
    val isLeadSearching: Boolean = false,
    val leadSearchError: String? = null
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
        _formState.value = _formState.value.copy(
            leadUsername = value,
            leadSearch = emptyList(),
            isLeadSearching = false,
            leadSearchError = null
        )
        if (value.length < SEARCH_MIN_QUERY_LENGTH) return
        searchLead(value)
    }

    fun retryLeadSearch() {
        val value = _formState.value.leadUsername
        if (value.length < SEARCH_MIN_QUERY_LENGTH) return
        leadSearchJob?.cancel()
        searchLead(value)
    }

    private fun searchLead(value: String) {
        _formState.value = _formState.value.copy(isLeadSearching = true)
        leadSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            try {
                when (val result = apiClient.service.searchUsers(value)) {
                    is ApiResult.Success -> _formState.value = _formState.value.copy(
                        leadSearch = result.data,
                        isLeadSearching = false,
                        leadSearchError = null
                    )
                    is ApiResult.Error -> _formState.value = _formState.value.copy(
                        leadSearch = emptyList(),
                        isLeadSearching = false,
                        leadSearchError = "Search failed (${result.code})"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.value = _formState.value.copy(
                    leadSearch = emptyList(),
                    isLeadSearching = false,
                    leadSearchError = "Connection error. Check your network and try again"
                )
            }
        }
    }

    fun selectLeadUser(user: User) {
        leadSearchJob?.cancel()
        // Keeps the picked user as a singleton list, not emptyList() - SearchPickerField derives
        // "resolved" by matching the current query against `results`, so clearing it would
        // immediately un-resolve the field right after picking.
        _formState.value = _formState.value.copy(
            leadUsername = user.username ?: "",
            leadSearch = listOf(user),
            isLeadSearching = false,
            leadSearchError = null
        )
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
                        _saveState.value = SaveState.Success(resp.data.uniqueId)
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

sealed class ProjectMoveState {
    data object Idle : ProjectMoveState()
    data class Previewing(val projectId: String) : ProjectMoveState()
    data class PreviewReady(val preview: ReassignProjectResponse) : ProjectMoveState()
    data class Moving(val preview: ReassignProjectResponse) : ProjectMoveState()
    data class Success(val result: ReassignProjectResponse) : ProjectMoveState()
    data class Error(val message: String) : ProjectMoveState()
}

class EditResourceViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    private val _projectMoveState = MutableStateFlow<ProjectMoveState>(ProjectMoveState.Idle)
    val projectMoveState: StateFlow<ProjectMoveState> = _projectMoveState.asStateFlow()

    // metadataWrite: null = unchanged or blank, skip the API call. Merge -> PATCH (only the
    // changed keys, so a concurrent edit to any other key survives). Replace -> POST ?overwrite=true
    // (only needed when a key was deleted in the editor, since PATCH can't express a delete).
    fun updateSample(uuid: String, request: SampleUpdateRequest, metadataWrite: MetadataWrite? = null) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val writeEpoch = repository.captureCacheEpoch()
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
                                    repository.cacheResource(uuid, sample, writeEpoch)
                                    _saveState.value = SaveState.Error("Saved, but metadata failed to save (${metaResp.code})")
                                    return@launch
                                }
                            }
                        }
                        repository.cacheResource(uuid, sample, writeEpoch)
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

    fun updateDataset(
        uuid: String,
        request: DatasetUpdateRequest,
        metadataWrite: MetadataWrite? = null,
        newInstrumentMfid: String? = null,
        previousInstrumentMfid: String? = null
    ) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        val writeEpoch = repository.captureCacheEpoch()
        viewModelScope.launch {
            try {
                when (val resp = apiClient.service.updateDataset(uuid, request)) {
                    is ApiResult.Success -> {
                        var dataset = resp.data
                        if (newInstrumentMfid != null) {
                            when (val assignment = apiClient.service.assignDatasetInstrument(uuid, newInstrumentMfid)) {
                                is ApiResult.Success -> {
                                    val instrument = assignment.data.instrument
                                    dataset = dataset.copy(
                                        instrument = instrument,
                                        instrumentId = instrument.instrumentId,
                                        instrumentName = instrument.instrumentName
                                    )
                                    previousInstrumentMfid?.let(repository::invalidateInstrumentDatasets)
                                    repository.invalidateInstrumentDatasets(newInstrumentMfid)
                                    dataset.resolvedProjectId?.let { repository.invalidateProjectData(it, writeEpoch) }
                                }
                                is ApiResult.Error -> {
                                    repository.cacheResource(uuid, dataset, writeEpoch)
                                    _saveState.value = SaveState.Error("Saved, but instrument reassignment failed (${assignment.code})")
                                    return@launch
                                }
                            }
                        }
                        if (metadataWrite != null) {
                            val metaResp = when (metadataWrite) {
                                is MetadataWrite.Merge -> apiClient.service.patchResourceMetadata(uuid, metadataWrite.updates)
                                is MetadataWrite.Replace -> apiClient.service.postResourceMetadata(uuid, metadataWrite.full, overwrite = true)
                            }
                            when (metaResp) {
                                is ApiResult.Success -> dataset = dataset.copy(scientificMetadata = metaResp.data)
                                is ApiResult.Error -> {
                                    repository.cacheResource(uuid, dataset, writeEpoch)
                                    _saveState.value = SaveState.Error("Saved, but metadata failed to save (${metaResp.code})")
                                    return@launch
                                }
                            }
                        }
                        repository.cacheResource(uuid, dataset, writeEpoch)
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

    fun previewProjectMove(uuid: String, projectId: String) {
        if (_projectMoveState.value !is ProjectMoveState.Idle) return
        _projectMoveState.value = ProjectMoveState.Previewing(projectId)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.reassignResourceProject(uuid, projectId)) {
                    is ApiResult.Success -> _projectMoveState.value = ProjectMoveState.PreviewReady(result.data)
                    is ApiResult.Error -> _projectMoveState.value = ProjectMoveState.Error(projectMoveError(result.code))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _projectMoveState.value = ProjectMoveState.Error("Connection error, check your network")
            }
        }
    }

    fun confirmProjectMove(uuid: String) {
        val preview = (_projectMoveState.value as? ProjectMoveState.PreviewReady)?.preview ?: return
        _projectMoveState.value = ProjectMoveState.Moving(preview)
        val writeEpoch = repository.captureCacheEpoch()
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.reassignResourceProject(uuid, preview.newProjectId, confirm = true)) {
                    is ApiResult.Success -> {
                        val moved = when (val resource = repository.getCachedResource(uuid)) {
                            is Sample -> resource.copy(projectId = result.data.newProjectId, project = null)
                            is Dataset -> resource.copy(projectId = result.data.newProjectId, project = null)
                            else -> null
                        }
                        if (moved != null) repository.cacheResource(uuid, moved, writeEpoch)
                        result.data.previousProjectId?.let { repository.invalidateProjectData(it, writeEpoch) }
                        repository.invalidateProjectData(result.data.newProjectId, writeEpoch)
                        _projectMoveState.value = ProjectMoveState.Success(result.data)
                    }
                    is ApiResult.Error -> _projectMoveState.value = ProjectMoveState.Error(projectMoveError(result.code))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _projectMoveState.value = ProjectMoveState.Error("Connection error, check your network")
            }
        }
    }

    fun resetProjectMoveState() {
        if (_projectMoveState.value !is ProjectMoveState.Moving) {
            _projectMoveState.value = ProjectMoveState.Idle
        }
    }

    fun resetState() { _saveState.value = SaveState.Idle }

    private fun projectMoveError(code: Int): String = when (code) {
        403 -> "You do not have permission to move this resource"
        404 -> "The selected project is no longer available"
        409 -> "The project cannot accept this resource"
        422 -> "The selected project is invalid"
        else -> "Could not move the resource ($code)"
    }
}
