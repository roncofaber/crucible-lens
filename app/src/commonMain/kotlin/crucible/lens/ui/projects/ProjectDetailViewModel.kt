package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.ProjectScope
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.sync.DataSyncManager
import crucible.lens.data.sync.ProjectSyncTarget
import crucible.lens.platform.PlatformContext
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectContent(
    val samples: List<Sample>,
    val datasets: List<Dataset>,
    val sharedSampleIds: Set<String> = emptySet(),
    val sharedDatasetIds: Set<String> = emptySet()
)

internal fun mergeProjectContent(assigned: ProjectContent, shared: ProjectContent): ProjectContent {
    val assignedSampleIds = assigned.samples.mapTo(mutableSetOf()) { it.uniqueId }
    val assignedDatasetIds = assigned.datasets.mapTo(mutableSetOf()) { it.uniqueId }
    return ProjectContent(
        samples = (assigned.samples + shared.samples).distinctBy { it.uniqueId },
        datasets = (assigned.datasets + shared.datasets).distinctBy { it.uniqueId },
        sharedSampleIds = shared.samples.mapTo(mutableSetOf()) { it.uniqueId } - assignedSampleIds,
        sharedDatasetIds = shared.datasets.mapTo(mutableSetOf()) { it.uniqueId } - assignedDatasetIds
    )
}

internal fun mergeProjectLoadStates(
    assigned: LoadState<ProjectContent>,
    shared: LoadState<ProjectContent>?
): LoadState<ProjectContent> = when {
    assigned is LoadState.Success && shared is LoadState.Success -> LoadState.Success(
        data = mergeProjectContent(assigned.data, shared.data),
        isRefreshing = assigned.isRefreshing || shared.isRefreshing,
        fromCache = assigned.fromCache,
        refreshError = assigned.refreshError ?: shared.refreshError
    )
    assigned is LoadState.Success && shared is LoadState.Error -> assigned.copy(refreshError = shared.message)
    assigned is LoadState.Error && shared is LoadState.Success -> shared.copy(refreshError = assigned.message)
    assigned is LoadState.Success -> assigned
    shared is LoadState.Success -> shared
    assigned is LoadState.Error && shared is LoadState.Error -> LoadState.Error("${assigned.message}. ${shared.message}")
    assigned is LoadState.Error -> assigned
    shared is LoadState.Error -> shared
    else -> LoadState.Loading
}

sealed class ProjectJoinRequestState {
    object Idle : ProjectJoinRequestState()
    object Loading : ProjectJoinRequestState()
    data class Ready(val request: JoinRequest?, val refreshError: String? = null) : ProjectJoinRequestState()
    data class Error(val message: String) : ProjectJoinRequestState()
}

sealed class JoinRequestSubmissionState {
    object Idle : JoinRequestSubmissionState()
    object Submitting : JoinRequestSubmissionState()
    object Submitted : JoinRequestSubmissionState()
    data class Error(val message: String) : JoinRequestSubmissionState()
}

private fun projectScopeError(code: Int): String = when (code) {
    403 -> "You no longer have permission to inspect resources shared with this project"
    404 -> "This project could not be found"
    409 -> "This project is not ready to receive shared resources"
    else -> "Could not load shared resources ($code)"
}

internal fun sharedProjectContentState(
    sampleResult: ApiResult<List<Sample>>,
    datasetResult: ApiResult<List<Dataset>>,
    previous: LoadState.Success<ProjectContent>? = null
): LoadState<ProjectContent> {
    val errors = buildList {
        if (sampleResult is ApiResult.Error) add(projectScopeError(sampleResult.code))
        if (datasetResult is ApiResult.Error) add(projectScopeError(datasetResult.code))
    }.distinct()
    if (sampleResult is ApiResult.Error && datasetResult is ApiResult.Error) {
        return projectScopeFailureState(previous, errors.joinToString(". "))
    }
    val samples = when (sampleResult) {
        is ApiResult.Success -> sampleResult.data
        is ApiResult.Error -> previous?.data?.samples.orEmpty()
    }
    val datasets = when (datasetResult) {
        is ApiResult.Success -> datasetResult.data
        is ApiResult.Error -> previous?.data?.datasets.orEmpty()
    }
    return LoadState.Success(
        data = ProjectContent(
            samples = samples,
            datasets = datasets,
            sharedSampleIds = samples.mapTo(mutableSetOf()) { it.uniqueId },
            sharedDatasetIds = datasets.mapTo(mutableSetOf()) { it.uniqueId }
        ),
        refreshError = errors.takeIf { it.isNotEmpty() }?.joinToString(". ")
    )
}

class ProjectDetailViewModel(
    private val repository: CrucibleRepository,
    private val dataSyncManager: DataSyncManager,
    private val apiClient: ApiClient
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<ProjectContent>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<ProjectContent>> = _loadState.asStateFlow()

    private val _sharedLoadState = MutableStateFlow<LoadState<ProjectContent>?>(null)
    val sharedLoadState: StateFlow<LoadState<ProjectContent>?> = _sharedLoadState.asStateFlow()

    private val _joinRequestState = MutableStateFlow<ProjectJoinRequestState>(ProjectJoinRequestState.Idle)
    val joinRequestState: StateFlow<ProjectJoinRequestState> = _joinRequestState.asStateFlow()

    private val _joinRequestSubmissionState = MutableStateFlow<JoinRequestSubmissionState>(JoinRequestSubmissionState.Idle)
    val joinRequestSubmissionState: StateFlow<JoinRequestSubmissionState> = _joinRequestSubmissionState.asStateFlow()

    private var currentTarget: ProjectSyncTarget? = null
    private var currentAccountId: String? = null
    private var currentIsSynced = false
    private var joinRequestJob: Job? = null
    private var sharedLoadJob: Job? = null
    private var sharedProjectMfid: String? = null

    fun load(
        target: ProjectSyncTarget,
        context: PlatformContext,
        accountId: String?,
        isSynced: Boolean,
        forceRefresh: Boolean = false
    ) {
        if (target == currentTarget && accountId == currentAccountId && isSynced == currentIsSynced && !forceRefresh &&
            _loadState.value is LoadState.Success) return
        currentTarget = target
        currentAccountId = accountId
        currentIsSynced = isSynced
        viewModelScope.launch {
            try {
                if (isSynced && accountId != null && !repository.hasPersistedProjectData(target.projectMfid)) {
                    dataSyncManager.restoreProject(context, accountId, target.projectMfid)
                }
                val current = (_loadState.value as? LoadState.Success)?.data
                _loadState.value = if (forceRefresh && current != null)
                    LoadState.Success(current, isRefreshing = true)
                else LoadState.Loading

                val cachedSamples = repository.getCachedProjectSamples(target.projectSlug)
                val cachedDatasets = repository.getCachedProjectDatasets(target.projectSlug)

                if (cachedSamples != null && cachedDatasets != null && !forceRefresh) {
                    _loadState.value = LoadState.Success(
                        ProjectContent(cachedSamples, cachedDatasets),
                        fromCache = repository.hasPersistedProjectData(target.projectMfid)
                    )
                    if (!repository.hasPersistedProjectData(target.projectMfid)) return@launch
                }

                val (samples, datasets) = if (isSynced && accountId != null) {
                    dataSyncManager.syncProject(
                        context,
                        accountId,
                        target,
                        forceRefresh = forceRefresh || repository.hasPersistedProjectData(target.projectMfid)
                    ).let { it.samples to it.datasets }
                } else {
                    repository.fetchProjectData(target.projectMfid, target.projectSlug, forceRefresh = forceRefresh)
                }
                _loadState.value = LoadState.Success(ProjectContent(samples, datasets))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val samples = repository.getCachedProjectSamples(target.projectSlug)
                val datasets = repository.getCachedProjectDatasets(target.projectSlug)
                _loadState.value = if (samples != null && datasets != null) {
                    LoadState.Success(ProjectContent(samples, datasets), fromCache = true)
                } else {
                    LoadState.Error("Connection error - check your network")
                }
            }
        }
    }

    fun loadShared(projectMfid: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && sharedProjectMfid == projectMfid && _sharedLoadState.value is LoadState.Success) return
        sharedLoadJob?.cancel()
        if (sharedProjectMfid != projectMfid) _sharedLoadState.value = LoadState.Loading
        sharedProjectMfid = projectMfid
        sharedLoadJob = viewModelScope.launch {
            val previous = _sharedLoadState.value as? LoadState.Success
            _sharedLoadState.value = if (forceRefresh && previous != null) {
                previous.copy(isRefreshing = true, refreshError = null)
            } else {
                LoadState.Loading
            }
            try {
                val samples = async { apiClient.service.getSamplesByProject(projectMfid, ProjectScope.Shared) }
                val datasets = async { apiClient.service.getDatasetsByProject(projectMfid, ProjectScope.Shared) }
                val sampleResult = samples.await()
                val datasetResult = datasets.await()
                _sharedLoadState.value = sharedProjectContentState(sampleResult, datasetResult, previous)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _sharedLoadState.value = projectScopeFailureState(previous, "Connection error - check your network")
            }
        }
    }

    fun loadJoinRequestStatus(projectId: String, forceRefresh: Boolean = false) {
        joinRequestJob?.cancel()
        joinRequestJob = viewModelScope.launch {
            val cached = repository.getCachedMyJoinRequests()
            if (!forceRefresh && cached != null) {
                _joinRequestState.value = ProjectJoinRequestState.Ready(
                    cached.find { it.groupName == projectId && it.status == "pending" }
                )
                return@launch
            }
            val previous = _joinRequestState.value as? ProjectJoinRequestState.Ready
            if (previous == null) _joinRequestState.value = ProjectJoinRequestState.Loading
            try {
                when (val result = repository.fetchMyJoinRequests(forceRefresh = forceRefresh)) {
                    is ApiResult.Success -> _joinRequestState.value = ProjectJoinRequestState.Ready(
                        result.data.find { it.groupName == projectId && it.status == "pending" }
                    )
                    is ApiResult.Error -> _joinRequestState.value = if (previous != null) {
                        previous.copy(refreshError = "Could not refresh request status (${result.code})")
                    } else {
                        ProjectJoinRequestState.Error("Could not check request status (${result.code})")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _joinRequestState.value = if (previous != null) {
                    previous.copy(refreshError = "Connection error. Request status may be out of date")
                } else {
                    ProjectJoinRequestState.Error("Connection error. Check your network and try again")
                }
            }
        }
    }

    fun clearJoinRequestStatus() {
        joinRequestJob?.cancel()
        _joinRequestState.value = ProjectJoinRequestState.Idle
    }

    fun submitJoinRequest(projectId: String, reason: String) {
        if (_joinRequestSubmissionState.value is JoinRequestSubmissionState.Submitting) return
        _joinRequestSubmissionState.value = JoinRequestSubmissionState.Submitting
        val writeEpoch = repository.captureCacheEpoch()
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.requestToJoinProject(
                    projectId = projectId,
                    reason = reason.trim().ifBlank { null }
                )) {
                    is ApiResult.Success -> {
                        repository.updateCachedMyJoinRequest(result.data, writeEpoch)
                        _joinRequestState.value = ProjectJoinRequestState.Ready(result.data)
                        _joinRequestSubmissionState.value = JoinRequestSubmissionState.Submitted
                    }
                    is ApiResult.Error -> _joinRequestSubmissionState.value = JoinRequestSubmissionState.Error(
                        if (result.code == 409) {
                            "You already have a pending request, or are already a member"
                        } else {
                            "Could not submit join request (${result.code})"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _joinRequestSubmissionState.value = JoinRequestSubmissionState.Error(
                    "Connection error. Check your network and try again"
                )
            }
        }
    }

    fun clearJoinRequestSubmission() {
        if (_joinRequestSubmissionState.value is JoinRequestSubmissionState.Submitting) return
        _joinRequestSubmissionState.value = JoinRequestSubmissionState.Idle
    }
}

private fun projectScopeFailureState(
    previous: LoadState.Success<ProjectContent>?,
    message: String
): LoadState<ProjectContent> = previous?.copy(isRefreshing = false, refreshError = message)
    ?: LoadState.Error(message)
