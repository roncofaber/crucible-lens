package crucible.lens.data.cache

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.platform.PlatformContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val PROJECT_CONTENT_CACHE_VERSION = 3

@Serializable
data class ProjectCacheOwner(
    val accountId: String,
    val serverUrl: String
)

internal fun ProjectCacheOwner.storageKey(): String =
    PlatformCrypto.sha256Hex("$serverUrl\n$accountId".encodeToByteArray())

@Serializable
data class ProjectReplicaMetadata(
    val lastSuccessfulSyncAt: Long,
    val deltaCursor: String? = null,
    val requiresFullRefresh: Boolean = false,
    val deletedSampleIds: Set<String> = emptySet(),
    val deletedDatasetIds: Set<String> = emptySet()
)

@Serializable
data class CachedProjects(
    val accountId: String = "",
    val serverUrl: String = "",
    val projects: List<Project>,
    val cachedAt: Long
)

internal fun CachedProjects.belongsTo(owner: ProjectCacheOwner): Boolean =
    accountId == owner.accountId && serverUrl == owner.serverUrl

internal fun CachedProjects.migrate(owner: ProjectCacheOwner): CachedProjects? = takeIf { belongsTo(owner) }

@Serializable
data class CachedProjectContent(
    val projectMfid: String,
    val projectSlug: String,
    val samples: List<Sample>,
    val datasets: List<Dataset>,
    val cachedAt: Long,
    val replica: ProjectReplicaMetadata = ProjectReplicaMetadata(lastSuccessfulSyncAt = cachedAt)
) {
    init {
        require(samples.all { it.projectId == null || it.projectId == projectSlug })
        require(datasets.all { it.projectId == null || it.projectId == projectSlug })
    }
}

@Serializable
data class ProjectContentDelta(
    val projectMfid: String,
    val projectSlug: String,
    val synchronizedAt: Long,
    val cursor: String? = null,
    val samples: List<Sample> = emptyList(),
    val datasets: List<Dataset> = emptyList(),
    val deletedSampleIds: Set<String> = emptySet(),
    val deletedDatasetIds: Set<String> = emptySet()
)

@Serializable
data class CachedProjectContents(
    val version: Int = PROJECT_CONTENT_CACHE_VERSION,
    val accountId: String = "",
    val serverUrl: String = "",
    val projects: Map<String, CachedProjectContent> = emptyMap()
)

internal fun CachedProjectContents.belongsTo(owner: ProjectCacheOwner): Boolean =
    version == PROJECT_CONTENT_CACHE_VERSION && accountId == owner.accountId && serverUrl == owner.serverUrl

internal fun CachedProjectContents.contentFor(owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent? =
    takeIf { it.belongsTo(owner) }?.projects?.get(projectMfid)?.takeIf { it.projectMfid == projectMfid }

internal fun CachedProjectContents.retaining(projectIds: Set<String>): CachedProjectContents =
    copy(projects = projects.filterKeys { it in projectIds })

internal fun CachedProjectContents.migrate(owner: ProjectCacheOwner): CachedProjectContents? = takeIf { belongsTo(owner) }

internal fun CachedProjectContent.applyDelta(delta: ProjectContentDelta): CachedProjectContent {
    require(delta.projectMfid == projectMfid)
    require(delta.projectSlug == projectSlug)
    require(delta.samples.all { it.projectId == null || it.projectId == projectSlug })
    require(delta.datasets.all { it.projectId == null || it.projectId == projectSlug })
    if (delta.synchronizedAt < replica.lastSuccessfulSyncAt) return this

    val sampleTombstones = replica.deletedSampleIds + delta.deletedSampleIds
    val datasetTombstones = replica.deletedDatasetIds + delta.deletedDatasetIds
    val mergedSamples = samples.associateBy(Sample::uniqueId).toMutableMap().apply {
        delta.samples.filterNot { it.uniqueId in sampleTombstones }.forEach { put(it.uniqueId, it) }
        sampleTombstones.forEach(::remove)
    }.values.toList()
    val mergedDatasets = datasets.associateBy(Dataset::uniqueId).toMutableMap().apply {
        delta.datasets.filterNot { it.uniqueId in datasetTombstones }.forEach { put(it.uniqueId, it) }
        datasetTombstones.forEach(::remove)
    }.values.toList()

    return copy(
        samples = mergedSamples,
        datasets = mergedDatasets,
        cachedAt = delta.synchronizedAt,
        replica = ProjectReplicaMetadata(
            lastSuccessfulSyncAt = delta.synchronizedAt,
            deltaCursor = delta.cursor,
            deletedSampleIds = sampleTombstones,
            deletedDatasetIds = datasetTombstones
        )
    )
}

internal fun CachedProjectContent.requireFullRefresh(): CachedProjectContent = copy(
    replica = replica.copy(requiresFullRefresh = true)
)

private val projectContentCacheJson = Json { ignoreUnknownKeys = true }

internal fun encodeProjectContents(contents: CachedProjectContents): String =
    projectContentCacheJson.encodeToString(CachedProjectContents.serializer(), contents)

internal fun decodeProjectContents(value: String): CachedProjectContents? =
    runCatching { projectContentCacheJson.decodeFromString(CachedProjectContents.serializer(), value) }.getOrNull()

expect object PersistentProjectCache {
    suspend fun save(context: PlatformContext, owner: ProjectCacheOwner, projects: List<Project>)
    suspend fun load(context: PlatformContext, owner: ProjectCacheOwner): List<Project>?
    suspend fun saveContent(context: PlatformContext, owner: ProjectCacheOwner, content: CachedProjectContent)
    suspend fun loadContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent?
    suspend fun loadContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent>
    suspend fun removeContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String)
    suspend fun retainContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>)
    suspend fun clear(context: PlatformContext)
    suspend fun getCacheAgeHours(context: PlatformContext, owner: ProjectCacheOwner): Long?
}
