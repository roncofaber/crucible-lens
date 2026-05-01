@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crucible.lens.data.cache

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.*

private const val CACHE_FILE = "projects_cache.json"
private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L

private fun cacheFile(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true)
    val library = paths.firstOrNull() as? String ?: ""
    return "$library/$CACHE_FILE"
}

actual object PersistentProjectCache {

    actual suspend fun saveProjectData(
        context: PlatformContext,
        projects: List<Project>,
        samplesMap: Map<String, List<Sample>>,
        datasetsMap: Map<String, List<Dataset>>
    ): Unit = withContext(Dispatchers.Default) {
        try {
            val summaries = projects.map { project ->
                val samples = samplesMap[project.projectId] ?: emptyList()
                val datasets = datasetsMap[project.projectId] ?: emptyList()
                ProjectSummary(
                    projectId = project.projectId,
                    projectName = project.title,
                    description = null,
                    projectLeadEmail = project.lead?.email,
                    createdAt = null,
                    sampleCount = samples.size,
                    datasetCount = datasets.size,
                    sampleTypes = samples.mapNotNull { it.sampleType }.distinct().sorted(),
                    measurements = datasets.mapNotNull { it.measurement }.distinct().sorted(),
                    lastUpdated = NSDate().timeIntervalSince1970.toLong() * 1000
                )
            }
            val cacheData = CachedProjectData(
                summaries = summaries,
                cachedAt = NSDate().timeIntervalSince1970.toLong() * 1000
            )
            val json = Json.encodeToString(cacheData)
            (json as NSString).writeToFile(cacheFile(), atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) {}
    }

    actual suspend fun loadProjectData(context: PlatformContext): List<ProjectSummary>? = withContext(Dispatchers.Default) {
        try {
            val path = cacheFile()
            val json = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
                ?.toString() ?: return@withContext null
            val cacheData = Json.decodeFromString<CachedProjectData>(json)
            val now = NSDate().timeIntervalSince1970.toLong() * 1000
            if (now - cacheData.cachedAt > MAX_CACHE_AGE_MS) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
                return@withContext null
            }
            cacheData.summaries
        } catch (_: Exception) { null }
    }

    actual suspend fun clear(context: PlatformContext): Unit = withContext(Dispatchers.Default) {
        try { NSFileManager.defaultManager.removeItemAtPath(cacheFile(), error = null) }
        catch (_: Exception) {}
    }

    actual suspend fun getCacheAgeHours(context: PlatformContext): Long? = withContext(Dispatchers.Default) {
        try {
            val path = cacheFile()
            val json = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
                ?.toString() ?: return@withContext null
            val cacheData = Json.decodeFromString<CachedProjectData>(json)
            val now = NSDate().timeIntervalSince1970.toLong() * 1000
            (now - cacheData.cachedAt) / 3_600_000L
        } catch (_: Exception) { null }
    }
}
