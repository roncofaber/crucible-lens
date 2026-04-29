package crucible.lens.data.cache

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.platform.PlatformContext
import kotlinx.serialization.Serializable

@Serializable
data class ProjectSummary(
    val projectId: String,
    val projectName: String? = null,
    val description: String? = null,
    val projectLeadEmail: String? = null,
    val createdAt: String? = null,
    val sampleCount: Int = 0,
    val datasetCount: Int = 0,
    val sampleTypes: List<String> = emptyList(),
    val measurements: List<String> = emptyList(),
    val lastUpdated: Long = 0L
)

@Serializable
data class CachedProjectData(
    val summaries: List<ProjectSummary>,
    val cachedAt: Long
)

expect object PersistentProjectCache {
    suspend fun saveProjectData(
        context: PlatformContext,
        projects: List<Project>,
        samplesMap: Map<String, List<Sample>>,
        datasetsMap: Map<String, List<Dataset>>
    )

    suspend fun loadProjectData(context: PlatformContext): List<ProjectSummary>?

    suspend fun clear(context: PlatformContext)

    suspend fun getCacheAgeHours(context: PlatformContext): Long?
}
