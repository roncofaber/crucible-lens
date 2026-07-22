package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Single point of contact between ViewModels and the network/cache layers.
 * Every ViewModel that fetches Crucible data goes through here — see
 * dev/ARCHITECTURE.md "Known architectural debt" for the three leaf-composable
 * exceptions that call [ApiClient]/[CacheManager] directly instead.
 */
class CrucibleRepository(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) {
    private val api get() = apiClient.service

    // Per-project mutex prevents duplicate concurrent fetches of the same project.
    private val projectFetchMutexes = mutableMapOf<String, Mutex>()

    /**
     * Fetches any resource by UUID using the unified /resources/{uuid} endpoint.
     * Single call — no type lookup needed, links and metadata included.
     */
    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.Default) {
        try {
            val cached = cacheManager.getResource(uuid)
            // Check if we have a fully-loaded cached version (with links)
            if (cached != null && hasLinks(cached)) {
                return@withContext ResourceResult.Success(cached)
            }

            when (val result = api.getResource(uuid)) {
                is ApiResult.Success -> {
                    val resource = result.data
                    cacheManager.cacheResource(uuid, resource)
                    ResourceResult.Success(resource)
                }
                is ApiResult.Error -> httpError(result.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResourceResult.Error("Network error: ${e.message ?: "check your connection"}")
        }
    }

    /** Returns true if the resource was loaded with links (i.e. from a detail fetch, not a list fetch). */
    private fun hasLinks(resource: CrucibleResource): Boolean = when (resource) {
        is Sample -> resource.links != null
        is Dataset -> resource.links != null
    }

    suspend fun fetchThumbnails(datasetUuid: String): List<Thumbnail> = withContext(Dispatchers.Default) {
        try {
            when (val result = api.getThumbnails(datasetUuid)) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Cache-first project list fetch. Caches on success. */
    suspend fun fetchProjects(forceRefresh: Boolean = false): ApiResult<List<Project>> {
        if (!forceRefresh) {
            cacheManager.getProjects()?.let { return ApiResult.Success(it) }
        }
        return api.getProjects().also { result ->
            if (result is ApiResult.Success) cacheManager.cacheProjects(result.data)
        }
    }

    /** Cache-first instrument list fetch. Caches on success. */
    suspend fun fetchInstruments(forceRefresh: Boolean = false): ApiResult<List<Instrument>> {
        if (!forceRefresh) {
            cacheManager.getInstruments()?.let { return ApiResult.Success(it) }
        }
        return api.getInstruments().also { result ->
            if (result is ApiResult.Success) cacheManager.cacheInstruments(result.data)
        }
    }

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
            val cachedSamples = if (!forceRefresh) cacheManager.getProjectSamples(projectId) else null
            val cachedDatasets = if (!forceRefresh) cacheManager.getProjectDatasets(projectId) else null
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
                        val result = api.getSamplesByProject(projectId, onTotalKnown = sOnTotal)
                        (result as? ApiResult.Success)?.data
                            ?.also {
                                cacheManager.cacheProjectSamples(projectId, it)
                                it.forEach { s -> cacheManager.cacheResourceType(s.uniqueId, "sample") }
                            } ?: emptyList()
                    }
                }
                val d = async {
                    if (cachedDatasets != null) {
                        dOnTotal?.invoke(cachedDatasets.size)
                        cachedDatasets
                    } else {
                        val result = api.getDatasetsByProject(projectId, onTotalKnown = dOnTotal)
                        (result as? ApiResult.Success)?.data
                            ?.also {
                                cacheManager.cacheProjectDatasets(projectId, it)
                                it.forEach { ds -> cacheManager.cacheResourceType(ds.uniqueId, "dataset") }
                            } ?: emptyList()
                    }
                }
                s.await() to d.await()
            }
        }
    }
}
