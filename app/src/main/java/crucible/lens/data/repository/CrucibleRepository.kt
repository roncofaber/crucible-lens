package crucible.lens.data.repository

import android.util.Log
import crucible.lens.data.api.ApiClient
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "CrucibleRepository"

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
    private val api = ApiClient.service

    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.IO) {
        try {
            // Check if we already know the resource type from cache
            val cachedType = crucible.lens.data.cache.CacheManager.getResourceType(uuid)

            val resourceType = if (cachedType != null) {
                cachedType
            } else {
                val typeResponse = api.getResourceType(uuid)
                val typeBody = typeResponse.body()

                if (!typeResponse.isSuccessful || typeBody == null) {
                    return@withContext fetchResourceByUuidFallback(uuid)
                }

                typeBody.resolvedType?.lowercase() ?: return@withContext fetchResourceByUuidFallback(uuid)
            }

            when (resourceType.lowercase()) {
                "sample" -> {
                    val sampleResponse = api.getSample(uuid)
                    val sampleBody = sampleResponse.body()
                    if (sampleResponse.isSuccessful && sampleBody != null) {
                        crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "sample")
                        return@withContext ResourceResult.Success(sampleBody)
                    } else if (cachedType != null) {
                        crucible.lens.data.cache.CacheManager.removeResourceType(uuid)
                        return@withContext fetchResourceByUuidFallback(uuid)
                    } else {
                        return@withContext httpError(sampleResponse.code())
                    }
                }
                "dataset" -> {
                    val dataset = fetchDatasetWithMetadata(uuid)
                    if (dataset != null) {
                        crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "dataset")
                        return@withContext ResourceResult.Success(dataset)
                    } else if (cachedType != null) {
                        crucible.lens.data.cache.CacheManager.removeResourceType(uuid)
                        return@withContext fetchResourceByUuidFallback(uuid)
                    }
                }
            }

            httpError(404)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching resource $uuid", e)
            ResourceResult.Error("Network error: check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching resource $uuid", e)
            ResourceResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    private suspend fun fetchResourceByUuidFallback(uuid: String): ResourceResult {
        try {
            val sampleResponse = api.getSample(uuid)
            val sampleBody = sampleResponse.body()
            if (sampleResponse.isSuccessful && sampleBody != null) {
                crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "sample")
                return ResourceResult.Success(sampleBody)
            }

            val dataset = fetchDatasetWithMetadata(uuid)
            if (dataset != null) {
                crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "dataset")
                return ResourceResult.Success(dataset)
            }

            return httpError(sampleResponse.code())
        } catch (e: IOException) {
            Log.e(TAG, "Network error in fallback fetch for $uuid", e)
            return ResourceResult.Error("Network error: check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fallback fetch for $uuid", e)
            return ResourceResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    // Fetch dataset and its scientific metadata in parallel, then merge.
    private suspend fun fetchDatasetWithMetadata(uuid: String): Dataset? = coroutineScope {
        val datasetDeferred = async { api.getDataset(uuid, includeMetadata = true) }
        val metaDeferred    = async {
            try { api.getDatasetScientificMetadata(uuid) }
            catch (e: Exception) { null }
        }
        val datasetResponse = datasetDeferred.await()
        val dataset = datasetResponse.body() ?: return@coroutineScope null
        val meta = metaDeferred.await()?.takeIf { it.isSuccessful }?.body()
        if (!meta.isNullOrEmpty()) dataset.copy(scientificMetadata = meta) else dataset
    }

    suspend fun fetchThumbnails(datasetUuid: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getThumbnails(datasetUuid)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                body.map { thumb -> "data:image/png;base64,${thumb.thumbnailB64}" }
            } else {
                emptyList()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching thumbnails for $datasetUuid", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching thumbnails for $datasetUuid", e)
            emptyList()
        }
    }
}
