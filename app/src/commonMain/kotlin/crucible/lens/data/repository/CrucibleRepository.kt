package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.ObservableCache
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.creationTimeOrEmpty
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.monthBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
 * dev/architecture.md's "Leaf-composable exception" for the three leaf-composable
 * exceptions that call [ApiClient]/[CacheManager] directly instead.
 */
class CrucibleRepository(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) {
    private val api get() = apiClient.service

    // Per-project mutex prevents duplicate concurrent fetches of the same project.
    private val projectFetchMutexes = mutableMapOf<String, Mutex>()

    private val resourceObservableCache = ObservableCache<String, CrucibleResource>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    /**
     * Fetches any resource by UUID using the unified /resources/{uuid} endpoint.
     * Single call — no type lookup needed, links and metadata included.
     *
     * [forceRefresh] skips the cache short-circuit and always hits the network, but — unlike
     * calling [invalidateResource] first — never evicts the existing cache entry beforehand.
     * Every [observeResource] collector keeps rendering the old value until [put] overwrites
     * it with the fresh result, so a pull-to-refresh never has a window where the resource is
     * momentarily absent (which previously collapsed every links/metadata-gated card and then
     * popped them back in once the new fetch landed).
     */
    suspend fun fetchResourceByUuid(uuid: String, forceRefresh: Boolean = false): ResourceResult = withContext(Dispatchers.Default) {
        try {
            val cached = resourceObservableCache.get(uuid)
            // Check if we have a fully-loaded cached version (with links)
            if (!forceRefresh && cached != null && hasLinks(cached)) {
                return@withContext ResourceResult.Success(cached)
            }

            when (val result = api.getResource(uuid)) {
                is ApiResult.Success -> {
                    val resource = result.data
                    resourceObservableCache.put(uuid, resource)
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

    /** Reactive read — emits the current cached resource (or null) and re-emits on any change. */
    fun observeResource(uuid: String): Flow<CrucibleResource?> = resourceObservableCache.observe(uuid)

    /** One-shot synchronous read — null if absent or expired. */
    fun getCachedResource(uuid: String): CrucibleResource? = resourceObservableCache.get(uuid)

    /** Evicts a single resource, forcing the next fetch to hit the network. */
    fun invalidateResource(uuid: String) = resourceObservableCache.invalidate(uuid)

    /** Age of the cached entry in milliseconds, or null if absent/expired. */
    fun resourceAgeMillis(uuid: String): Long? = resourceObservableCache.ageMillis(uuid)

    /** Returns true if the resource was loaded with links (i.e. from a detail fetch, not a list fetch). */
    private fun hasLinks(resource: CrucibleResource): Boolean = when (resource) {
        is Sample -> resource.links != null
        is Dataset -> resource.links != null
    }

    private val thumbnailObservableCache = ObservableCache<String, List<Thumbnail>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 20
    )

    suspend fun fetchThumbnails(datasetUuid: String, forceRefresh: Boolean = false): List<Thumbnail> = withContext(Dispatchers.Default) {
        if (!forceRefresh) {
            thumbnailObservableCache.get(datasetUuid)?.let { return@withContext it }
        }
        try {
            when (val result = api.getThumbnails(datasetUuid)) {
                is ApiResult.Success -> result.data.also { thumbnailObservableCache.put(datasetUuid, it) }
                is ApiResult.Error -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observeThumbnails(uuid: String): Flow<List<Thumbnail>?> = thumbnailObservableCache.observe(uuid)

    fun invalidateThumbnails(uuid: String) = thumbnailObservableCache.invalidate(uuid)

    private val projectsObservableCache = ObservableCache<Unit, List<Project>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 1
    )
    // Per-project entries, keyed by projectId — populated both in bulk (every project from a
    // fetchProjects() list fetch is written through here too) and individually (fetchProject()
    // for a single project, e.g. one found via discover-search that isn't in the member list).
    // This is what ProjectDetailScreen should observe for its cold-open header, instead of the
    // older CacheManager.getProjects() lookup, which only ever holds member projects and isn't
    // kept in sync with this cache.
    private val projectObservableCache = ObservableCache<String, Project>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )
    private val instrumentsObservableCache = ObservableCache<Unit, List<Instrument>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 1
    )

    /** Cache-first project list fetch. Caches on success. */
    suspend fun fetchProjects(forceRefresh: Boolean = false): ApiResult<List<Project>> {
        if (!forceRefresh) {
            projectsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return api.getProjects().also { result ->
            if (result is ApiResult.Success) {
                projectsObservableCache.put(Unit, result.data)
                result.data.forEach { projectObservableCache.put(it.projectId, it) }
            }
        }
    }

    fun observeProjects(): Flow<List<Project>?> = projectsObservableCache.observe(Unit)

    fun invalidateProjects() = projectsObservableCache.invalidate(Unit)

    /**
     * Fetches a single project by ID. Used both for a member project's detail screen (usually
     * already warm from [fetchProjects]) and for a non-member project found via discover-search
     * (never in the list cache, since the list endpoint only returns the caller's projects).
     *
     * [forceRefresh] behaves like [fetchResourceByUuid]'s — always hits the network but never
     * evicts the existing entry first, so observers keep the old value until the new one lands.
     */
    suspend fun fetchProject(projectId: String, forceRefresh: Boolean = false): ApiResult<Project> {
        if (!forceRefresh) {
            projectObservableCache.get(projectId)?.let { return ApiResult.Success(it) }
        }
        return api.getProject(projectId).also { result ->
            if (result is ApiResult.Success) projectObservableCache.put(projectId, result.data)
        }
    }

    /** Reactive read — emits the current cached project (or null) and re-emits on any change. */
    fun observeProject(projectId: String): Flow<Project?> = projectObservableCache.observe(projectId)

    /** One-shot synchronous read — null if absent or expired. */
    fun getCachedProject(projectId: String): Project? = projectObservableCache.get(projectId)

    fun invalidateProject(projectId: String) = projectObservableCache.invalidate(projectId)

    // Pending join-request count per project, for the lead-facing badge on Home/Projects list.
    // Only ever populated for projects the current user leads (DataSyncManager filters by
    // projectLeadOrcid before fetching) — GET /join_requests?group_name= is 403 for anyone else.
    private val pendingJoinRequestCountObservableCache = ObservableCache<String, Int>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    suspend fun fetchPendingJoinRequestCount(projectId: String, forceRefresh: Boolean = false): ApiResult<Int> {
        if (!forceRefresh) {
            pendingJoinRequestCountObservableCache.get(projectId)?.let { return ApiResult.Success(it) }
        }
        return when (val result = api.getJoinRequests(groupName = projectId, status = "pending")) {
            is ApiResult.Success -> result.data.size.also { pendingJoinRequestCountObservableCache.put(projectId, it) }
                .let { ApiResult.Success(it) }
            is ApiResult.Error -> result
        }
    }

    fun observePendingJoinRequestCount(projectId: String): Flow<Int?> = pendingJoinRequestCountObservableCache.observe(projectId)

    fun getCachedPendingJoinRequestCount(projectId: String): Int? = pendingJoinRequestCountObservableCache.get(projectId)

    /** Cache-first instrument list fetch. Caches on success. */
    suspend fun fetchInstruments(forceRefresh: Boolean = false): ApiResult<List<Instrument>> {
        if (!forceRefresh) {
            instrumentsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return api.getInstruments().also { result ->
            if (result is ApiResult.Success) instrumentsObservableCache.put(Unit, result.data)
        }
    }

    fun observeInstruments(): Flow<List<Instrument>?> = instrumentsObservableCache.observe(Unit)

    fun invalidateInstruments() = instrumentsObservableCache.invalidate(Unit)

    /**
     * Fetches samples and datasets for a project in parallel, using the cache when available.
     * A per-project mutex prevents duplicate concurrent fetches: if two callers request the same
     * project simultaneously, the second waits for the first to finish and then reads from cache.
     * Throws on network failure so callers can handle errors appropriately.
     *
     * [onCountsAvailable] fires as soon as both the sample total and dataset total are known
     * (from the first page of each paginated response), before full data is loaded.
     */
    private val projectSamplesObservableCache = ObservableCache<String, List<Sample>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
    private val projectDatasetsObservableCache = ObservableCache<String, List<Dataset>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )

    suspend fun fetchProjectData(
        projectId: String,
        forceRefresh: Boolean = false,
        onCountsAvailable: (suspend (Int, Int) -> Unit)? = null
    ): Pair<List<Sample>, List<Dataset>> {
        val mutex = projectFetchMutexes.getOrPut(projectId) { Mutex() }
        return mutex.withLock {
            val cachedSamples = if (!forceRefresh) projectSamplesObservableCache.get(projectId) else null
            val cachedDatasets = if (!forceRefresh) projectDatasetsObservableCache.get(projectId) else null
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
                        when (val result = api.getSamplesByProject(projectId, onTotalKnown = sOnTotal)) {
                            is ApiResult.Success -> result.data.also {
                                projectSamplesObservableCache.put(projectId, it)
                                it.forEach { s -> cacheManager.cacheResourceType(s.uniqueId, "sample") }
                            }
                            // Thrown (not swallowed to emptyList()) so a genuinely empty project
                            // is never confused with a failed fetch — callers already catch and
                            // handle generic exceptions (error card / retry, or best-effort
                            // preload failure counting).
                            is ApiResult.Error -> error("Failed to load samples: ${result.message}")
                        }
                    }
                }
                val d = async {
                    if (cachedDatasets != null) {
                        dOnTotal?.invoke(cachedDatasets.size)
                        cachedDatasets
                    } else {
                        when (val result = api.getDatasetsByProject(projectId, onTotalKnown = dOnTotal)) {
                            is ApiResult.Success -> result.data.also {
                                projectDatasetsObservableCache.put(projectId, it)
                                it.forEach { ds -> cacheManager.cacheResourceType(ds.uniqueId, "dataset") }
                            }
                            is ApiResult.Error -> error("Failed to load datasets: ${result.message}")
                        }
                    }
                }
                s.await() to d.await()
            }
        }
    }

    fun observeProjectSamples(projectId: String): Flow<List<Sample>?> =
        projectSamplesObservableCache.observe(projectId)

    fun observeProjectDatasets(projectId: String): Flow<List<Dataset>?> =
        projectDatasetsObservableCache.observe(projectId)

    /** One-shot synchronous reads — null if absent or expired. Used for cache-first renders. */
    fun getCachedProjectSamples(projectId: String): List<Sample>? = projectSamplesObservableCache.get(projectId)
    fun getCachedProjectDatasets(projectId: String): List<Dataset>? = projectDatasetsObservableCache.get(projectId)

    /** Age of the cached sample list for [projectId], in minutes — null if absent or expired. */
    fun projectDataAgeMinutes(projectId: String): Long? =
        projectSamplesObservableCache.ageMillis(projectId)?.let { it / 60000 }

    fun invalidateProjectData(projectId: String) {
        projectSamplesObservableCache.invalidate(projectId)
        projectDatasetsObservableCache.invalidate(projectId)
    }

    private val instrumentDatasetsObservableCache = ObservableCache<String, List<Dataset>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 15
    )

    /** Cache-first dataset-by-instrument fetch. Caches on success. */
    suspend fun fetchInstrumentDatasets(
        instrumentName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<Dataset>> {
        if (!forceRefresh) {
            instrumentDatasetsObservableCache.get(instrumentName)?.let { return ApiResult.Success(it) }
        }
        return api.getDatasetsByInstrument(instrumentName).also { result ->
            if (result is ApiResult.Success) instrumentDatasetsObservableCache.put(instrumentName, result.data)
        }
    }

    fun observeInstrumentDatasets(instrumentName: String): Flow<List<Dataset>?> =
        instrumentDatasetsObservableCache.observe(instrumentName)

    fun invalidateInstrumentDatasets(instrumentName: String) =
        instrumentDatasetsObservableCache.invalidate(instrumentName)

    /**
     * Resolves the sibling list for [resource] within its project, applying [groupBy]
     * filtering. Cache-first via the project sample/dataset list cache; falls back to a
     * filtered API call scoped to the group when no cached project list is available.
     * Always returns a list containing at least [resource] itself.
     */
    suspend fun fetchSiblings(resource: CrucibleResource, groupBy: String?, sortState: SortState = SortState()): List<CrucibleResource> {
        return when (resource) {
            is Sample -> {
                val projectId = resource.projectId ?: return listOf(resource)
                val cached = projectSamplesObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredSamples(
                            projectId = projectId,
                            sampleType = if (groupBy == null || groupBy == "TYPE") resource.sampleType else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> resp.data.ensureContains(resource, sortState)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.projectId ?: return listOf(resource)
                val cached = projectDatasetsObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredDatasets(
                            projectId = projectId,
                            measurement = if (groupBy == null || groupBy == "MEASUREMENT") resource.measurement else null,
                            instrumentName = if (groupBy == "INSTRUMENT") resource.instrumentName else null,
                            dataFormat = if (groupBy == "FORMAT") resource.dataFormat else null,
                            sessionName = if (groupBy == "SESSION") resource.sessionName else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> resp.data.ensureContains(resource, sortState)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
        }
    }
}

private fun <T : CrucibleResource> List<T>.ensureContains(resource: T, sortState: SortState): List<T> =
    (if (any { it.uniqueId == resource.uniqueId }) this else this + resource)
        .applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })

private fun List<Sample>.filterSiblings(groupBy: String?, resource: Sample) =
    filter { s -> when (groupBy) {
        "DATE"  -> crucible.lens.data.util.dateGroupKey(s.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "OWNER" -> s.ownerOrcid == resource.ownerOrcid
        else    -> s.sampleType == resource.sampleType
    } }

private fun List<Dataset>.filterSiblings(groupBy: String?, resource: Dataset) =
    filter { d -> when (groupBy) {
        "INSTRUMENT" -> d.instrumentName == resource.instrumentName
        "DATE"       -> crucible.lens.data.util.dateGroupKey(d.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "FORMAT"     -> d.dataFormat == resource.dataFormat
        "SESSION"    -> d.sessionName == resource.sessionName
        "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
        else         -> d.measurement == resource.measurement
    } }
