package crucible.lens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.model.Project
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _projects = MutableStateFlow(repository.getCachedProjects() ?: emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    private val _isPreloading = MutableStateFlow(false)
    val isPreloading: StateFlow<Boolean> = _isPreloading.asStateFlow()

    private var loadedApiKey: String? = null
    private var preloadJob: Job? = null

    fun loadPersistedCache(platformContext: PlatformContext) {
        if (_projects.value.isNotEmpty()) return
        viewModelScope.launch {
            PersistentProjectCache.load(platformContext)?.let { cached ->
                repository.seedProjects(cached)
                _projects.value = cached
            }
        }
    }

    fun ensureLoaded(apiKey: String?) {
        if (apiKey.isNullOrBlank() || apiKey == loadedApiKey) return
        fetchProjects(apiKey)
    }

    fun refresh(apiKey: String?) {
        loadedApiKey = null
        fetchProjects(apiKey)
    }

    fun dismissError() {
        _fetchError.value = null
    }

    private fun fetchProjects(apiKey: String?) {
        if (apiKey.isNullOrBlank()) return
        loadedApiKey = apiKey
        viewModelScope.launch {
            try {
                when (val response = repository.fetchProjects()) {
                    is ApiResult.Success -> {
                        _projects.value = response.data
                        _fetchError.value = null
                    }
                    is ApiResult.Error -> _fetchError.value = response.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _fetchError.value = e.message ?: "Network error"
            }
        }
        viewModelScope.launch {
            try {
                repository.fetchInstruments()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun preload(platformContext: PlatformContext, pinnedProjects: Set<String>, syncedProjects: Set<String>) {
        val projects = _projects.value
        if (projects.isEmpty()) return
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            _isPreloading.value = true
            try {
                delay(500)

                // Only synced projects are preloaded; everything else fetches on demand when opened.
                val prioritizedProjects = projects
                    .filter { it.projectId in syncedProjects }
                    .sortedByDescending { it.projectId in pinnedProjects }
                var consecutiveFailures = 0
                val maxConsecutiveFailures = 5

                prioritizedProjects.chunked(3).forEach { batch ->
                    if (consecutiveFailures >= maxConsecutiveFailures) return@forEach
                    batch.forEach { project ->
                        launch(Dispatchers.Default) {
                            try {
                                repository.fetchProjectData(project.projectId)
                                consecutiveFailures = 0
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                consecutiveFailures++
                            }
                        }
                    }
                    delay(150)
                }

                launch(Dispatchers.Default) {
                    try {
                        PersistentProjectCache.save(platformContext, projects)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            } finally {
                _isPreloading.value = false
            }
        }
    }
}
