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
 *
 * [onCountsAvailable] fires as soon as both the sample total and dataset total are known
 * (from the first page of each paginated response), before full data is loaded.
 */
suspend fun fetchProjectData(
    projectId: String,
    forceRefresh: Boolean = false,
    onCountsAvailable: (suspend (Int, Int) -> Unit)? = null
): Pair<List<Sample>, List<Dataset>> {
    val mutex = projectFetchMutexes.getOrPut(projectId) { Mutex() }
    return mutex.withLock {
        val cachedSamples = if (!forceRefresh) CacheManager.getProjectSamples(projectId) else null
        val cachedDatasets = if (!forceRefresh) CacheManager.getProjectDatasets(projectId) else null
        if (cachedSamples != null && cachedDatasets != null) {
            onCountsAvailable?.invoke(cachedSamples.size, cachedDatasets.size)
            return@withLock cachedSamples to cachedDatasets
        }
        coroutineScope {
            // Coordinate fire-once callback: fires when both totals are known
            var sampleTotal: Int? = null
            var datasetTotal: Int? = null
            val coordMutex = if (onCountsAvailable != null) Mutex() else null
            var countFired = false

            val sOnTotal: (suspend (Int) -> Unit)? = if (onCountsAvailable != null) { total ->
                coordMutex!!.withLock {
                    sampleTotal = total
                    val d = datasetTotal
                    if (d != null && !countFired) { countFired = true; onCountsAvailable(total, d) }
                }
            } else null

            val dOnTotal: (suspend (Int) -> Unit)? = if (onCountsAvailable != null) { total ->
                coordMutex!!.withLock {
                    datasetTotal = total
                    val s = sampleTotal
                    if (s != null && !countFired) { countFired = true; onCountsAvailable(s, total) }
                }
            } else null

            val s = async {
                if (cachedSamples != null) {
                    sOnTotal?.invoke(cachedSamples.size)
                    cachedSamples
                } else {
                    val result = ApiClient.service.getSamplesByProject(projectId, onTotalKnown = sOnTotal)
                    (result as? ApiResult.Success)?.data
                        ?.also {
                            CacheManager.cacheProjectSamples(projectId, it)
                            it.forEach { s -> CacheManager.cacheResourceType(s.uniqueId, "sample") }
                        } ?: emptyList()
                }
            }
            val d = async {
                if (cachedDatasets != null) {
                    dOnTotal?.invoke(cachedDatasets.size)
                    cachedDatasets
                } else {
                    val result = ApiClient.service.getDatasetsByProject(projectId, onTotalKnown = dOnTotal)
                    (result as? ApiResult.Success)?.data
                        ?.also {
                            CacheManager.cacheProjectDatasets(projectId, it)
                            it.forEach { ds -> CacheManager.cacheResourceType(ds.uniqueId, "dataset") }
                        } ?: emptyList()
                }
            }
            s.await() to d.await()
        }
    }
}
