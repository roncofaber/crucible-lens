package crucible.lens.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentThumbnailCache
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
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

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CrucibleRepository()
    private val ctx = application.applicationContext

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
            ?: PersistentThumbnailCache.load(ctx, uuid)?.also { CacheManager.cacheThumbnails(uuid, it) }

    private suspend fun fetchAndCacheThumbnails(uuid: String): List<String> {
        val fetched = repository.fetchThumbnails(uuid)
        CacheManager.cacheThumbnails(uuid, fetched)
        PersistentThumbnailCache.save(ctx, uuid, fetched)
        return fetched
    }

    private fun evictThumbnails(uuid: String) {
        CacheManager.clearThumbnail(uuid)
        PersistentThumbnailCache.clear(ctx, uuid)
    }

    // ─────────────────────────────────────────────────────────────────────────

    fun fetchResource(uuid: String) {
        viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            val cachedResource = CacheManager.getResource(trimmedUuid)
            val cachedThumbnails = getThumbnails(trimmedUuid)

            if (cachedResource != null) {
                _uiState.value = UiState.Success(cachedResource, cachedThumbnails ?: emptyList())
                preloadRelatedResources(cachedResource)
                return@launch
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
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    suspend fun ensureResourceCached(uuid: String): CrucibleResource? {
        val cached = CacheManager.getResource(uuid)
        if (cached != null) return cached
        return try {
            when (val result = repository.fetchResourceByUuid(uuid)) {
                is ResourceResult.Success -> {
                    CacheManager.cacheResource(uuid, result.resource)
                    result.resource
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun refreshResource(uuid: String) {
        viewModelScope.launch {
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
                        else -> _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = false) else it }
                    }
                } catch (_: Exception) {
                    _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = false) else it }
                }
            } else {
                fetchResource(trimmedUuid)
            }
        }
    }
}
