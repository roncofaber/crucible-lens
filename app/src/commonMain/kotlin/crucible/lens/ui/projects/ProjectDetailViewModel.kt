package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectContent(val samples: List<Sample>, val datasets: List<Dataset>)

class ProjectDetailViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<ProjectContent>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<ProjectContent>> = _loadState.asStateFlow()

    private var currentProjectId: String? = null

    fun load(projectId: String, isHidden: Boolean = false, forceRefresh: Boolean = false) {
        if (projectId == currentProjectId && !forceRefresh &&
            _loadState.value is LoadState.Success) return
        currentProjectId = projectId
        viewModelScope.launch {
            try {
                val current = (_loadState.value as? LoadState.Success)?.data
                _loadState.value = if (forceRefresh && current != null)
                    LoadState.Success(current, isRefreshing = true)
                else LoadState.Loading

                val cachedSamples = cacheManager.getProjectSamples(projectId)
                val cachedDatasets = cacheManager.getProjectDatasets(projectId)

                if (cachedSamples != null && cachedDatasets != null && !forceRefresh) {
                    _loadState.value = LoadState.Success(
                        ProjectContent(cachedSamples, cachedDatasets), fromCache = true
                    )
                    return@launch
                }

                if (isHidden) {
                    val fromCache = cachedSamples != null && cachedDatasets != null
                    _loadState.value = LoadState.Success(
                        ProjectContent(cachedSamples ?: emptyList(), cachedDatasets ?: emptyList()),
                        fromCache = fromCache
                    )
                    return@launch
                }

                val (samplesResp, datasetsResp) = coroutineScope {
                    val s = async { apiClient.service.getSamplesByProject(projectId) }
                    val d = async { apiClient.service.getDatasetsByProject(projectId) }
                    s.await() to d.await()
                }
                val samples = (samplesResp as? ApiResult.Success)?.data
                val datasets = (datasetsResp as? ApiResult.Success)?.data

                if (samples != null && datasets != null) {
                    cacheManager.cacheProjectSamples(projectId, samples)
                    cacheManager.cacheProjectDatasets(projectId, datasets)
                    _loadState.value = LoadState.Success(ProjectContent(samples, datasets))
                } else {
                    _loadState.value = LoadState.Error("Failed to load project data")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
