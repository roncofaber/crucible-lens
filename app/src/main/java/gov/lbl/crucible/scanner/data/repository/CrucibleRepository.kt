package gov.lbl.crucible.scanner.data.repository

import android.util.Log
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.DatasetReference
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.model.SampleReference
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
                        var sample = sampleBody

                        // Fetch parent samples
                        val parentsResponse = api.getParentSamples(uuid)
                        val parentsBody = parentsResponse.body()
                        if (parentsResponse.isSuccessful && parentsBody != null) {
                            sample = sample.copy(
                                parentSamples = parentsBody
                                    .map { SampleReference(it.uniqueId, it.name) }
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId }
                            )
                        }

                        // Fetch child samples
                        val childrenResponse = api.getChildSamples(uuid)
                        val childrenBody = childrenResponse.body()
                        if (childrenResponse.isSuccessful && childrenBody != null) {
                            sample.childSamples = childrenBody
                                .map { SampleReference(it.uniqueId, it.name) }
                                .distinctBy { it.uniqueId }
                                .sortedBy { it.uniqueId }
                        }

                        return@withContext ResourceResult.Success(sample)
                    }
                }
                "dataset" -> {
                    val datasetResponse = api.getDataset(uuid)
                    val datasetBody = datasetResponse.body()
                    if (datasetResponse.isSuccessful && datasetBody != null) {
                        var dataset = datasetBody

                        // Fetch scientific metadata
                        val metadataResponse = api.getScientificMetadata(uuid)
                        if (metadataResponse.isSuccessful) {
                            dataset = dataset.copy(scientificMetadata = metadataResponse.body())
                        }

                        // Fetch parent datasets
                        val parentsResponse = api.getParentDatasets(uuid)
                        val parentsBody = parentsResponse.body()
                        if (parentsResponse.isSuccessful && parentsBody != null) {
                            dataset.parentDatasets = parentsBody
                                .map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                .distinctBy { it.uniqueId }
                                .sortedBy { it.uniqueId }
                        }

                        // Fetch child datasets
                        val childrenResponse = api.getChildDatasets(uuid)
                        val childrenBody = childrenResponse.body()
                        if (childrenResponse.isSuccessful && childrenBody != null) {
                            dataset.childDatasets = childrenBody
                                .map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                .distinctBy { it.uniqueId }
                                .sortedBy { it.uniqueId }
                        }

                        // Fetch samples associated with this dataset
                        val samplesResponse = api.getDatasetSamples(uuid)
                        val samplesBody = samplesResponse.body()
                        if (samplesResponse.isSuccessful && samplesBody != null) {
                            dataset = dataset.copy(
                                samples = samplesBody
                                    .map { SampleReference(it.uniqueId, it.name) }
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId }
                            )
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
