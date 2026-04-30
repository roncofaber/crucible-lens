package crucible.lens.ui.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.sync.DataSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val resource: CrucibleResource, val thumbnails: List<String> = emptyList(), val isRefreshing: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

private const val MAX_CARD_STATE_ENTRIES = 50

class ResourceDetailViewModel : ViewModel() {
    private val repository = CrucibleRepository()

    // Tracks the active fetch/refresh so navigating to a new resource
    // cancels any in-flight request for the previous one.
    private var activeFetchJob: Job? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Persists expanded/collapsed state of detail screen cards across navigation. */
    private val resourceCardState = mutableStateMapOf<String, SnapshotStateMap<String, Boolean>>()
    private val resourceCardStateOrder = ArrayDeque<String>()

    fun getCardState(resourceId: String, key: String): Boolean =
        resourceCardState[resourceId]?.get(key) ?: false

    fun setCardState(resourceId: String, key: String, value: Boolean) {
        if (!resourceCardState.containsKey(resourceId)) {
            resourceCardStateOrder.addLast(resourceId)
            if (resourceCardStateOrder.size > MAX_CARD_STATE_ENTRIES) {
                resourceCardState.remove(resourceCardStateOrder.removeFirst())
            }
        }
        resourceCardState.getOrPut(resourceId) { mutableStateMapOf() }[key] = value
    }

    // ── Thumbnail helpers (L1 memory → L2 disk → network) ────────────────────

    private fun getThumbnails(uuid: String): List<String>? =
        CacheManager.getThumbnails(uuid)
            ?: null // L2 disk cache stubbed: PersistentThumbnailCache not available in commonMain

    private suspend fun fetchAndCacheThumbnails(uuid: String): List<String> {
        val fetched = repository.fetchThumbnails(uuid)
        CacheManager.cacheThumbnails(uuid, fetched)
        // L2 disk cache stubbed: PersistentThumbnailCache not available in commonMain
        return fetched
    }

    private fun evictThumbnails(uuid: String) {
        CacheManager.clearThumbnail(uuid)
        // L2 disk cache stubbed: PersistentThumbnailCache not available in commonMain
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun fetchResource(uuid: String) {
        activeFetchJob?.cancel()
        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            val cachedResource = CacheManager.getResource(trimmedUuid)
            val cachedThumbnails = getThumbnails(trimmedUuid)

            // Show cached version immediately for snappy navigation, but always
            // fetch fresh data so the detail view has links and full metadata.
            if (cachedResource != null) {
                _uiState.value = UiState.Success(cachedResource, cachedThumbnails ?: emptyList())
            }

            val current = _uiState.value
            _uiState.value = if (current is UiState.Success) current.copy(isRefreshing = true)
                             else UiState.Loading

            when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                is ResourceResult.Success -> {
                    val resource = result.resource
                    CacheManager.cacheResource(trimmedUuid, resource)

                    val thumbnails = if (resource is Dataset) {
                        getThumbnails(resource.uniqueId) ?: fetchAndCacheThumbnails(resource.uniqueId)
                    } else emptyList()

                    _uiState.value = UiState.Success(resource, thumbnails)
                    preloadRelatedResources(resource)
                }
                is ResourceResult.Error -> _uiState.value = UiState.Error(result.message)
                is ResourceResult.Loading -> {}
            }
        }
    }

    private fun preloadRelatedResources(resource: CrucibleResource) {
        viewModelScope.launch {
            val uuidsToPreload = when (resource) {
                is Sample -> resource.links?.map { it.uniqueId } ?: emptyList()
                is Dataset -> resource.links?.map { it.uniqueId } ?: emptyList()
            }

            uuidsToPreload.filter { it != resource.uniqueId }.distinct().forEach { uuid ->
                if (CacheManager.getResource(uuid) == null) {
                    launch {
                        try {
                            when (val result = repository.fetchResourceByUuid(uuid)) {
                                is ResourceResult.Success -> {
                                    CacheManager.cacheResource(uuid, result.resource)
                                    if (result.resource is Dataset && getThumbnails(uuid) == null) {
                                        fetchAndCacheThumbnails(uuid)
                                    }
                                }
                                else -> {}
                            }
                        } catch (_: Exception) {
                            // Background preload — silently ignore failures
                        }
                    }
                }
            }
        }
    }

    private var syncJob: Job? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun startBackgroundSync() {
        _isSyncing.value = true
        syncJob = viewModelScope.launch {
            try { DataSyncManager.syncAll() } catch (_: Exception) { }
            finally { _isSyncing.value = false }
        }
    }

    fun updateCurrentResource(resource: CrucibleResource) {
        _uiState.update {
            if (it is UiState.Success && it.resource.uniqueId != resource.uniqueId) {
                val thumbnails = CacheManager.getThumbnails(resource.uniqueId) ?: emptyList()
                it.copy(resource = resource, thumbnails = thumbnails)
            } else it
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun refreshResource(uuid: String) {
        activeFetchJob?.cancel()
        // Pause background sync so the user-initiated refresh gets uncontested network access.
        // Sync resumes after the refresh completes.
        val syncWasActive = syncJob?.isActive == true
        syncJob?.cancel()

        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()
            CacheManager.clearResource(trimmedUuid)
            evictThumbnails(trimmedUuid)

            val current = _uiState.value
            if (current is UiState.Success && current.resource.uniqueId == trimmedUuid) {
                _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = true) else it }
                try {
                    when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                        is ResourceResult.Success -> {
                            val thumbnails = if (result.resource is Dataset)
                                fetchAndCacheThumbnails(trimmedUuid) else emptyList()
                            CacheManager.cacheResource(trimmedUuid, result.resource)
                            _uiState.value = UiState.Success(result.resource, thumbnails)
                        }
                        is ResourceResult.Error -> _uiState.value = UiState.Error(result.message)
                        else -> {}
                    }
                } catch (_: Exception) {
                    // Timeout or network failure — error state will be set by the result handling above
                } finally {
                    // Always clear the spinner — prevents stuck refresh if anything goes wrong
                    _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = false) else it }
                    if (syncWasActive) startBackgroundSync()
                }
            } else {
                fetchResource(trimmedUuid)
                if (syncWasActive) startBackgroundSync()
            }
        }
    }
}
