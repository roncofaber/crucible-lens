package gov.lbl.crucible.scanner.data.repository

import android.util.Log
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.DatasetReference
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.model.SampleReference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            // First, determine the resource type using the new /idtype endpoint
            val typeResponse = api.getResourceType(uuid)
            val typeBody = typeResponse.body()

            if (!typeResponse.isSuccessful || typeBody == null) {
                // Fallback to old method if idtype endpoint doesn't work
                return@withContext fetchResourceByUuidFallback(uuid)
            }

            val resourceType = typeBody.objectType

            // Fetch the appropriate resource based on type
            when (resourceType.lowercase()) {
                "sample" -> {
                    val sampleResponse = api.getSample(uuid)
                    val sampleBody = sampleResponse.body()
                    if (sampleResponse.isSuccessful && sampleBody != null) {
                        val sample = coroutineScope {
                            val parentsDeferred = async { api.getParentSamples(uuid) }
                            val childrenDeferred = async { api.getChildSamples(uuid) }

                            val parentsResponse = parentsDeferred.await()
                            val childrenResponse = childrenDeferred.await()

                            var s = sampleBody
                            val parentsBody = parentsResponse.body()
                            if (parentsResponse.isSuccessful && parentsBody != null) {
                                s = s.copy(
                                    parentSamples = parentsBody
                                        .map { SampleReference(it.uniqueId, it.name) }
                                        .distinctBy { it.uniqueId }
                                        .sortedBy { it.uniqueId }
                                )
                            }
                            val childrenBody = childrenResponse.body()
                            if (childrenResponse.isSuccessful && childrenBody != null) {
                                s.childSamples = childrenBody
                                    .map { SampleReference(it.uniqueId, it.name) }
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId }
                            }
                            s
                        }
                        return@withContext ResourceResult.Success(sample)
                    }
                }
                "dataset" -> {
                    val datasetResponse = api.getDataset(uuid)
                    val datasetBody = datasetResponse.body()
                    if (datasetResponse.isSuccessful && datasetBody != null) {
                        val dataset = coroutineScope {
                            val metadataDeferred = async { api.getScientificMetadata(uuid) }
                            val parentsDeferred  = async { api.getParentDatasets(uuid) }
                            val childrenDeferred = async { api.getChildDatasets(uuid) }
                            val samplesDeferred  = async { api.getDatasetSamples(uuid) }

                            val metadataResponse = metadataDeferred.await()
                            val parentsResponse  = parentsDeferred.await()
                            val childrenResponse = childrenDeferred.await()
                            val samplesResponse  = samplesDeferred.await()

                            var d = datasetBody
                            if (metadataResponse.isSuccessful) {
                                d = d.copy(scientificMetadata = metadataResponse.body())
                            }
                            parentsResponse.body()?.let { body ->
                                d.parentDatasets = body
                                    .map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId }
                            }
                            childrenResponse.body()?.let { body ->
                                d.childDatasets = body
                                    .map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId }
                            }
                            samplesResponse.body()?.let { body ->
                                d = d.copy(
                                    samples = body
                                        .map { SampleReference(it.uniqueId, it.name) }
                                        .distinctBy { it.uniqueId }
                                        .sortedBy { it.uniqueId }
                                )
                            }
                            d
                        }
                        return@withContext ResourceResult.Success(dataset)
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
                return ResourceResult.Success(sampleBody)
            }

            // If not a sample, try dataset
            val datasetResponse = api.getDataset(uuid)
            val datasetBody = datasetResponse.body()
            if (datasetResponse.isSuccessful && datasetBody != null) {
                // Try to fetch scientific metadata
                val metadataResponse = api.getScientificMetadata(uuid)
                val enrichedDataset = if (metadataResponse.isSuccessful) {
                    datasetBody.copy(scientificMetadata = metadataResponse.body())
                } else {
                    datasetBody
                }

                return ResourceResult.Success(enrichedDataset)
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
