package crucible.lens.data.repository

import android.util.Log
import crucible.lens.data.api.ApiClient
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "CrucibleRepository"

sealed class ResourceResult {
    data class Success(val resource: CrucibleResource) : ResourceResult()
    data class Error(val message: String) : ResourceResult()
    object Loading : ResourceResult()
}

class CrucibleRepository {
    private val api = ApiClient.service

    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.IO) {
        try {
            // Check if we already know the resource type from cache
            val cachedType = crucible.lens.data.cache.CacheManager.getResourceType(uuid)

            val resourceType = if (cachedType != null) {
                // Use cached type to avoid API call
                cachedType
            } else {
                // Determine the resource type using the /idtype endpoint
                val typeResponse = api.getResourceType(uuid)
                val typeBody = typeResponse.body()

                if (!typeResponse.isSuccessful || typeBody == null) {
                    // Fallback to old method if idtype endpoint doesn't work
                    return@withContext fetchResourceByUuidFallback(uuid)
                }

                typeBody.resolvedType?.lowercase() ?: return@withContext fetchResourceByUuidFallback(uuid)
            }

            // Fetch the appropriate resource based on type
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
                    }
                }
                "dataset" -> {
                    val datasetResponse = api.getDataset(uuid, includeMetadata = true)
                    val datasetBody = datasetResponse.body()
                    Log.d(TAG, "Dataset $uuid — scientificMetadata: ${datasetBody?.scientificMetadata}")
                    if (datasetResponse.isSuccessful && datasetBody != null) {
                        crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "dataset")
                        return@withContext ResourceResult.Success(datasetBody)
                    } else if (cachedType != null) {
                        crucible.lens.data.cache.CacheManager.removeResourceType(uuid)
                        return@withContext fetchResourceByUuidFallback(uuid)
                    }
                }
            }

            ResourceResult.Error("Resource not found: $uuid")
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching resource $uuid", e)
            ResourceResult.Error("Network error: check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching resource $uuid", e)
            ResourceResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    // Fallback method using the old approach (try both endpoints)
    private suspend fun fetchResourceByUuidFallback(uuid: String): ResourceResult {
        try {
            // Try sample first
            val sampleResponse = api.getSample(uuid)
            val sampleBody = sampleResponse.body()
            if (sampleResponse.isSuccessful && sampleBody != null) {
                // Cache the type for future calls
                crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "sample")
                return ResourceResult.Success(sampleBody)
            }

            // If not a sample, try dataset
            val datasetResponse = api.getDataset(uuid, includeMetadata = true)
            val datasetBody = datasetResponse.body()
            Log.d(TAG, "Fallback dataset $uuid — scientificMetadata: ${datasetBody?.scientificMetadata}")
            if (datasetResponse.isSuccessful && datasetBody != null) {
                crucible.lens.data.cache.CacheManager.cacheResourceType(uuid, "dataset")
                return ResourceResult.Success(datasetBody)
            }

            // Neither worked
            return ResourceResult.Error("Resource not found: $uuid")
        } catch (e: IOException) {
            Log.e(TAG, "Network error in fallback fetch for $uuid", e)
            return ResourceResult.Error("Network error: check your connection")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in fallback fetch for $uuid", e)
            return ResourceResult.Error(e.message ?: "Unknown error occurred")
        }
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
