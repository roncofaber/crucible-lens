package crucible.lens.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ProjectScope
import crucible.lens.data.model.Sample
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.model.resolvedProjectReference
import crucible.lens.data.util.isMfidReference
import crucible.lens.data.util.ResourceLinkDirection
import crucible.lens.data.util.ResourceLinkOperation
import crucible.lens.data.util.SEARCH_DEBOUNCE_MS
import crucible.lens.data.util.SEARCH_MIN_QUERY_LENGTH
import crucible.lens.data.util.resourceLinkOperation
import crucible.lens.ui.navigation.DeepLinkTarget
import crucible.lens.ui.navigation.parseScannedTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed class LinkSearchState {
    object Idle : LinkSearchState()
    object Searching : LinkSearchState()
    data class Results(
        val resources: List<CrucibleResource>,
        val warning: String? = null
    ) : LinkSearchState()
    data class Error(val message: String) : LinkSearchState()
}

internal sealed class LinkResolutionState {
    object Idle : LinkResolutionState()
    object Resolving : LinkResolutionState()
    data class Resolved(val resource: CrucibleResource) : LinkResolutionState()
    object NotFound : LinkResolutionState()
    data class Error(val message: String) : LinkResolutionState()
}

internal sealed class LinkSubmissionState {
    object Idle : LinkSubmissionState()
    object Submitting : LinkSubmissionState()
    object Submitted : LinkSubmissionState()
    data class Error(val message: String) : LinkSubmissionState()
}

internal data class LinkResourceUiState(
    val input: String = "",
    val search: LinkSearchState = LinkSearchState.Idle,
    val resolution: LinkResolutionState = LinkResolutionState.Idle,
    val submission: LinkSubmissionState = LinkSubmissionState.Idle
)

internal data class LinkProjectSearchContext(
    val projectId: String?,
    val projectMfid: String?,
    val projectScope: ProjectScope?
)

internal fun CrucibleResource.linkProjectSearchContext(): LinkProjectSearchContext {
    val projectReference = when (this) {
        is Sample -> resolvedProjectReference
        is Dataset -> resolvedProjectReference
    }
    val projectMfid = projectReference?.takeIf(::isMfidReference)
    val projectId = if (projectMfid == null) when (this) {
        is Sample -> resolvedProjectId
        is Dataset -> resolvedProjectId
    } else null
    return LinkProjectSearchContext(
        projectId = projectId,
        projectMfid = projectMfid,
        projectScope = if (projectMfid != null || projectId != null) ProjectScope.Assigned else null
    )
}

internal class LinkResourceViewModel(
    private val apiClient: ApiClient
) : ViewModel() {
    private val _state = MutableStateFlow(LinkResourceUiState())
    val state: StateFlow<LinkResourceUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null
    private var submissionJob: Job? = null
    private var sourceResourceId: String? = null

    fun start(resourceId: String) {
        if (sourceResourceId == resourceId) return
        reset()
        sourceResourceId = resourceId
    }

    fun updateInput(value: String, current: CrucibleResource) {
        if (_state.value.submission is LinkSubmissionState.Submitting) return
        lookupJob?.cancel()
        val query = value.trim()
        _state.value = LinkResourceUiState(input = value)

        when {
            query.length >= 10 && !query.contains(' ') -> resolve(query, current)
            query.length >= SEARCH_MIN_QUERY_LENGTH -> search(query, current)
        }
    }

    fun updateScannedInput(value: String, current: CrucibleResource) {
        when (val target = parseScannedTarget(value)) {
            is DeepLinkTarget.Resource -> updateInput(target.resourceReference, current)
            is DeepLinkTarget.Project -> {
                lookupJob?.cancel()
                _state.value = LinkResourceUiState(
                    input = value,
                    resolution = LinkResolutionState.Error("Scan a sample or dataset code")
                )
            }
            null -> {
                lookupJob?.cancel()
                _state.value = LinkResourceUiState(
                    input = value,
                    resolution = LinkResolutionState.Error("This is not a recognized Crucible resource code")
                )
            }
        }
    }

    fun selectResource(resource: CrucibleResource) {
        if (_state.value.submission is LinkSubmissionState.Submitting) return
        lookupJob?.cancel()
        _state.value = LinkResourceUiState(
            input = resource.uniqueId,
            resolution = LinkResolutionState.Resolved(resource)
        )
    }

    fun retryLookup(current: CrucibleResource) {
        updateInput(_state.value.input, current)
    }

    fun submit(current: CrucibleResource, direction: ResourceLinkDirection) {
        if (_state.value.submission is LinkSubmissionState.Submitting) return
        val target = (_state.value.resolution as? LinkResolutionState.Resolved)?.resource ?: return
        val operation = resourceLinkOperation(current, target, direction)
        _state.value = _state.value.copy(submission = LinkSubmissionState.Submitting)
        submissionJob = viewModelScope.launch {
            try {
                val result = when (operation) {
                    is ResourceLinkOperation.Samples -> apiClient.service.linkSamples(
                        operation.parentUuid,
                        operation.childUuid
                    )
                    is ResourceLinkOperation.Datasets -> apiClient.service.linkDatasets(
                        operation.parentUuid,
                        operation.childUuid
                    )
                    is ResourceLinkOperation.DatasetSample -> apiClient.service.linkDatasetSample(
                        operation.datasetUuid,
                        operation.sampleUuid
                    )
                }
                _state.value = _state.value.copy(
                    submission = when (result) {
                        is ApiResult.Success -> LinkSubmissionState.Submitted
                        is ApiResult.Error -> LinkSubmissionState.Error(
                            when (result.code) {
                                400 -> "This relationship is not valid"
                                403 -> "You do not have permission to link these resources"
                                404 -> "One of these resources could not be found"
                                409 -> "These resources may already be linked"
                                else -> "Could not link resources (${result.code})"
                            }
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    submission = LinkSubmissionState.Error(
                        "Connection error. Check your network and try again"
                    )
                )
            }
        }
    }

    fun reset() {
        lookupJob?.cancel()
        submissionJob?.cancel()
        lookupJob = null
        submissionJob = null
        sourceResourceId = null
        _state.value = LinkResourceUiState()
    }

    private fun search(query: String, current: CrucibleResource) {
        val project = current.linkProjectSearchContext()
        lookupJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (_state.value.input.trim() != query) return@launch
            _state.value = _state.value.copy(search = LinkSearchState.Searching)
            try {
                val samples = apiClient.service.searchSamples(
                    q = query,
                    projectId = project.projectId,
                    projectMfid = project.projectMfid,
                    projectScope = project.projectScope,
                    limit = 6
                )
                val datasets = apiClient.service.searchDatasets(
                    q = query,
                    projectId = project.projectId,
                    projectMfid = project.projectMfid,
                    projectScope = project.projectScope,
                    limit = 6
                )
                if (_state.value.input.trim() != query) return@launch
                val resources = listOfNotNull(
                    (samples as? ApiResult.Success)?.data,
                    (datasets as? ApiResult.Success)?.data
                ).flatten().filter { it.uniqueId != current.uniqueId }.take(6)
                _state.value = _state.value.copy(
                    search = when {
                        samples is ApiResult.Error && datasets is ApiResult.Error -> {
                            LinkSearchState.Error("Could not search resources")
                        }
                        samples is ApiResult.Error || datasets is ApiResult.Error -> {
                            LinkSearchState.Results(resources, "Some results could not be loaded")
                        }
                        else -> LinkSearchState.Results(resources)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.input.trim() == query) {
                    _state.value = _state.value.copy(
                        search = LinkSearchState.Error("Connection error. Check your network and try again")
                    )
                }
            }
        }
    }

    private fun resolve(query: String, current: CrucibleResource) {
        _state.value = _state.value.copy(resolution = LinkResolutionState.Resolving)
        lookupJob = viewModelScope.launch {
            try {
                when (val result = apiClient.service.getResource(
                    uuid = query,
                    includeLinks = false,
                    includeDatasets = false,
                    includeMetadata = false,
                    includeOwner = false
                )) {
                    is ApiResult.Success -> {
                        if (_state.value.input.trim() != query) return@launch
                        _state.value = _state.value.copy(
                            resolution = if (result.data.uniqueId == current.uniqueId) {
                                LinkResolutionState.Error("A resource cannot be linked to itself")
                            } else {
                                LinkResolutionState.Resolved(result.data)
                            }
                        )
                    }
                    is ApiResult.Error -> {
                        if (_state.value.input.trim() != query) return@launch
                        _state.value = _state.value.copy(
                            resolution = if (result.code == 404) {
                                LinkResolutionState.NotFound
                            } else {
                                LinkResolutionState.Error("Could not look up resource (${result.code})")
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.input.trim() == query) {
                    _state.value = _state.value.copy(
                        resolution = LinkResolutionState.Error(
                            "Connection error. Check your network and try again"
                        )
                    )
                }
            }
        }
    }
}
