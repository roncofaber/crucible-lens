package crucible.lens.data.repository

import crucible.lens.data.api.ApiResult
import crucible.lens.data.api.CrucibleApiService
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

class CrucibleRepository(private val api: CrucibleApiService) {

    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.IO) {
        try {
            // Check if we already know the resource type from cache
            val cachedType = CacheManager.getResourceType(uuid)

            val resourceType = if (cachedType != null) {
                cachedType
            } else {
                val typeResult = api.getResourceType(uuid)
                when (typeResult) {
                    is ApiResult.Success -> typeResult.data.resolvedType?.lowercase()
                        ?: return@withContext fetchResourceByUuidFallback(uuid)
                    is ApiResult.Error -> return@withContext fetchResourceByUuidFallback(uuid)
                }
            }

            when (resourceType.lowercase()) {
                "sample" -> {
                    val sampleResult = api.getSample(uuid)
                    return@withContext when (sampleResult) {
                        is ApiResult.Success -> {
                            CacheManager.cacheResourceType(uuid, "sample")
                            ResourceResult.Success(sampleResult.data)
                        }
                        is ApiResult.Error -> {
                            if (cachedType != null) {
                                CacheManager.removeResourceType(uuid)
                                fetchResourceByUuidFallback(uuid)
                            } else {
                                httpError(sampleResult.code)
                            }
                        }
                    }
                }
                "dataset" -> {
                    val dataset = fetchDatasetWithMetadata(uuid)
                    if (dataset != null) {
                        CacheManager.cacheResourceType(uuid, "dataset")
                        ResourceResult.Success(dataset)
                    } else if (cachedType != null) {
                        CacheManager.removeResourceType(uuid)
                        fetchResourceByUuidFallback(uuid)
                    } else {
                        ResourceResult.Error("Failed to fetch dataset")
                    }
                }
                else -> ResourceResult.Error("Unknown resource type: $resourceType")
            }
        } catch (e: Exception) {
            ResourceResult.Error("Network error: ${e.message ?: "check your connection"}")
        }
    }

    private suspend fun fetchResourceByUuidFallback(uuid: String): ResourceResult {
        return try {
            val sampleResult = api.getSample(uuid)
            when (sampleResult) {
                is ApiResult.Success -> {
                    CacheManager.cacheResourceType(uuid, "sample")
                    ResourceResult.Success(sampleResult.data)
                }
                is ApiResult.Error -> {
                    val dataset = fetchDatasetWithMetadata(uuid)
                    if (dataset != null) {
                        CacheManager.cacheResourceType(uuid, "dataset")
                        ResourceResult.Success(dataset)
                    } else {
                        httpError(sampleResult.code)
                    }
                }
            }
        } catch (e: Exception) {
            ResourceResult.Error("Network error: ${e.message ?: "check your connection"}")
        }
    }

    // Fetch dataset and its scientific metadata in parallel, then merge.
    private suspend fun fetchDatasetWithMetadata(uuid: String): Dataset? = coroutineScope {
        val datasetDeferred = async { api.getDataset(uuid, includeMetadata = true) }
        val metaDeferred = async {
            try {
                api.getDatasetScientificMetadata(uuid)
            } catch (e: Exception) {
                ApiResult.Error(-1, e.message ?: "Unknown error")
            }
        }
        val datasetResult = datasetDeferred.await()
        val dataset = when (datasetResult) {
            is ApiResult.Success -> datasetResult.data
            is ApiResult.Error -> return@coroutineScope null
        }
        val metaResult = metaDeferred.await()
        val meta = when (metaResult) {
            is ApiResult.Success -> metaResult.data
            is ApiResult.Error -> null
        }
        if (meta != null) dataset.copy(scientificMetadata = meta) else dataset
    }

    suspend fun fetchThumbnails(datasetUuid: String): List<String> = withContext(Dispatchers.IO) {
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
