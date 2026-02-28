package crucible.lens.data.cache

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lightweight project summary for quick display
 */
@JsonClass(generateAdapter = true)
data class ProjectSummary(
    val projectId: String,
    val projectName: String?,
    val description: String?,
    val projectLeadEmail: String?,
    val createdAt: String?,
    val sampleCount: Int,
    val datasetCount: Int,
    val sampleTypes: List<String>, // distinct sample types
    val measurements: List<String>, // distinct measurements
    val lastUpdated: Long
)

/**
 * Complete cached project data
 */
@JsonClass(generateAdapter = true)
data class CachedProjectData(
    val summaries: List<ProjectSummary>,
    val cachedAt: Long
)

/**
 * Persistent cache for project data that survives app restarts
 */
object PersistentProjectCache {
    private const val CACHE_FILE = "projects_cache.json"
    private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(CachedProjectData::class.java)

    /**
     * Save project summaries to disk
     */
    suspend fun saveProjectData(
        context: Context,
        projects: List<Project>,
        samplesMap: Map<String, List<Sample>>,
        datasetsMap: Map<String, List<Dataset>>
    ) = withContext(Dispatchers.IO) {
        try {
            val summaries = projects.map { project ->
                val samples = samplesMap[project.projectId] ?: emptyList()
                val datasets = datasetsMap[project.projectId] ?: emptyList()

                ProjectSummary(
                    projectId = project.projectId,
                    projectName = project.projectName,
                    description = project.description,
                    projectLeadEmail = project.projectLeadEmail,
                    createdAt = project.createdAt,
                    sampleCount = samples.size,
                    datasetCount = datasets.size,
                    sampleTypes = samples.mapNotNull { it.sampleType }.distinct().sorted(),
                    measurements = datasets.mapNotNull { it.measurement }.distinct().sorted(),
                    lastUpdated = System.currentTimeMillis()
                )
            }

            val cacheData = CachedProjectData(
                summaries = summaries,
                cachedAt = System.currentTimeMillis()
            )

            val file = File(context.filesDir, CACHE_FILE)
            file.writeText(adapter.toJson(cacheData))
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail silently - cache is optional
        }
    }

    /**
     * Load project summaries from disk
     * Returns null if cache is expired or doesn't exist
     */
    suspend fun loadProjectData(context: Context): List<ProjectSummary>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null

            val json = file.readText()
            val cacheData = adapter.fromJson(json) ?: return@withContext null

            // Check if cache is expired
            val age = System.currentTimeMillis() - cacheData.cachedAt
            if (age > MAX_CACHE_AGE_MS) {
                file.delete()
                return@withContext null
            }

            cacheData.summaries
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Clear the persistent cache
     */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get cache age in hours, or null if no cache exists
     */
    suspend fun getCacheAgeHours(context: Context): Long? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null

            val json = file.readText()
            val cacheData = adapter.fromJson(json) ?: return@withContext null

            (System.currentTimeMillis() - cacheData.cachedAt) / (60 * 60 * 1000)
        } catch (e: Exception) {
            null
        }
    }
}
