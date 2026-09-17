package crucible.lens.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ResourceSearchResult
import crucible.lens.data.model.DatasetFacetField
import crucible.lens.data.model.SampleFacetField
import crucible.lens.data.model.User
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.model.resolvedProjectName
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.util.SearchCoverage
import crucible.lens.data.util.SearchEndpointCategory
import crucible.lens.data.util.searchCoverage
import crucible.lens.data.util.toUtcQueryTimestamp
import crucible.lens.ui.common.FacetSuggestions
import crucible.lens.ui.common.SearchFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val metadataMode: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    val resourceResults: List<ResourceSearchResult> = emptyList(),
    val userResults: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
    val facetSuggestions: FacetSuggestions = FacetSuggestions(),
    val isLoadingFacets: Boolean = false,
    val facetError: String? = null
)

private data class SearchCriteria(
    val query: String = "",
    val metadataMode: Boolean = false,
    val filters: SearchFilters = SearchFilters(),
    val retry: Int = 0
)

private data class SearchRequestKey(
    val query: String,
    val metadataMode: Boolean,
    val filters: SearchFilters,
    val peopleLimit: Int,
    val projectLimit: Int,
    val retry: Int
)

class SearchViewModel(
    private val apiClient: ApiClient,
    preferences: AppPreferences
) : ViewModel() {
    private val criteria = MutableStateFlow(SearchCriteria())
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                criteria,
                preferences.peopleResultLimit,
                preferences.projectResultLimit
            ) { current, peopleLimit, projectLimit -> Triple(current, peopleLimit, projectLimit) }
                .distinctUntilChangedBy { (current, peopleLimit, projectLimit) ->
                    val usesNameLimits = !current.metadataMode && !current.filters.isActive
                    SearchRequestKey(
                        query = if (current.filters.isActive && !current.metadataMode) "" else current.query.trim(),
                        metadataMode = current.metadataMode,
                        filters = current.filters,
                        peopleLimit = if (usesNameLimits) peopleLimit else 0,
                        projectLimit = if (usesNameLimits) projectLimit else 0,
                        retry = current.retry
                    )
                }
                .collectLatest { (current, peopleLimit, projectLimit) ->
                    search(current, peopleLimit, projectLimit)
                }
        }
    }

    fun updateQuery(value: String) {
        val current = criteria.value
        if (current.filters.isActive && !current.metadataMode) {
            criteria.value = current.copy(query = value)
            _state.update { it.copy(query = value) }
        } else {
            updateCriteria(current.copy(query = value))
        }
    }

    fun setMetadataMode(enabled: Boolean) {
        updateCriteria(criteria.value.copy(metadataMode = enabled))
    }

    fun setFilters(filters: SearchFilters) {
        updateCriteria(criteria.value.copy(filters = filters))
    }

    fun retry() {
        val current = criteria.value
        criteria.value = current.copy(retry = current.retry + 1)
        _state.update { it.copy(isLoading = true, error = null, warning = null) }
    }

    fun loadFacetSuggestions(forceRefresh: Boolean = false) {
        val current = _state.value
        if (current.isLoadingFacets) return
        if (!forceRefresh && current.facetSuggestions != FacetSuggestions()) return
        _state.update { it.copy(isLoadingFacets = true, facetError = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val measurements = async { apiClient.service.getDatasetFacetValues(DatasetFacetField.Measurement) }
                    val formats = async { apiClient.service.getDatasetFacetValues(DatasetFacetField.DataFormat) }
                    val sessions = async { apiClient.service.getDatasetFacetValues(DatasetFacetField.Session) }
                    val sampleTypes = async { apiClient.service.getSampleFacetValues(SampleFacetField.SampleType) }
                    val responses = listOf(measurements.await(), formats.await(), sessions.await(), sampleTypes.await())
                    fun values(index: Int) = (responses[index] as? ApiResult.Success)
                        ?.data
                        .orEmpty()
                        .mapNotNull { it.value }
                        .distinct()
                    _state.update {
                        it.copy(
                            facetSuggestions = FacetSuggestions(
                                measurements = values(0),
                                dataFormats = values(1),
                                sessionNames = values(2),
                                sampleTypes = values(3)
                            ),
                            isLoadingFacets = false,
                            facetError = if (responses.any { response -> response is ApiResult.Error }) {
                                "Some suggestions could not be loaded"
                            } else null
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingFacets = false, facetError = "Could not load suggestions") }
            }
        }
    }

    private fun updateCriteria(updated: SearchCriteria) {
        criteria.value = updated
        val shouldSearch = shouldSearch(updated)
        _state.value = SearchUiState(
            query = updated.query,
            metadataMode = updated.metadataMode,
            filters = updated.filters,
            isLoading = shouldSearch,
            facetSuggestions = _state.value.facetSuggestions,
            isLoadingFacets = _state.value.isLoadingFacets,
            facetError = _state.value.facetError
        )
    }

    private suspend fun search(
        current: SearchCriteria,
        peopleLimit: Int,
        projectLimit: Int
    ) {
        if (!shouldSearch(current)) {
            _state.value = SearchUiState(
                query = current.query,
                metadataMode = current.metadataMode,
                filters = current.filters,
                facetSuggestions = _state.value.facetSuggestions,
                isLoadingFacets = _state.value.isLoadingFacets,
                facetError = _state.value.facetError
            )
            return
        }

        _state.update {
            it.copy(
                query = current.query,
                metadataMode = current.metadataMode,
                filters = current.filters,
                isLoading = true,
                error = null,
                warning = null
            )
        }
        if (!current.filters.isActive) delay(350)

        try {
            when {
                current.metadataMode -> searchMetadata(current)
                current.filters.isActive -> searchFilters(current)
                else -> searchNames(current, peopleLimit, projectLimit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    resourceResults = emptyList(),
                    userResults = emptyList(),
                    isLoading = false,
                    hasSearched = true,
                    error = "Connection error. Check your network and try again"
                )
            }
        }
    }

    private suspend fun searchNames(
        current: SearchCriteria,
        peopleLimit: Int,
        projectLimit: Int
    ) = coroutineScope {
        val query = current.query.trim()
        val samplesRequest = async { apiClient.service.searchSamples(query) }
        val datasetsRequest = async { apiClient.service.searchDatasets(query) }
        val projectsRequest = async { apiClient.service.searchProjects(query, limit = projectLimit) }
        val usersRequest = async { apiClient.service.searchUsers(query, limit = peopleLimit) }
        val samples = samplesRequest.await()
        val datasets = datasetsRequest.await()
        val projects = projectsRequest.await()
        val users = usersRequest.await()

        val failures = buildSet {
            if (samples is ApiResult.Error) add(SearchEndpointCategory.SAMPLES)
            if (datasets is ApiResult.Error) add(SearchEndpointCategory.DATASETS)
            if (projects is ApiResult.Error) add(SearchEndpointCategory.PROJECTS)
            if (users is ApiResult.Error) add(SearchEndpointCategory.PEOPLE)
        }
        val coverage = searchCoverage(SearchEndpointCategory.entries.toSet(), failures)
        val resources = (projects as? ApiResult.Success)?.data.orEmpty().map {
            ResourceSearchResult(it.projectId, "project", it.title ?: it.projectId)
        } + (samples as? ApiResult.Success)?.data.orEmpty().map {
            ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.resolvedProjectId, projectLabel = it.resolvedProjectName)
        } + (datasets as? ApiResult.Success)?.data.orEmpty().map {
            ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.resolvedProjectId, projectLabel = it.resolvedProjectName)
        }

        publish(
            current = current,
            resources = resources,
            users = (users as? ApiResult.Success)?.data.orEmpty(),
            coverage = coverage
        )
    }

    private suspend fun searchFilters(current: SearchCriteria) = coroutineScope {
        val filters = current.filters
        val after = toUtcQueryTimestamp(filters.createdAfter)
        val before = toUtcQueryTimestamp(filters.createdBefore)
        val projectId = filters.projectId.ifBlank { null }
        val ownerId = filters.ownerId.ifBlank { null }
        val samplesRequest = async {
            apiClient.service.getFilteredSamples(
                projectId = projectId,
                sampleType = filters.sampleType.ifBlank { null },
                ownerId = ownerId,
                creationTimeGte = after,
                creationTimeLte = before
            )
        }
        val datasetsRequest = async {
            apiClient.service.getFilteredDatasets(
                projectId = projectId,
                measurement = filters.measurement.ifBlank { null },
                instrumentName = filters.instrumentName.ifBlank { null },
                dataFormat = filters.dataFormat.ifBlank { null },
                sessionName = filters.sessionName.ifBlank { null },
                ownerId = ownerId,
                creationTimeGte = after,
                creationTimeLte = before
            )
        }
        val samples = samplesRequest.await()
        val datasets = datasetsRequest.await()
        val failures = buildSet {
            if (samples is ApiResult.Error) add(SearchEndpointCategory.SAMPLES)
            if (datasets is ApiResult.Error) add(SearchEndpointCategory.DATASETS)
        }
        val requested = setOf(SearchEndpointCategory.SAMPLES, SearchEndpointCategory.DATASETS)
        val resources = (samples as? ApiResult.Success)?.data.orEmpty().map {
            ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.resolvedProjectId, projectLabel = it.resolvedProjectName)
        } + (datasets as? ApiResult.Success)?.data.orEmpty().map {
            ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.resolvedProjectId, projectLabel = it.resolvedProjectName)
        }
        publish(current, resources, emptyList(), searchCoverage(requested, failures))
    }

    private suspend fun searchMetadata(current: SearchCriteria) {
        when (val result = apiClient.service.searchScientificMetadata(current.query.trim())) {
            is ApiResult.Success -> publish(
                current,
                result.data,
                emptyList(),
                SearchCoverage.Complete
            )
            is ApiResult.Error -> _state.update {
                it.copy(
                    resourceResults = emptyList(),
                    userResults = emptyList(),
                    isLoading = false,
                    hasSearched = true,
                    error = "Metadata search failed (${result.code})"
                )
            }
        }
    }

    private fun publish(
        current: SearchCriteria,
        resources: List<ResourceSearchResult>,
        users: List<User>,
        coverage: SearchCoverage
    ) {
        _state.update {
            it.copy(
                query = current.query,
                metadataMode = current.metadataMode,
                filters = current.filters,
                resourceResults = resources,
                userResults = users,
                isLoading = false,
                hasSearched = true,
                error = if (coverage is SearchCoverage.Failed) "Search failed. Try again" else null,
                warning = (coverage as? SearchCoverage.Partial)?.message
            )
        }
    }

    private fun shouldSearch(current: SearchCriteria): Boolean = when {
        current.metadataMode -> current.query.trim().length >= 3
        current.filters.isActive -> true
        else -> current.query.trim().length >= 3
    }
}
