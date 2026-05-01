package crucible.lens.data.cache

import android.util.Log
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

private const val CACHE_FILE = "projects_cache.json"
private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L

actual object PersistentProjectCache {

    actual suspend fun saveProjectData(
        context: PlatformContext,
        projects: List<Project>,
        samplesMap: Map<String, List<Sample>>,
        datasetsMap: Map<String, List<Dataset>>
    ) { withContext(Dispatchers.IO) {
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
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
            }
            val cacheData = CachedProjectData(summaries = summaries, cachedAt = Clock.System.now().toEpochMilliseconds())
            File(context.filesDir, CACHE_FILE).writeText(json.encodeToString(cacheData))
        } catch (e: Exception) {
            println("Failed to save project data: $e")
        }
    } }

    actual suspend fun loadProjectData(context: PlatformContext): List<ProjectSummary>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null
            val cacheData = json.decodeFromString<CachedProjectData>(file.readText())
            if (Clock.System.now().toEpochMilliseconds() - cacheData.cachedAt > MAX_CACHE_AGE_MS) {
                file.delete(); return@withContext null
            }
            cacheData.summaries
        } catch (e: Exception) {
            Log.e("PersistentProjectCache", "Failed to load project data", e)
            null
        }
    }

    actual suspend fun clear(context: PlatformContext) { withContext(Dispatchers.IO) {
        try { File(context.filesDir, CACHE_FILE).delete() }
        catch (e: Exception) { println("Failed to clear cache: $e") }
    } }

    actual suspend fun getCacheAgeHours(context: PlatformContext): Long? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null
            val cacheData = json.decodeFromString<CachedProjectData>(file.readText())
            (Clock.System.now().toEpochMilliseconds() - cacheData.cachedAt) / 3_600_000L
        } catch (e: Exception) { null }
    }
}
