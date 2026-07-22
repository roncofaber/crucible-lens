package crucible.lens.data.cache

import crucible.lens.data.model.Project
import crucible.lens.platform.PlatformContext
import kotlinx.serialization.Serializable

@Serializable
data class CachedProjects(
    val projects: List<Project>,
    val cachedAt: Long
)

expect object PersistentProjectCache {
    suspend fun save(context: PlatformContext, projects: List<Project>)
    suspend fun load(context: PlatformContext): List<Project>?
    suspend fun clear(context: PlatformContext)
    suspend fun getCacheAgeHours(context: PlatformContext): Long?
}
