package crucible.lens.data.cache

import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import kotlin.time.Clock

data class CachedItem<T>(
    val data: T,
    val timestamp: Long
)

object CacheManager {
    private const val CACHE_TTL = 10 * 60 * 1000L // 10 minutes
    private const val MAX_RESOURCE_CACHE_SIZE = 50
    private const val MAX_THUMBNAIL_CACHE_SIZE = 20
    private const val MAX_PROJECT_DETAIL_CACHE_SIZE = 30
    private const val MAX_INSTRUMENT_DATASETS_CACHE_SIZE = 15

    private fun <V : CachedItem<*>> LinkedHashMap<String, V>.evictOldestIfOver(limit: Int) {
        if (size >= limit) {
            entries.minByOrNull { it.value.timestamp }?.key?.let { remove(it) }
        }
    }

    private fun <T> CachedItem<T>.isExpired() =
        Clock.System.now().toEpochMilliseconds() - timestamp > CACHE_TTL

    private val resourceCache = LinkedHashMap<String, CachedItem<CrucibleResource>>()
    private val resourceTypeCache = LinkedHashMap<String, String>() // UUID -> "sample" or "dataset"
    private val thumbnailCache = LinkedHashMap<String, CachedItem<List<Thumbnail>>>()
    private var projectsCache: CachedItem<List<Project>>? = null
    private var instrumentsCache: CachedItem<List<Instrument>>? = null
    private val instrumentDatasetsCache = LinkedHashMap<String, CachedItem<List<Dataset>>>()
    private val projectSamplesCache = LinkedHashMap<String, CachedItem<List<Sample>>>()
    private val projectDatasetsCache = LinkedHashMap<String, CachedItem<List<Dataset>>>()

    // Resource caching
    fun cacheResource(uuid: String, resource: CrucibleResource) {
        // Maintain cache size
        if (resourceCache.size >= MAX_RESOURCE_CACHE_SIZE) {
            // Remove oldest entries
            val oldestKey = resourceCache.entries
                .minByOrNull { it.value.timestamp }
                ?.key
            oldestKey?.let {
                resourceCache.remove(it)
                resourceTypeCache.remove(it)
            }
        }

        resourceCache[uuid] = CachedItem(resource, Clock.System.now().toEpochMilliseconds())

        // Also cache the resource type to avoid type-check API calls
        val type = when (resource) {
            is Sample -> "sample"
            is Dataset -> "dataset"
        }
        resourceTypeCache[uuid] = type
    }

    fun getResource(uuid: String): CrucibleResource? {
        val cached = resourceCache[uuid] ?: return null
        if (cached.isExpired()) {
            resourceCache.remove(uuid)
            resourceTypeCache.remove(uuid)
            return null
        }
        return cached.data
    }

    // Resource type caching
    fun getResourceType(uuid: String): String? {
        return resourceTypeCache[uuid]
    }

    fun cacheResourceType(uuid: String, type: String) {
        resourceTypeCache[uuid] = type
    }

    fun removeResourceType(uuid: String) {
        resourceTypeCache.remove(uuid)
    }

    // Thumbnail caching
    fun cacheThumbnails(uuid: String, thumbnails: List<Thumbnail>) {
        thumbnailCache.evictOldestIfOver(MAX_THUMBNAIL_CACHE_SIZE)
        thumbnailCache[uuid] = CachedItem(thumbnails, Clock.System.now().toEpochMilliseconds())
    }

    fun getThumbnails(uuid: String): List<Thumbnail>? {
        val cached = thumbnailCache[uuid] ?: return null
        if (cached.isExpired()) { thumbnailCache.remove(uuid); return null }
        return cached.data
    }

    // Projects caching
    fun cacheProjects(projects: List<Project>) {
        projectsCache = CachedItem(projects, Clock.System.now().toEpochMilliseconds())
    }

    fun getProjects(): List<Project>? {
        val cached = projectsCache ?: return null
        if (cached.isExpired()) { projectsCache = null; return null }
        return cached.data
    }

    // Instruments caching
    fun cacheInstruments(instruments: List<Instrument>) {
        instrumentsCache = CachedItem(instruments, Clock.System.now().toEpochMilliseconds())
    }

    fun getInstruments(): List<Instrument>? {
        val cached = instrumentsCache ?: return null
        if (cached.isExpired()) { instrumentsCache = null; return null }
        return cached.data
    }

    fun cacheInstrumentDatasets(instrumentName: String, datasets: List<Dataset>) {
        instrumentDatasetsCache.evictOldestIfOver(MAX_INSTRUMENT_DATASETS_CACHE_SIZE)
        instrumentDatasetsCache[instrumentName] = CachedItem(datasets, Clock.System.now().toEpochMilliseconds())
    }

    fun getInstrumentDatasets(instrumentName: String): List<Dataset>? {
        val cached = instrumentDatasetsCache[instrumentName] ?: return null
        if (cached.isExpired()) { instrumentDatasetsCache.remove(instrumentName); return null }
        return cached.data
    }

    fun clearInstrumentsCache() {
        instrumentsCache = null
        instrumentDatasetsCache.clear()
    }

    // Clear individual items
    fun clearResource(uuid: String) {
        resourceCache.remove(uuid)
        resourceTypeCache.remove(uuid)
    }

    fun clearThumbnail(uuid: String) {
        thumbnailCache.remove(uuid)
    }

    // Project detail caching (samples and datasets per project)
    fun cacheProjectSamples(projectId: String, samples: List<Sample>) {
        projectSamplesCache.evictOldestIfOver(MAX_PROJECT_DETAIL_CACHE_SIZE)
        projectSamplesCache[projectId] = CachedItem(samples, Clock.System.now().toEpochMilliseconds())
    }

    fun getProjectSamples(projectId: String): List<Sample>? {
        val cached = projectSamplesCache[projectId] ?: return null
        if (cached.isExpired()) { projectSamplesCache.remove(projectId); return null }
        return cached.data
    }

    fun cacheProjectDatasets(projectId: String, datasets: List<Dataset>) {
        projectDatasetsCache.evictOldestIfOver(MAX_PROJECT_DETAIL_CACHE_SIZE)
        projectDatasetsCache[projectId] = CachedItem(datasets, Clock.System.now().toEpochMilliseconds())
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
        resourceTypeCache.clear()
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
        clearInstrumentsCache()
    }

    data class CacheStats(
        val resourceCount: Int,
        val thumbnailCount: Int,
        val projectCount: Int,
        val cachedSampleCount: Int,
        val cachedDatasetCount: Int,
        val estimatedSizeKB: Long
    )

    fun getDetailedStats(): CacheStats {
        val cachedSampleCount = projectSamplesCache.values.sumOf { it.data.size }
        val cachedDatasetCount = projectDatasetsCache.values.sumOf { it.data.size }
        val thumbnailSizeBytes = thumbnailCache.values.sumOf { cached ->
            cached.data.sumOf { it.thumbnailB64.length.toLong() }
        }
        val estimatedSizeKB =
            resourceCache.size * 3L +              // ~3 KB per resource (metadata + relationships)
            thumbnailSizeBytes / 1024L +            // actual base64 thumbnail data
            (cachedSampleCount + cachedDatasetCount).toLong() // ~1 KB each from project lists
        return CacheStats(
            resourceCount = resourceCache.size,
            thumbnailCount = thumbnailCache.size,
            projectCount = projectsCache?.data?.size ?: 0,
            cachedSampleCount = cachedSampleCount,
            cachedDatasetCount = cachedDatasetCount,
            estimatedSizeKB = estimatedSizeKB
        )
    }

    // Age query methods (return null if not cached, otherwise age in minutes)
    fun getProjectsAgeMinutes(): Long? =
        projectsCache?.let { (Clock.System.now().toEpochMilliseconds() - it.timestamp) / 60000 }

    fun getProjectDataAgeMinutes(projectId: String): Long? =
        projectSamplesCache[projectId]?.let { (Clock.System.now().toEpochMilliseconds() - it.timestamp) / 60000 }

    fun getResourceAgeMinutes(uuid: String): Long? =
        resourceCache[uuid]?.let { (Clock.System.now().toEpochMilliseconds() - it.timestamp) / 60000 }
}
