package crucible.lens.data.util

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Fetches samples and datasets for a project in parallel, using the cache when available.
 * Throws on network failure so callers can handle errors appropriately.
 */
suspend fun fetchProjectData(
    projectId: String,
    forceRefresh: Boolean = false
): Pair<List<Sample>, List<Dataset>> {
    val cachedSamples = if (!forceRefresh) CacheManager.getProjectSamples(projectId) else null
    val cachedDatasets = if (!forceRefresh) CacheManager.getProjectDatasets(projectId) else null
    if (cachedSamples != null && cachedDatasets != null) return cachedSamples to cachedDatasets
    return coroutineScope {
        val s = async {
            cachedSamples ?: ApiClient.service.getSamplesByProject(projectId).body()
                ?.also { CacheManager.cacheProjectSamples(projectId, it) } ?: emptyList()
        }
        val d = async {
            cachedDatasets ?: ApiClient.service.getDatasetsByProject(projectId).body()
                ?.also { CacheManager.cacheProjectDatasets(projectId, it) } ?: emptyList()
        }
        s.await() to d.await()
    }
}
