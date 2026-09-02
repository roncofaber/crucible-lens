package crucible.lens.data.sync

import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CachedProjectContent
import crucible.lens.data.cache.ProjectCacheOwner
import crucible.lens.data.cache.ProjectContentDelta
import crucible.lens.data.cache.applyDelta
import crucible.lens.data.cache.requireFullRefresh
import crucible.lens.data.model.Project
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class ProjectSyncTarget(
    val projectMfid: String,
    val projectSlug: String
)

fun Project.toSyncTarget(): ProjectSyncTarget = ProjectSyncTarget(uniqueId, projectId)

class DataSyncManager(private val repository: CrucibleRepository) {
    private val projectMutexes = mutableMapOf<String, Mutex>()
    private val projectMutexesGuard = Mutex()
    private val projectSyncFlight = SingleFlight<ProjectSyncKey, CachedProjectContent>()
    private val overviewFlight = SingleFlight<OverviewKey, ApiResult<List<Project>>>()
    private val fullSyncFlight = SingleFlight<FullSyncKey, Unit>()
    private val _projectStates = MutableStateFlow<Map<String, ProjectSyncState>>(emptyMap())
    val projectStates: StateFlow<Map<String, ProjectSyncState>> = _projectStates.asStateFlow()
    private var activeOwner: ProjectCacheOwner? = null

    fun activateCacheScope(accountId: String?) {
        val owner = accountId?.let(repository::projectCacheOwner)
        if (activeOwner == owner) return
        activeOwner = owner
        _projectStates.value = emptyMap()
    }

    private suspend fun projectMutex(projectMfid: String): Mutex =
        projectMutexesGuard.withLock { projectMutexes.getOrPut(projectMfid) { Mutex() } }

    suspend fun restoreProjects(context: PlatformContext, accountId: String, projectMfids: Set<String>) =
        restoreProjects(PlatformProjectCacheStore(context), accountId, projectMfids)

    internal suspend fun restoreProjects(store: ProjectCacheStore, accountId: String, projectMfids: Set<String>) {
        val epoch = repository.captureCacheEpoch()
        val owner = repository.projectCacheOwner(accountId)
        store.loadContents(owner, projectMfids)
            .forEach { repository.seedPersistedProjectData(it, epoch) }
    }

    suspend fun restoreProject(context: PlatformContext, accountId: String, projectMfid: String): CachedProjectContent? =
        restoreProject(PlatformProjectCacheStore(context), accountId, projectMfid)

    internal suspend fun restoreProject(store: ProjectCacheStore, accountId: String, projectMfid: String): CachedProjectContent? {
        val epoch = repository.captureCacheEpoch()
        return store.loadContent(repository.projectCacheOwner(accountId), projectMfid)
            ?.also { repository.seedPersistedProjectData(it, epoch) }
    }

    suspend fun restoreProjectSummaries(context: PlatformContext, accountId: String): List<Project>? =
        restoreProjectSummaries(PlatformProjectCacheStore(context), accountId)

    internal suspend fun restoreProjectSummaries(store: ProjectCacheStore, accountId: String): List<Project>? {
        val epoch = repository.captureCacheEpoch()
        return store.loadProjects(repository.projectCacheOwner(accountId))
            ?.also { repository.seedPersistedProjects(it, epoch) }
    }

    suspend fun refreshOverview(context: PlatformContext, accountId: String): ApiResult<List<Project>> =
        refreshOverview(PlatformProjectCacheStore(context), accountId)

    internal suspend fun refreshOverview(store: ProjectCacheStore, accountId: String): ApiResult<List<Project>> {
        val owner = repository.projectCacheOwner(accountId)
        val epoch = repository.captureCacheEpoch()
        return overviewFlight.run(OverviewKey(owner, epoch)) {
            coroutineScope {
                val projectsDeferred = async { repository.fetchProjects(forceRefresh = true) }
                val instrumentsDeferred = async { repository.fetchInstruments(forceRefresh = true) }
                val projects = projectsDeferred.await()
                instrumentsDeferred.await()
                if (projects is ApiResult.Success && repository.isCacheEpochCurrent(epoch)) {
                    store.saveProjects(owner, projects.data)
                }
                projects
            }
        }
    }

    suspend fun syncProject(
        context: PlatformContext,
        accountId: String,
        target: ProjectSyncTarget,
        forceRefresh: Boolean = false
    ): CachedProjectContent = syncProject(
        PlatformProjectCacheStore(context),
        accountId,
        target,
        forceRefresh
    )

    internal suspend fun syncProject(
        store: ProjectCacheStore,
        accountId: String,
        target: ProjectSyncTarget,
        forceRefresh: Boolean = false
    ): CachedProjectContent {
        val epoch = repository.captureCacheEpoch()
        val owner = repository.projectCacheOwner(accountId)
        return projectSyncFlight.run(ProjectSyncKey(owner, target, forceRefresh, epoch)) {
            projectMutex(target.projectMfid).withLock {
                _projectStates.update { it + (target.projectMfid to ProjectSyncState.Syncing) }
                try {
                    var requiresFullRefresh = false
                    if (!forceRefresh && repository.hasPersistedProjectData(target.projectMfid)) {
                        val restored = store.loadContent(owner, target.projectMfid)
                            ?: error("Persisted project cache is unavailable")
                        if (!restored.replica.requiresFullRefresh && restored.projectSlug == target.projectSlug) {
                            _projectStates.update { it + (target.projectMfid to ProjectSyncState.Ready(restored.replica.lastSuccessfulSyncAt)) }
                            return@withLock restored
                        }
                        requiresFullRefresh = true
                    }
                    val (samples, datasets) = repository.fetchProjectData(
                        target.projectSlug,
                        forceRefresh = forceRefresh || requiresFullRefresh
                    )
                    val content = CachedProjectContent(
                        projectMfid = target.projectMfid,
                        projectSlug = target.projectSlug,
                        samples = samples,
                        datasets = datasets,
                        cachedAt = Clock.System.now().toEpochMilliseconds()
                    )
                    if (!repository.isCacheEpochCurrent(epoch)) throw CancellationException("Cache scope changed")
                    store.saveContent(owner, content)
                    if (!repository.isCacheEpochCurrent(epoch)) throw CancellationException("Cache scope changed")
                    repository.seedPersistedProjectData(content, epoch)
                    _projectStates.update { it + (target.projectMfid to ProjectSyncState.Ready(content.replica.lastSuccessfulSyncAt)) }
                    content
                } catch (error: CancellationException) {
                    _projectStates.update { it - target.projectMfid }
                    throw error
                } catch (error: Exception) {
                    _projectStates.update { it + (target.projectMfid to ProjectSyncState.Failed(error.message ?: "Sync failed")) }
                    throw error
                }
            }
        }
    }

    suspend fun applyProjectDelta(context: PlatformContext, accountId: String, delta: ProjectContentDelta): CachedProjectContent =
        applyProjectDelta(PlatformProjectCacheStore(context), accountId, delta)

    internal suspend fun applyProjectDelta(
        store: ProjectCacheStore,
        accountId: String,
        delta: ProjectContentDelta
    ): CachedProjectContent {
        val epoch = repository.captureCacheEpoch()
        val owner = repository.projectCacheOwner(accountId)
        return projectMutex(delta.projectMfid).withLock {
            val current = store.loadContent(owner, delta.projectMfid)
                ?: error("A full project sync is required before applying deltas")
            check(!current.replica.requiresFullRefresh) { "A full project sync is required before applying deltas" }
            val updated = current.applyDelta(delta)
            if (!repository.isCacheEpochCurrent(epoch)) throw CancellationException("Cache scope changed")
            store.saveContent(owner, updated)
            if (!repository.isCacheEpochCurrent(epoch)) throw CancellationException("Cache scope changed")
            repository.seedPersistedProjectData(updated, epoch)
            _projectStates.update { it + (delta.projectMfid to ProjectSyncState.Ready(updated.replica.lastSuccessfulSyncAt)) }
            updated
        }
    }

    suspend fun markProjectForFullRefresh(context: PlatformContext, accountId: String, projectMfid: String) =
        markProjectForFullRefresh(PlatformProjectCacheStore(context), accountId, projectMfid)

    internal suspend fun markProjectForFullRefresh(store: ProjectCacheStore, accountId: String, projectMfid: String) {
        val owner = repository.projectCacheOwner(accountId)
        projectMutex(projectMfid).withLock {
            val current = store.loadContent(owner, projectMfid) ?: return@withLock
            store.saveContent(owner, current.requireFullRefresh())
            _projectStates.update { it + (projectMfid to ProjectSyncState.RefreshRequired) }
        }
    }

    suspend fun removeProject(context: PlatformContext, accountId: String, projectMfid: String) =
        removeProject(PlatformProjectCacheStore(context), accountId, projectMfid)

    internal suspend fun removeProject(store: ProjectCacheStore, accountId: String, projectMfid: String) {
        projectMutex(projectMfid).withLock {
            store.removeContent(repository.projectCacheOwner(accountId), projectMfid)
            repository.removePersistedProjectData(projectMfid)
            _projectStates.update { it - projectMfid }
        }
    }

    suspend fun retainProjects(context: PlatformContext, accountId: String, projectMfids: Set<String>) =
        retainProjects(PlatformProjectCacheStore(context), accountId, projectMfids)

    internal suspend fun retainProjects(store: ProjectCacheStore, accountId: String, projectMfids: Set<String>) {
        store.retainContents(repository.projectCacheOwner(accountId), projectMfids)
        repository.retainPersistedProjectData(projectMfids)
        _projectStates.update { states -> states.filterKeys { it in projectMfids } }
    }

    suspend fun syncAll(
        context: PlatformContext,
        accountId: String,
        syncedProjectIds: Set<String> = emptySet(),
        currentUserOrcid: String? = null
    ) = syncAll(PlatformProjectCacheStore(context), accountId, syncedProjectIds, currentUserOrcid)

    internal suspend fun syncAll(
        store: ProjectCacheStore,
        accountId: String,
        syncedProjectIds: Set<String> = emptySet(),
        currentUserOrcid: String? = null
    ) = fullSyncFlight.run(
        FullSyncKey(repository.projectCacheOwner(accountId), syncedProjectIds, repository.captureCacheEpoch())
    ) {
        coroutineScope {
            restoreProjects(store, accountId, syncedProjectIds)
            val projects = (refreshOverview(store, accountId) as? ApiResult.Success)?.data
                ?: repository.getCachedProjects()
                ?: emptyList()

            val targets = syncedProjectIds.mapNotNull { projectMfid ->
                projects.firstOrNull { it.uniqueId == projectMfid }?.toSyncTarget()
            }
            targets.chunked(5).forEach { batch ->
                coroutineScope {
                    batch.map { target ->
                        async {
                            try {
                                syncProject(store, accountId, target, forceRefresh = true)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                            }
                        }
                    }.awaitAll()
                }
            }

            val ledProjectIds = if (currentUserOrcid != null) {
                projects.filter { it.projectLeadOrcid == currentUserOrcid }.map { it.projectId }
            } else {
                emptyList()
            }
            if (ledProjectIds.isNotEmpty()) {
                try {
                    repository.fetchPendingJoinRequestCounts(ledProjectIds)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            }
        }
    }
}

sealed class ProjectSyncState {
    data object Syncing : ProjectSyncState()
    data class Ready(val lastSuccessfulSyncAt: Long) : ProjectSyncState()
    data object RefreshRequired : ProjectSyncState()
    data class Failed(val message: String) : ProjectSyncState()
}

private data class FullSyncKey(
    val owner: ProjectCacheOwner,
    val projectIds: Set<String>,
    val epoch: Long
)

private data class OverviewKey(
    val owner: ProjectCacheOwner,
    val epoch: Long
)

private data class ProjectSyncKey(
    val owner: ProjectCacheOwner,
    val target: ProjectSyncTarget,
    val forceRefresh: Boolean,
    val epoch: Long
)
