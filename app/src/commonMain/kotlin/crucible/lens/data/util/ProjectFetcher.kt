package crucible.lens.data.util

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.api.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
private val projectFetchMutexes = mutableMapOf<String, Mutex>()

/**
 * Fetches samples and datasets for a project in parallel, using the cache when available.
 * A per-project mutex prevents duplicate concurrent fetches: if two callers request the same
 * project simultaneously, the second waits for the first to finish and then reads from cache.
 * Throws on network failure so callers can handle errors appropriately.
 */
suspend fun fetchProjectData(
    projectId: String,
    forceRefresh: Boolean = false
): Pair<List<Sample>, List<Dataset>> {
    val mutex = projectFetchMutexes.getOrPut(projectId) { Mutex() }
    return mutex.withLock {
        val cachedSamples = if (!forceRefresh) CacheManager.getProjectSamples(projectId) else null
        val cachedDatasets = if (!forceRefresh) CacheManager.getProjectDatasets(projectId) else null
        if (cachedSamples != null && cachedDatasets != null) return@withLock cachedSamples to cachedDatasets
        coroutineScope {
            val s = async {
                if (cachedSamples != null) cachedSamples else {
                    val result = ApiClient.service.getSamplesByProject(projectId)
                    (result as? ApiResult.Success)?.data
                        ?.also { CacheManager.cacheProjectSamples(projectId, it) } ?: emptyList()
                }
            }
            val d = async {
                if (cachedDatasets != null) cachedDatasets else {
                    val result = ApiClient.service.getDatasetsByProject(projectId)
                    (result as? ApiResult.Success)?.data
                        ?.also { CacheManager.cacheProjectDatasets(projectId, it) } ?: emptyList()
                }
            }
            s.await() to d.await()
        }
    }
}
