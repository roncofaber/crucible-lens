package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CachedProjectContent
import crucible.lens.data.cache.CacheEpoch
import crucible.lens.data.cache.ObservableCache
import crucible.lens.data.cache.ProjectCacheOwner
import crucible.lens.data.model.AssociatedFile
import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessPrincipalKind
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentCreateRequest
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.User
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.model.creationTimeOrEmpty
import crucible.lens.data.model.resolvedInstrumentName
import crucible.lens.data.model.resolvedInstrumentId
import crucible.lens.data.model.resolvedInstrumentReference
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.sync.SingleFlight
import crucible.lens.data.util.SortState
import crucible.lens.data.util.accessGrantComparator
import crucible.lens.data.util.accessGrantMutationTarget
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.isMfidReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class ResourceResult {
    data class Success(val resource: CrucibleResource) : ResourceResult()
    data class Error(val message: String) : ResourceResult()
    object Loading : ResourceResult()
}

data class InstrumentDatasetPage(
    val datasets: List<Dataset>,
    val nextCursor: String?
)

internal const val INSTRUMENT_DATASET_PAGE_SIZE = 100

internal fun mergeInstrumentDatasetPage(
    current: InstrumentDatasetPage?,
    incoming: List<Dataset>,
    nextCursor: String?,
    append: Boolean
): InstrumentDatasetPage {
    val datasets = if (append) current?.datasets.orEmpty() + incoming else incoming
    return InstrumentDatasetPage(
        datasets = datasets.distinctBy { it.uniqueId },
        nextCursor = nextCursor
    )
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
 * exceptions that call [ApiClient] directly instead.
 */
class CrucibleRepository(
    private val apiClient: ApiClient
) {
    private val api get() = apiClient.service
    private val cacheEpoch = CacheEpoch()
    private val activeCacheScope = MutableStateFlow<RepositoryCacheScope?>(null)

    private val projectFetchMutexes = mutableMapOf<String, Mutex>()
    private val projectFetchMutexesGuard = Mutex()

    fun activateCacheScope(accountId: String?) {
        val scope = RepositoryCacheScope(accountId, apiClient.getBaseUrl(), apiClient.getApiKey())
        if (activeCacheScope.value == scope) return
        activeCacheScope.value = scope
        invalidateAll()
    }

    internal fun captureCacheEpoch(): Long = cacheEpoch.capture()

    internal fun isCacheEpochCurrent(epoch: Long): Boolean = cacheEpoch.isCurrent(epoch)

    internal fun projectCacheOwner(accountId: String): ProjectCacheOwner =
        ProjectCacheOwner(accountId, apiClient.getBaseUrl())

    private suspend fun projectFetchMutex(projectId: String): Mutex =
        projectFetchMutexesGuard.withLock { projectFetchMutexes.getOrPut(projectId) { Mutex() } }

    private val resourceObservableCache = ObservableCache<String, CrucibleResource>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    private val resourceAccessObservableCache = ObservableCache<String, List<AccessGrant>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
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
        val epoch = cacheEpoch.capture()
        val service = api
        try {
            val cached = resourceObservableCache.get(uuid)
            // Check if we have a fully-loaded cached version (with links)
            if (!forceRefresh && cached != null && hasLinks(cached)) {
                return@withContext ResourceResult.Success(cached)
            }

            when (val result = service.getResource(uuid)) {
                is ApiResult.Success -> {
                    val resource = result.data
                    if (cacheEpoch.isCurrent(epoch)) resourceObservableCache.put(uuid, resource)
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

    fun observeResource(uuid: String): Flow<CrucibleResource?> = combine(
        resourceObservableCache.observe(uuid),
        persistedProjectData
    ) { detail, persisted -> detail ?: persisted.findResource(uuid) }

    fun getCachedResource(uuid: String): CrucibleResource? =
        resourceObservableCache.peek(uuid) ?: persistedProjectData.value.findResource(uuid)

    fun cacheResource(uuid: String, resource: CrucibleResource, epoch: Long = cacheEpoch.capture()) {
        if (!cacheEpoch.isCurrent(epoch)) return
        resourceObservableCache.put(uuid, preserveResourceDetailFields(resourceObservableCache.peek(uuid), resource))
    }

    /** Evicts a single resource, forcing the next fetch to hit the network. */
    fun invalidateResource(uuid: String) = resourceObservableCache.invalidate(uuid)

    suspend fun fetchResourceAccess(resourceMfid: String, forceRefresh: Boolean = false): ApiResult<List<AccessGrant>> {
        val epoch = cacheEpoch.capture()
        if (!forceRefresh) {
            resourceAccessObservableCache.get(resourceMfid)?.let { return ApiResult.Success(it) }
        }
        return api.getResourceAccess(resourceMfid).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) {
                resourceAccessObservableCache.put(resourceMfid, result.data)
            }
        }
    }

    fun getCachedResourceAccess(resourceMfid: String): List<AccessGrant>? =
        resourceAccessObservableCache.get(resourceMfid)

    suspend fun setResourceAccess(
        resourceMfid: String,
        kind: AccessPrincipalKind,
        principal: String,
        permission: ResourceGrantRole
    ): ApiResult<AccessGrant> {
        val epoch = cacheEpoch.capture()
        return api.setResourceAccess(resourceMfid, kind, principal, permission).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) {
                val current = resourceAccessObservableCache.peek(resourceMfid).orEmpty()
                resourceAccessObservableCache.put(
                    resourceMfid,
                    (current.filterNot { it.principalId == result.data.principalId } + result.data)
                        .sortedWith(accessGrantComparator)
                )
            }
        }
    }

    suspend fun revokeResourceAccess(resourceMfid: String, grant: AccessGrant): ApiResult<Boolean> {
        val target = accessGrantMutationTarget(grant)
            ?: return ApiResult.Error(422, "This grant cannot be revoked through the client")
        val epoch = cacheEpoch.capture()
        return api.revokeResourceAccess(resourceMfid, target.kind, target.principal).also { result ->
            if (result is ApiResult.Success && result.data && cacheEpoch.isCurrent(epoch)) {
                val current = resourceAccessObservableCache.peek(resourceMfid).orEmpty()
                resourceAccessObservableCache.put(resourceMfid, current.filterNot { it.principalId == grant.principalId })
            }
        }
    }

    suspend fun setResourcePublic(resourceMfid: String, isPublic: Boolean): ApiResult<AccessGrant?> {
        val epoch = cacheEpoch.capture()
        return if (isPublic) {
            when (val result = api.publishResource(resourceMfid)) {
                is ApiResult.Success -> {
                    if (cacheEpoch.isCurrent(epoch)) {
                        val current = resourceAccessObservableCache.peek(resourceMfid).orEmpty()
                        resourceAccessObservableCache.put(
                            resourceMfid,
                            (current.filterNot { it.principalType == AccessPrincipalType.Public } + result.data)
                                .sortedWith(accessGrantComparator)
                        )
                        updateCachedResourceVisibility(resourceMfid, true)
                    }
                    ApiResult.Success(result.data)
                }
                is ApiResult.Error -> result
            }
        } else {
            when (val result = api.unpublishResource(resourceMfid)) {
                is ApiResult.Success -> {
                    if (cacheEpoch.isCurrent(epoch)) {
                        val current = resourceAccessObservableCache.peek(resourceMfid).orEmpty()
                        resourceAccessObservableCache.put(resourceMfid, current.filterNot { it.principalType == AccessPrincipalType.Public })
                        updateCachedResourceVisibility(resourceMfid, false)
                    }
                    ApiResult.Success(null)
                }
                is ApiResult.Error -> result
            }
        }
    }

    private fun updateCachedResourceVisibility(resourceMfid: String, isPublic: Boolean) {
        val updated = when (val resource = resourceObservableCache.peek(resourceMfid)) {
            is Sample -> resource.copy(isPublic = isPublic)
            is Dataset -> resource.copy(isPublic = isPublic)
            null -> null
        }
        if (updated != null) resourceObservableCache.put(resourceMfid, updated)
    }

    /** Age of the cached entry in milliseconds, or null if absent/expired. */
    fun resourceAgeMillis(uuid: String): Long? = resourceObservableCache.ageMillis(uuid)

    // uuid -> "sample"/"dataset", so screens that only have a UUID (e.g. History) can avoid a
    // type-check API call. Populated by fetchProjectData's list fetches.
    private val resourceTypeObservableCache = ObservableCache<String, String>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    fun cacheResourceType(uuid: String, type: String) = resourceTypeObservableCache.put(uuid, type)

    fun getCachedResourceType(uuid: String): String? = resourceTypeObservableCache.get(uuid)

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
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            thumbnailObservableCache.get(datasetUuid)?.let { return@withContext it }
        }
        try {
            when (val result = service.getThumbnails(datasetUuid)) {
                is ApiResult.Success -> result.data.also {
                    if (cacheEpoch.isCurrent(epoch)) thumbnailObservableCache.put(datasetUuid, it)
                }
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
    private val projectsFetchFlight = SingleFlight<Long, ApiResult<List<Project>>>()
    private val projectObservableCache = ObservableCache<String, Project>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )
    private val persistedProjects = MutableStateFlow<List<Project>>(emptyList())
    private val instrumentsObservableCache = ObservableCache<InstrumentStatus, List<Instrument>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = InstrumentStatus.entries.size
    )
    private val instrumentObservableCache = ObservableCache<String, Instrument>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
    private val instrumentsFetchFlight = SingleFlight<Pair<Long, InstrumentStatus>, ApiResult<List<Instrument>>>()

    /** Cache-first project list fetch. Caches on success. */
    suspend fun fetchProjects(forceRefresh: Boolean = false): ApiResult<List<Project>> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            projectsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return projectsFetchFlight.run(epoch) {
            service.getProjects().also { result ->
                if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) seedProjects(result.data)
            }
        }
    }

    fun observeProjects(): Flow<List<Project>?> = combine(
        projectsObservableCache.observe(Unit),
        persistedProjects
    ) { live, persisted -> live ?: persisted.takeIf { it.isNotEmpty() } }

    fun getCachedProjects(): List<Project>? =
        projectsObservableCache.peek(Unit) ?: persistedProjects.value.takeIf { it.isNotEmpty() }

    fun observeLiveProjects(): Flow<List<Project>?> = projectsObservableCache.observe(Unit)

    fun getCachedLiveProjects(): List<Project>? = projectsObservableCache.peek(Unit)

    fun seedProjects(projects: List<Project>) {
        projectsObservableCache.put(Unit, projects)
    }

    fun seedPersistedProjects(projects: List<Project>) {
        persistedProjects.value = projects
    }

    internal fun seedPersistedProjects(projects: List<Project>, epoch: Long) {
        if (cacheEpoch.isCurrent(epoch)) seedPersistedProjects(projects)
    }

    fun invalidateProjects() = projectsObservableCache.invalidate(Unit)

    /** Age of the cached project list, in minutes — null if absent or expired. */
    fun projectsAgeMinutes(): Long? = projectsObservableCache.ageMillis(Unit)?.let { it / 60000 }

    /**
     * Fetches a single project by ID. Used both for a member project's detail screen (usually
     * already warm from [fetchProjects]) and for a non-member project found via discover-search
     * (never in the list cache, since the list endpoint only returns the caller's projects).
     *
     * [forceRefresh] behaves like [fetchResourceByUuid]'s — always hits the network but never
     * evicts the existing entry first, so observers keep the old value until the new one lands.
     */
    suspend fun fetchProject(projectReference: String, forceRefresh: Boolean = false): ApiResult<Project> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh && isMfidReference(projectReference)) {
            projectObservableCache.get(projectReference)?.let { return ApiResult.Success(it) }
        }
        return service.getProject(projectReference).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) {
                projectObservableCache.put(result.data.uniqueId, result.data)
            }
        }
    }

    fun observeProject(projectMfid: String): Flow<Project?> = combine(
        projectObservableCache.observe(projectMfid),
        projectsObservableCache.observe(Unit),
        persistedProjects
    ) { detail, liveProjects, persisted ->
        detail
            ?: liveProjects?.find { it.uniqueId == projectMfid }
            ?: persisted.find { it.uniqueId == projectMfid }
    }

    fun getCachedProject(projectMfid: String): Project? =
        projectObservableCache.peek(projectMfid)
            ?: projectsObservableCache.peek(Unit)?.find { it.uniqueId == projectMfid }
            ?: persistedProjects.value.find { it.uniqueId == projectMfid }

    fun invalidateProject(projectId: String) = projectObservableCache.invalidate(projectId)

    // Per-project member list — fetched alongside the project itself (see ProjectDetailScreen's
    // load effect) so both the collapsing header's member count and owner-groupby name resolution
    // (rememberOwnerNames) share one cached fetch instead of hitting GET /projects/{id}/users twice.
    private val projectMembersObservableCache = ObservableCache<String, List<User>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    suspend fun fetchProjectMembers(projectId: String, forceRefresh: Boolean = false): ApiResult<List<User>> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            projectMembersObservableCache.get(projectId)?.let { return ApiResult.Success(it) }
        }
        return service.getProjectUsers(projectId).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) projectMembersObservableCache.put(projectId, result.data)
        }
    }

    fun observeProjectMembers(projectId: String): Flow<List<User>?> = projectMembersObservableCache.observe(projectId)

    fun getCachedProjectMembers(projectId: String): List<User>? = projectMembersObservableCache.get(projectId)

    fun invalidateProjectMembers(projectId: String) = projectMembersObservableCache.invalidate(projectId)

    private val myJoinRequestsObservableCache = ObservableCache<Unit, List<JoinRequest>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 1
    )

    suspend fun fetchMyJoinRequests(forceRefresh: Boolean = false): ApiResult<List<JoinRequest>> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            myJoinRequestsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return service.getMyJoinRequests().also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) myJoinRequestsObservableCache.put(Unit, result.data)
        }
    }

    fun observeMyJoinRequests(): Flow<List<JoinRequest>?> = myJoinRequestsObservableCache.observe(Unit)

    fun getCachedMyJoinRequests(): List<JoinRequest>? = myJoinRequestsObservableCache.get(Unit)

    internal fun cacheMyJoinRequests(requests: List<JoinRequest>) = myJoinRequestsObservableCache.put(Unit, requests)

    fun updateCachedMyJoinRequest(request: JoinRequest, epoch: Long = cacheEpoch.capture()) {
        if (!cacheEpoch.isCurrent(epoch)) return
        val cached = myJoinRequestsObservableCache.get(Unit) ?: return
        myJoinRequestsObservableCache.put(Unit, cached.filterNot { it.id == request.id } + request)
    }

    fun invalidateMyJoinRequests() = myJoinRequestsObservableCache.invalidate(Unit)

    // Pending join-request count per project, for the lead-facing badge on Home/Projects list.
    // Only ever populated for projects the current user leads (DataSyncManager filters by
    // projectLeadOrcid before fetching). GET /join_requests with no group_name now auto-scopes
    // to the caller's own led projects server-side (non-admins get 403 there before this — see
    // dev/architecture.md), so one call covers every led project instead of one call each.
    private val pendingJoinRequestCountObservableCache = ObservableCache<String, Int>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    /**
     * Fetches pending join-request counts for every project in [ledProjectIds] in a single
     * request. Projects with zero pending requests are written as 0 (not left absent), so a
     * resolved request clears its badge on the next sync instead of staying stuck.
     */
    suspend fun fetchPendingJoinRequestCounts(ledProjectIds: Collection<String>): ApiResult<Map<String, Int>> {
        val epoch = cacheEpoch.capture()
        val service = api
        return when (val result = service.getJoinRequests(status = "pending")) {
            is ApiResult.Success -> {
                val counts = result.data.groupingBy { it.groupName }.eachCount()
                if (cacheEpoch.isCurrent(epoch)) {
                    ledProjectIds.forEach { projectId ->
                        pendingJoinRequestCountObservableCache.put(projectId, counts[projectId] ?: 0)
                    }
                }
                ApiResult.Success(counts)
            }
            is ApiResult.Error -> result
        }
    }

    fun observePendingJoinRequestCount(projectId: String): Flow<Int?> = pendingJoinRequestCountObservableCache.observe(projectId)

    fun getCachedPendingJoinRequestCount(projectId: String): Int? = pendingJoinRequestCountObservableCache.get(projectId)

    /** Cache-first instrument list fetch. Caches on success. */
    suspend fun fetchInstruments(
        forceRefresh: Boolean = false,
        status: InstrumentStatus = InstrumentStatus.Active
    ): ApiResult<List<Instrument>> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            instrumentsObservableCache.get(status)?.let { return ApiResult.Success(it) }
        }
        return instrumentsFetchFlight.run(epoch to status) {
            service.getInstruments(status).also { result ->
                if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) instrumentsObservableCache.put(status, result.data)
            }
        }
    }

    fun observeInstruments(status: InstrumentStatus = InstrumentStatus.Active): Flow<List<Instrument>?> = instrumentsObservableCache.observe(status)

    /** One-shot synchronous read — null if absent or expired. */
    fun getCachedInstruments(status: InstrumentStatus = InstrumentStatus.Active): List<Instrument>? = instrumentsObservableCache.get(status)

    suspend fun fetchInstrument(instrumentReference: String, forceRefresh: Boolean = false): ApiResult<Instrument> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh && isMfidReference(instrumentReference)) {
            instrumentObservableCache.get(instrumentReference)?.let { return ApiResult.Success(it) }
            InstrumentStatus.entries.firstNotNullOfOrNull { status ->
                instrumentsObservableCache.get(status)?.firstOrNull { it.uniqueId == instrumentReference }
            }?.let { return ApiResult.Success(it) }
        }
        return service.getInstrument(instrumentReference).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) {
                instrumentObservableCache.put(result.data.uniqueId, result.data)
            }
        }
    }

    fun getCachedInstrument(instrumentMfid: String): Instrument? =
        instrumentObservableCache.peek(instrumentMfid)
            ?: InstrumentStatus.entries.firstNotNullOfOrNull { status ->
                instrumentsObservableCache.peek(status)?.firstOrNull { it.uniqueId == instrumentMfid }
            }

    fun cacheInstrumentDetail(instrument: Instrument) = instrumentObservableCache.put(instrument.uniqueId, instrument)

    fun invalidateInstruments() = instrumentsObservableCache.invalidateAll()

    fun invalidateInstrument(instrumentMfid: String) = instrumentObservableCache.invalidate(instrumentMfid)

    suspend fun createInstrument(request: InstrumentCreateRequest): ApiResult<Instrument> {
        val epoch = cacheEpoch.capture()
        return api.createInstrument(request).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) {
                instrumentObservableCache.put(result.data.uniqueId, result.data)
                instrumentsObservableCache.invalidateAll()
            }
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
    private val projectSamplesObservableCache = ObservableCache<String, List<Sample>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
    private val projectDatasetsObservableCache = ObservableCache<String, List<Dataset>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
    private val persistedProjectData = MutableStateFlow<Map<String, CachedProjectContent>>(emptyMap())

    private fun persistedProjectContent(projectReference: String): CachedProjectContent? =
        persistedProjectData.value[projectReference]
            ?: persistedProjectData.value.values.firstOrNull { it.projectSlug == projectReference }

    fun seedPersistedProjectData(content: CachedProjectContent) {
        val previousSlug = persistedProjectData.value[content.projectMfid]?.projectSlug
        if (previousSlug != null && previousSlug != content.projectSlug) {
            projectSamplesObservableCache.invalidate(previousSlug)
            projectDatasetsObservableCache.invalidate(previousSlug)
        }
        persistedProjectData.update { it + (content.projectMfid to content) }
        projectSamplesObservableCache.put(content.projectSlug, content.samples)
        projectDatasetsObservableCache.put(content.projectSlug, content.datasets)
        content.samples.forEach {
            cacheResourceType(it.uniqueId, "sample")
        }
        content.datasets.forEach {
            cacheResourceType(it.uniqueId, "dataset")
        }
    }

    internal fun seedPersistedProjectData(content: CachedProjectContent, epoch: Long) {
        if (cacheEpoch.isCurrent(epoch)) seedPersistedProjectData(content)
    }

    fun hasPersistedProjectData(projectMfid: String): Boolean = projectMfid in persistedProjectData.value

    fun removePersistedProjectData(projectMfid: String) {
        val slug = persistedProjectData.value[projectMfid]?.projectSlug
        persistedProjectData.update { it - projectMfid }
        if (slug != null) {
            projectSamplesObservableCache.invalidate(slug)
            projectDatasetsObservableCache.invalidate(slug)
        }
    }

    fun retainPersistedProjectData(projectMfids: Set<String>) {
        val removed = persistedProjectData.value.filterKeys { it !in projectMfids }.values
        persistedProjectData.update { data -> data.filterKeys { it in projectMfids } }
        removed.forEach {
            projectSamplesObservableCache.invalidate(it.projectSlug)
            projectDatasetsObservableCache.invalidate(it.projectSlug)
        }
    }

    suspend fun fetchProjectData(
        projectMfid: String,
        projectSlug: String,
        forceRefresh: Boolean = false,
        onCountsAvailable: (suspend (Int, Int) -> Unit)? = null
    ): Pair<List<Sample>, List<Dataset>> {
        val epoch = cacheEpoch.capture()
        val service = api
        val mutex = projectFetchMutex(projectMfid)
        return mutex.withLock {
            val cachedSamples = if (!forceRefresh) projectSamplesObservableCache.get(projectSlug) else null
            val cachedDatasets = if (!forceRefresh) projectDatasetsObservableCache.get(projectSlug) else null
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
                        when (val result = service.getSamplesByProject(projectMfid, onTotalKnown = sOnTotal)) {
                            is ApiResult.Success -> result.data.map { it.copy(projectRelation = null) }
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
                        when (val result = service.getDatasetsByProject(projectMfid, onTotalKnown = dOnTotal)) {
                            is ApiResult.Success -> result.data.map { it.copy(projectRelation = null) }
                            is ApiResult.Error -> error("Failed to load datasets: ${result.message}")
                        }
                    }
                }
                val samples = s.await()
                val datasets = d.await()
                if (cacheEpoch.isCurrent(epoch)) {
                    projectSamplesObservableCache.put(projectSlug, samples)
                    projectDatasetsObservableCache.put(projectSlug, datasets)
                    samples.forEach { cacheResourceType(it.uniqueId, "sample") }
                    datasets.forEach { cacheResourceType(it.uniqueId, "dataset") }
                }
                samples to datasets
            }
        }
    }

    fun observeProjectSamples(projectId: String): Flow<List<Sample>?> =
        projectSamplesObservableCache.observe(projectId)

    fun observeProjectDatasets(projectId: String): Flow<List<Dataset>?> =
        projectDatasetsObservableCache.observe(projectId)

    /** One-shot synchronous reads — null if absent or expired. Used for cache-first renders. */
    fun getCachedProjectSamples(projectId: String): List<Sample>? =
        projectSamplesObservableCache.get(projectId) ?: persistedProjectContent(projectId)?.samples

    fun getCachedProjectDatasets(projectId: String): List<Dataset>? =
        projectDatasetsObservableCache.get(projectId) ?: persistedProjectContent(projectId)?.datasets

    /** Age of the cached sample list for [projectId], in minutes — null if absent or expired. */
    fun projectDataAgeMinutes(projectId: String): Long? = persistedProjectContent(projectId)
        ?.let { (kotlin.time.Clock.System.now().toEpochMilliseconds() - it.cachedAt).coerceAtLeast(0L) / 60000 }
        ?: projectSamplesObservableCache.ageMillis(projectId)?.let { it / 60000 }

    fun invalidateProjectData(projectId: String, epoch: Long = cacheEpoch.capture()) {
        if (!cacheEpoch.isCurrent(epoch)) return
        projectSamplesObservableCache.invalidate(projectId)
        projectDatasetsObservableCache.invalidate(projectId)
        persistedProjectData.update { data ->
            data.filterValues { it.projectMfid != projectId && it.projectSlug != projectId }
        }
    }

    private val instrumentDatasetsObservableCache = ObservableCache<String, InstrumentDatasetPage>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 15
    )

    suspend fun fetchInstrumentDatasets(
        instrumentMfid: String,
        cursor: String? = null,
        forceRefresh: Boolean = false
    ): ApiResult<InstrumentDatasetPage> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (cursor == null && !forceRefresh) {
            instrumentDatasetsObservableCache.get(instrumentMfid)?.let { return ApiResult.Success(it) }
        }
        return when (val result = service.getInstrumentDatasetsPage(instrumentMfid, INSTRUMENT_DATASET_PAGE_SIZE, cursor)) {
            is ApiResult.Success -> {
                val page = mergeInstrumentDatasetPage(
                    current = instrumentDatasetsObservableCache.peek(instrumentMfid),
                    incoming = result.data.items,
                    nextCursor = result.data.nextCursor,
                    append = cursor != null
                )
                if (cacheEpoch.isCurrent(epoch)) instrumentDatasetsObservableCache.put(instrumentMfid, page)
                ApiResult.Success(page)
            }
            is ApiResult.Error -> result
        }
    }

    fun observeInstrumentDatasets(instrumentMfid: String): Flow<InstrumentDatasetPage?> =
        instrumentDatasetsObservableCache.observe(instrumentMfid)

    fun getCachedInstrumentDatasets(instrumentMfid: String): InstrumentDatasetPage? =
        instrumentDatasetsObservableCache.get(instrumentMfid)

    fun invalidateInstrumentDatasets(instrumentMfid: String) =
        instrumentDatasetsObservableCache.invalidate(instrumentMfid)

    /**
     * Resolves the sibling list for [resource] within its project, applying [groupBy]
     * filtering. Cache-first via the project sample/dataset list cache; falls back to a
     * filtered API call scoped to the group when no cached project list is available.
     * Always returns a list containing at least [resource] itself.
     */
    suspend fun fetchSiblings(resource: CrucibleResource, groupBy: String?, sortState: SortState = SortState()): List<CrucibleResource> {
        return when (resource) {
            is Sample -> {
                val projectId = resource.resolvedProjectId ?: return listOf(resource)
                val cached = projectSamplesObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                } else {
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredSamples(
                            projectId = projectId,
                            sampleType = if (groupBy == null || groupBy == "TYPE") resource.sampleType else null,
                            ownerId = if (groupBy == "OWNER") resource.ownerOrcid else null
                        )
                    }) {
                        is ApiResult.Success -> resp.data.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.resolvedProjectId ?: return listOf(resource)
                val cached = projectDatasetsObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                } else {
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredDatasets(
                            projectId = projectId,
                            measurement = if (groupBy == null || groupBy == "MEASUREMENT") resource.measurement else null,
                            instrumentMfid = if (groupBy == "INSTRUMENT") resource.resolvedInstrumentReference?.takeIf(::isMfidReference) else null,
                            instrumentName = if (groupBy == "INSTRUMENT" && resource.resolvedInstrumentReference?.let(::isMfidReference) != true) resource.resolvedInstrumentName else null,
                            dataFormat = if (groupBy == "FORMAT") resource.dataFormat else null,
                            sessionName = if (groupBy == "SESSION") resource.sessionName else null,
                            ownerId = if (groupBy == "OWNER") resource.ownerOrcid else null
                        )
                    }) {
                        is ApiResult.Success -> resp.data.filterSiblings(groupBy, resource).ensureContains(resource, sortState)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
        }
    }

    private val datasetFilesObservableCache = ObservableCache<String, List<AssociatedFile>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )

    /** Cache-first associated-files list fetch for a dataset. Caches on success. */
    suspend fun fetchDatasetFiles(datasetUuid: String, forceRefresh: Boolean = false): ApiResult<List<AssociatedFile>> {
        val epoch = cacheEpoch.capture()
        val service = api
        if (!forceRefresh) {
            datasetFilesObservableCache.get(datasetUuid)?.let { return ApiResult.Success(it) }
        }
        return service.getDatasetFiles(datasetUuid).also { result ->
            if (result is ApiResult.Success && cacheEpoch.isCurrent(epoch)) datasetFilesObservableCache.put(datasetUuid, result.data)
        }
    }

    fun getCachedDatasetFiles(datasetUuid: String): List<AssociatedFile>? = datasetFilesObservableCache.get(datasetUuid)

    fun invalidateDatasetFiles(datasetUuid: String) = datasetFilesObservableCache.invalidate(datasetUuid)

    /**
     * Always fetches a fresh signed download URL — never cached. This is only ever called
     * on-demand (user taps share/download), not from a background preload, and reusing a
     * stale-but-not-yet-expired signed URL has no real upside over just asking again.
     */
    suspend fun fetchFileUrl(mfid: String): ApiResult<String> = when (val result = api.getFileDownloadLink(mfid)) {
        is ApiResult.Success -> ApiResult.Success(result.data.url)
        is ApiResult.Error -> result
    }

    data class CacheStats(
        val projectCount: Int,
        val instrumentCount: Int,
        val resourceCount: Int,
        val cachedSampleCount: Int,
        val cachedDatasetCount: Int,
        val datasetFileCount: Int
    )

    /** Snapshot of what's currently cached, for the Cache settings screen. */
    fun getCacheStats(): CacheStats = CacheStats(
        projectCount = getCachedProjects()?.size ?: 0,
        instrumentCount = getCachedInstruments()?.size ?: 0,
        resourceCount = resourceObservableCache.size,
        cachedSampleCount = projectSamplesObservableCache.size,
        cachedDatasetCount = projectDatasetsObservableCache.size,
        datasetFileCount = datasetFilesObservableCache.size
    )

    /** Evicts every cache this repository owns — for logout, API key change, or a manual "clear cache" action. */
    fun invalidateAll() {
        cacheEpoch.advance()
        resourceObservableCache.invalidateAll()
        resourceAccessObservableCache.invalidateAll()
        resourceTypeObservableCache.invalidateAll()
        thumbnailObservableCache.invalidateAll()
        projectsObservableCache.invalidateAll()
        projectObservableCache.invalidateAll()
        persistedProjects.value = emptyList()
        projectMembersObservableCache.invalidateAll()
        myJoinRequestsObservableCache.invalidateAll()
        instrumentsObservableCache.invalidateAll()
        instrumentObservableCache.invalidateAll()
        instrumentDatasetsObservableCache.invalidateAll()
        projectSamplesObservableCache.invalidateAll()
        projectDatasetsObservableCache.invalidateAll()
        persistedProjectData.value = emptyMap()
        pendingJoinRequestCountObservableCache.invalidateAll()
        datasetFilesObservableCache.invalidateAll()
    }
}

private data class RepositoryCacheScope(
    val accountId: String?,
    val serverUrl: String,
    val apiKey: String
)

private fun <T : CrucibleResource> List<T>.ensureContains(resource: T, sortState: SortState): List<T> =
    (if (any { it.uniqueId == resource.uniqueId }) this else this + resource)
        .applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })

private fun Map<String, CachedProjectContent>.findResource(uuid: String): CrucibleResource? {
    values.forEach { content ->
        content.samples.find { it.uniqueId == uuid }?.let { return it }
        content.datasets.find { it.uniqueId == uuid }?.let { return it }
    }
    return null
}

private fun preserveResourceDetailFields(existing: CrucibleResource?, incoming: CrucibleResource): CrucibleResource = when {
    existing is Sample && incoming is Sample -> incoming.copy(
        scientificMetadata = incoming.scientificMetadata ?: existing.scientificMetadata,
        datasets = incoming.datasets ?: existing.datasets,
        links = incoming.links ?: existing.links,
        owner = incoming.owner ?: existing.owner,
        project = incoming.project ?: existing.project
    )
    existing is Dataset && incoming is Dataset -> incoming.copy(
        scientificMetadata = incoming.scientificMetadata ?: existing.scientificMetadata,
        links = incoming.links ?: existing.links,
        owner = incoming.owner ?: existing.owner,
        instrument = incoming.instrument ?: existing.instrument,
        project = incoming.project ?: existing.project
    )
    else -> incoming
}

private fun List<Sample>.filterSiblings(groupBy: String?, resource: Sample) =
    filter { s -> when (groupBy) {
        "DATE"  -> crucible.lens.data.util.dateGroupKey(s.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "OWNER" -> s.ownerOrcid == resource.ownerOrcid
        else    -> s.sampleType == resource.sampleType
    } }

private fun List<Dataset>.filterSiblings(groupBy: String?, resource: Dataset) =
    filter { d -> when (groupBy) {
        "INSTRUMENT" -> when {
            d.instrument != null && resource.instrument != null -> d.instrument.uniqueId == resource.instrument.uniqueId
            d.resolvedInstrumentId != null && resource.resolvedInstrumentId != null -> d.resolvedInstrumentId == resource.resolvedInstrumentId
            else -> d.resolvedInstrumentName == resource.resolvedInstrumentName
        }
        "DATE"       -> crucible.lens.data.util.dateGroupKey(d.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "FORMAT"     -> d.dataFormat == resource.dataFormat
        "SESSION"    -> d.sessionName == resource.sessionName
        "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
        else         -> d.measurement == resource.measurement
    } }
