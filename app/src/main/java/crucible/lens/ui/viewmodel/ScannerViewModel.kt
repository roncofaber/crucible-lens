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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val resource: CrucibleResource, val thumbnails: List<String> = emptyList(), val isRefreshing: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

class ScannerViewModel : ViewModel() {
    private val repository = CrucibleRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var smoothAnimations: Boolean = true

    /** -1 = swiped to previous, 1 = swiped to next, 0 = normal navigation */
    var siblingNavDirection: Int = 0
        private set

    /** Persists expanded/collapsed state of detail screen cards across sibling navigation. */
    private val resourceCardState = mutableStateMapOf<String, SnapshotStateMap<String, Boolean>>()

    fun getCardState(resourceId: String, key: String): Boolean =
        resourceCardState[resourceId]?.get(key) ?: false

    fun setCardState(resourceId: String, key: String, value: Boolean) {
        resourceCardState.getOrPut(resourceId) { mutableStateMapOf() }[key] = value
    }

    fun setSmoothAnimations(enabled: Boolean) {
        smoothAnimations = enabled
    }

    fun prepareSiblingNav(direction: Int) {
        siblingNavDirection = direction
    }

    fun fetchResource(uuid: String) {
        viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            // Check cache first — emit Success immediately without Loading state
            val cachedResource = CacheManager.getResource(trimmedUuid)
            val cachedThumbnails = CacheManager.getThumbnails(trimmedUuid)

            if (cachedResource != null) {
                _uiState.value = UiState.Success(
                    cachedResource,
                    cachedThumbnails ?: emptyList()
                )
                // Preload related resources in background
                preloadRelatedResources(cachedResource)
                return@launch
            }

            // Not cached — stay in Success (isRefreshing=true) if we already have something
            // to show, so the TopAppBar never disappears. Only go to Loading from scratch.
            val current = _uiState.value
            _uiState.value = if (current is UiState.Success) current.copy(isRefreshing = true)
                             else UiState.Loading

            when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                is ResourceResult.Success -> {
                    val resource = result.resource

                    // Cache the resource
                    CacheManager.cacheResource(trimmedUuid, resource)

                    // Fetch thumbnails if it's a dataset
                    val thumbnails = if (resource is Dataset) {
                        val fetched = repository.fetchThumbnails(resource.uniqueId)
                        // Cache thumbnails
                        CacheManager.cacheThumbnails(resource.uniqueId, fetched)
                        fetched
                    } else {
                        emptyList()
                    }

                    _uiState.value = UiState.Success(resource, thumbnails)

                    // Preload related resources in background
                    preloadRelatedResources(resource)
                }
                is ResourceResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
                is ResourceResult.Loading -> {
                    // Already in loading state
                }
            }
        }
    }

    private fun preloadRelatedResources(resource: CrucibleResource) {
        viewModelScope.launch {
            val uuidsToPreload = mutableListOf<String>()

            when (resource) {
                is Sample -> {
                    // Preload parent samples
                    resource.parentSamples?.forEach { parent ->
                        uuidsToPreload.add(parent.uniqueId)
                    }
                    // Preload child samples
                    resource.childSamples?.forEach { child ->
                        uuidsToPreload.add(child.uniqueId)
                    }
                    // Preload linked datasets
                    resource.datasets?.forEach { dataset ->
                        uuidsToPreload.add(dataset.uniqueId)
                    }
                }
                is Dataset -> {
                    // Preload parent datasets
                    resource.parentDatasets?.forEach { parent ->
                        uuidsToPreload.add(parent.uniqueId)
                    }
                    // Preload child datasets
                    resource.childDatasets?.forEach { child ->
                        uuidsToPreload.add(child.uniqueId)
                    }
                    // Preload linked samples
                    resource.samples?.forEach { sample ->
                        uuidsToPreload.add(sample.uniqueId)
                    }
                }
            }

            // Fetch and cache related resources in background
            uuidsToPreload.forEach { uuid ->
                // Only fetch if not already cached
                if (CacheManager.getResource(uuid) == null) {
                    launch {
                        try {
                            when (val result = repository.fetchResourceByUuid(uuid)) {
                                is ResourceResult.Success -> {
                                    CacheManager.cacheResource(uuid, result.resource)
                                    // Also preload thumbnails for datasets
                                    if (result.resource is Dataset) {
                                        val thumbnails = repository.fetchThumbnails(uuid)
                                        CacheManager.cacheThumbnails(uuid, thumbnails)
                                    }
                                }
                                else -> {
                                    // Silently fail for background preloading
                                }
                            }
                        } catch (e: Exception) {
                            // Silently fail for background preloading
                        }
                    }
                }
            }
        }
    }

    /** Fetches and caches a resource by UUID without touching [uiState]. Returns null on failure. */
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
        } catch (e: Exception) {
            null
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun refreshResource(uuid: String) {
        viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            // Clear cache for this specific resource
            CacheManager.clearResource(trimmedUuid)
            CacheManager.clearThumbnail(trimmedUuid)

            // Fetch fresh data
            fetchResource(trimmedUuid)
        }
    }
}
