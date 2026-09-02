package crucible.lens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.Project
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.sync.DataSyncManager
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: CrucibleRepository,
    private val dataSyncManager: DataSyncManager
) : ViewModel() {

    private val _projects = MutableStateFlow(repository.getCachedProjects() ?: emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _fetchError = MutableStateFlow<String?>(null)
    val fetchError: StateFlow<String?> = _fetchError.asStateFlow()

    private val _instruments = MutableStateFlow<List<Instrument>>(emptyList())
    val instruments: StateFlow<List<Instrument>> = _instruments.asStateFlow()

    private var loadedApiKey: String? = null
    private var loadedAccountId: String? = null
    private var accountSelectionInitialized = false
    private var projectsFetchJob: Job? = null
    private var pinnedInstrumentsFetchJob: Job? = null

    suspend fun selectAccount(platformContext: PlatformContext, accountId: String?, syncedProjects: Set<String>) {
        if (accountSelectionInitialized && accountId == loadedAccountId) return
        accountSelectionInitialized = true
        projectsFetchJob?.cancel()
        pinnedInstrumentsFetchJob?.cancel()
        loadedApiKey = null
        loadedAccountId = accountId
        _projects.value = emptyList()
        _instruments.value = emptyList()
        _fetchError.value = null
        if (accountId == null) return
        dataSyncManager.restoreProjects(platformContext, accountId, syncedProjects)
        dataSyncManager.restoreProjectSummaries(platformContext, accountId)?.let { cached ->
            _projects.value = cached
        }
    }

    fun ensureLoaded(platformContext: PlatformContext, apiKey: String?, accountId: String?) {
        if (apiKey.isNullOrBlank() || accountId == null || accountId != loadedAccountId || apiKey == loadedApiKey) return
        fetchProjects(platformContext, apiKey, accountId)
    }

    fun refresh(platformContext: PlatformContext, apiKey: String?, accountId: String?) {
        loadedApiKey = null
        if (accountId != null) fetchProjects(platformContext, apiKey, accountId)
    }

    fun dismissError() {
        _fetchError.value = null
    }

    fun loadPinnedInstruments(pinnedInstrumentIds: Set<String>) {
        pinnedInstrumentsFetchJob?.cancel()
        pinnedInstrumentsFetchJob = viewModelScope.launch {
            val active = repository.getCachedInstruments().orEmpty()
            val activeById = active.associateBy { it.uniqueId }
            val missing = pinnedInstrumentIds.filterNot { it in activeById }
            val resolved = coroutineScope {
                missing.map { instrumentId ->
                    async {
                        (repository.fetchInstrument(instrumentId) as? ApiResult.Success)?.data
                    }
                }.mapNotNull { it.await() }
            }
            _instruments.value = (active.filter { it.uniqueId in pinnedInstrumentIds } + resolved).distinctBy { it.uniqueId }
        }
    }

    private fun fetchProjects(platformContext: PlatformContext, apiKey: String?, accountId: String) {
        if (apiKey.isNullOrBlank()) return
        loadedApiKey = apiKey
        projectsFetchJob?.cancel()
        projectsFetchJob = viewModelScope.launch {
            try {
                when (val response = dataSyncManager.refreshOverview(platformContext, accountId)) {
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
    }
}
