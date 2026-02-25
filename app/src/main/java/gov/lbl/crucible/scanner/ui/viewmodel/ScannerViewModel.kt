package gov.lbl.crucible.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.lbl.crucible.scanner.data.cache.CacheManager
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.repository.CrucibleRepository
import gov.lbl.crucible.scanner.data.repository.ResourceResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val resource: CrucibleResource, val thumbnails: List<String> = emptyList()) : UiState()
    data class Error(val message: String) : UiState()
}

class ScannerViewModel : ViewModel() {
    private val repository = CrucibleRepository()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var smoothAnimations: Boolean = true

    fun setSmoothAnimations(enabled: Boolean) {
        smoothAnimations = enabled
    }

    companion object {
        // Delay for cached resources to show loading animation (in milliseconds)
        private const val CACHE_LOADING_DELAY_MS = 350L
    }

    fun fetchResource(uuid: String) {
        viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            // Always show loading state briefly for smooth transition
            _uiState.value = UiState.Loading

            // Check cache first
            val cachedResource = CacheManager.getResource(trimmedUuid)
            val cachedThumbnails = CacheManager.getThumbnails(trimmedUuid)

            if (cachedResource != null) {
                // Brief delay to show loading animation (only if smooth animations enabled)
                if (smoothAnimations) {
                    delay(CACHE_LOADING_DELAY_MS)
                }
                // Use cached data
                _uiState.value = UiState.Success(
                    cachedResource,
                    cachedThumbnails ?: emptyList()
                )
                // Preload related resources in background
                preloadRelatedResources(cachedResource)
                return@launch
            }

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
