package crucible.lens.data.cache

import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import java.util.concurrent.ConcurrentHashMap

data class CachedItem<T>(
    val data: T,
    val timestamp: Long
)

object CacheManager {
    private const val CACHE_TTL = 10 * 60 * 1000L // 10 minutes
    private const val MAX_RESOURCE_CACHE_SIZE = 50

    private fun <T> CachedItem<T>.isExpired() =
        System.currentTimeMillis() - timestamp > CACHE_TTL

    private val resourceCache = ConcurrentHashMap<String, CachedItem<CrucibleResource>>()
    private val thumbnailCache = ConcurrentHashMap<String, CachedItem<List<String>>>()
    private var projectsCache: CachedItem<List<Project>>? = null
    private val projectSamplesCache = ConcurrentHashMap<String, CachedItem<List<Sample>>>()
    private val projectDatasetsCache = ConcurrentHashMap<String, CachedItem<List<Dataset>>>()

    // Resource caching
    fun cacheResource(uuid: String, resource: CrucibleResource) {
        // Maintain cache size
        if (resourceCache.size >= MAX_RESOURCE_CACHE_SIZE) {
            // Remove oldest entries
            val oldestKey = resourceCache.entries
                .minByOrNull { it.value.timestamp }
                ?.key
            oldestKey?.let { resourceCache.remove(it) }
        }

        resourceCache[uuid] = CachedItem(resource, System.currentTimeMillis())
    }

    fun getResource(uuid: String): CrucibleResource? {
        val cached = resourceCache[uuid] ?: return null
        if (cached.isExpired()) { resourceCache.remove(uuid); return null }
        return cached.data
    }

    // Thumbnail caching
    fun cacheThumbnails(uuid: String, thumbnails: List<String>) {
        thumbnailCache[uuid] = CachedItem(thumbnails, System.currentTimeMillis())
    }

    fun getThumbnails(uuid: String): List<String>? {
        val cached = thumbnailCache[uuid] ?: return null
        if (cached.isExpired()) { thumbnailCache.remove(uuid); return null }
        return cached.data
    }

    // Projects caching
    fun cacheProjects(projects: List<Project>) {
        projectsCache = CachedItem(projects, System.currentTimeMillis())
    }

    fun getProjects(): List<Project>? {
        val cached = projectsCache ?: return null
        if (cached.isExpired()) { projectsCache = null; return null }
        return cached.data
    }

    // Clear individual items
    fun clearResource(uuid: String) {
        resourceCache.remove(uuid)
    }

    fun clearThumbnail(uuid: String) {
        thumbnailCache.remove(uuid)
    }

    // Project detail caching (samples and datasets per project)
    fun cacheProjectSamples(projectId: String, samples: List<Sample>) {
        projectSamplesCache[projectId] = CachedItem(samples, System.currentTimeMillis())
    }

    fun getProjectSamples(projectId: String): List<Sample>? {
        val cached = projectSamplesCache[projectId] ?: return null
        if (cached.isExpired()) { projectSamplesCache.remove(projectId); return null }
        return cached.data
    }

    fun cacheProjectDatasets(projectId: String, datasets: List<Dataset>) {
        projectDatasetsCache[projectId] = CachedItem(datasets, System.currentTimeMillis())
    }

    fun getProjectDatasets(projectId: String): List<Dataset>? {
        val cached = projectDatasetsCache[projectId] ?: return null
        if (cached.isExpired()) { projectDatasetsCache.remove(projectId); return null }
        return cached.data
    }

    fun clearProjectDetail(projectId: String) {
        projectSamplesCache.remove(projectId)
        projectDatasetsCache.remove(projectId)
    }

    // Clear all cache methods
    fun clearResourceCache() {
        resourceCache.clear()
    }

    fun clearThumbnailCache() {
        thumbnailCache.clear()
    }

    fun clearProjectsCache() {
        projectsCache = null
    }

    fun clearProjectDetailsCache() {
        projectSamplesCache.clear()
        projectDatasetsCache.clear()
    }

    fun clearAll() {
        clearResourceCache()
        clearThumbnailCache()
        clearProjectsCache()
        clearProjectDetailsCache()
    }

    // Stats for debugging
    fun getCacheStats(): String {
        return "Resources: ${resourceCache.size}, Thumbnails: ${thumbnailCache.size}, Projects: ${if (projectsCache != null) "cached" else "empty"}, Project Details: ${projectSamplesCache.size + projectDatasetsCache.size}"
    }

    // Age query methods (return null if not cached, otherwise age in minutes)
    fun getProjectsAgeMinutes(): Long? =
        projectsCache?.let { (System.currentTimeMillis() - it.timestamp) / 60000 }

    fun getProjectDataAgeMinutes(projectId: String): Long? =
        projectSamplesCache[projectId]?.let { (System.currentTimeMillis() - it.timestamp) / 60000 }

    fun getResourceAgeMinutes(uuid: String): Long? =
        resourceCache[uuid]?.let { (System.currentTimeMillis() - it.timestamp) / 60000 }
}
