package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Project
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsListViewModel : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Project>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Project>>> = _loadState.asStateFlow()

    /** Per-project sample/dataset counts populated by background preloading. */
    private val _projectCounts = MutableStateFlow<Map<String, Pair<Int?, Int?>>>(emptyMap())
    val projectCounts: StateFlow<Map<String, Pair<Int?, Int?>>> = _projectCounts.asStateFlow()

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (!forceRefresh) {
                    val cached = CacheManager.getProjects()
                    if (cached != null) {
                        withContext(Dispatchers.Main) {
                            _loadState.value = LoadState.Success(cached)
                            _projectCounts.update { counts ->
                                counts + cached.associate { it.projectId to Pair<Int?, Int?>(null, null) }
                                    .filter { it.key !in counts }
                            }
                        }
                        return@launch
                    }
                } else {
                    CacheManager.clearAll()
                    withContext(Dispatchers.Main) {
                        _projectCounts.value = emptyMap()
                    }
                }

                withContext(Dispatchers.Main) {
                    val current = (_loadState.value as? LoadState.Success)?.data ?: emptyList()
                    _loadState.value = if (forceRefresh) LoadState.Success(current, isRefreshing = true)
                                       else LoadState.Loading
                }

                when (val resp = ApiClient.service.getProjects()) {
                    is ApiResult.Success -> {
                        CacheManager.cacheProjects(resp.data)
                        withContext(Dispatchers.Main) {
                            _loadState.value = LoadState.Success(resp.data)
                            _projectCounts.update { counts ->
                                counts + resp.data.associate { it.projectId to Pair<Int?, Int?>(null, null) }
                                    .filter { it.key !in counts }
                            }
                        }
                    }
                    is ApiResult.Error -> withContext(Dispatchers.Main) {
                        _loadState.value = LoadState.Error("Failed to load projects: ${resp.message}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                withContext(Dispatchers.Main) { _loadState.value = LoadState.Error("Error: ${e.message}") }
            }
        }
    }

    /** Called by background preloading when counts arrive for a project. */
    fun updateCount(projectId: String, sampleCount: Int, datasetCount: Int) {
        _projectCounts.update { it + (projectId to Pair(sampleCount, datasetCount)) }
    }
}
