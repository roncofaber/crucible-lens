package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ResourceResult {
    data class Success(val resource: CrucibleResource) : ResourceResult()
    data class Error(val message: String) : ResourceResult()
    object Loading : ResourceResult()
}

private fun httpError(code: Int): ResourceResult.Error = when (code) {
    401, 403 -> ResourceResult.Error("Access denied (HTTP $code) — check your API key")
    404      -> ResourceResult.Error("Resource not found")
    in 500..599 -> ResourceResult.Error("Server error (HTTP $code) — try again later")
    else        -> ResourceResult.Error("Request failed (HTTP $code)")
}

class CrucibleRepository {
    private val api get() = ApiClient.service

    /**
     * Fetches any resource by UUID using the unified /resources/{uuid} endpoint.
     * Single call — no type lookup needed, links and metadata included.
     */
    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.Default) {
        try {
            val cached = CacheManager.getResource(uuid)
            // Check if we have a fully-loaded cached version (with links)
            if (cached != null && hasLinks(cached)) {
                return@withContext ResourceResult.Success(cached)
            }

            when (val result = api.getResource(uuid)) {
                is ApiResult.Success -> {
                    val resource = result.data
                    CacheManager.cacheResource(uuid, resource)
                    ResourceResult.Success(resource)
                }
                is ApiResult.Error -> httpError(result.code)
            }
        } catch (e: Exception) {
            ResourceResult.Error("Network error: ${e.message ?: "check your connection"}")
        }
    }

    /** Returns true if the resource was loaded with links (i.e. from a detail fetch, not a list fetch). */
    private fun hasLinks(resource: CrucibleResource): Boolean = when (resource) {
        is crucible.lens.data.model.Sample -> resource.links != null
        is Dataset -> resource.links != null
    }

    suspend fun fetchThumbnails(datasetUuid: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val result = api.getThumbnails(datasetUuid)
            when (result) {
                is ApiResult.Success -> result.data.map { thumb -> "data:image/png;base64,${thumb.thumbnailB64}" }
                is ApiResult.Error -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
