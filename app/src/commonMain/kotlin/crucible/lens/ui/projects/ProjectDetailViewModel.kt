package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectContent(val samples: List<Sample>, val datasets: List<Dataset>)

class ProjectDetailViewModel(
    private val repository: CrucibleRepository
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

                val cachedSamples = repository.getCachedProjectSamples(projectId)
                val cachedDatasets = repository.getCachedProjectDatasets(projectId)

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

                val (samples, datasets) = repository.fetchProjectData(projectId, forceRefresh = forceRefresh)
                _loadState.value = LoadState.Success(ProjectContent(samples, datasets))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
