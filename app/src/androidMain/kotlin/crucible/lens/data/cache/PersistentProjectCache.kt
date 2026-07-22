package crucible.lens.data.cache

import android.util.Log
import crucible.lens.data.model.Project
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }
private const val CACHE_FILE = "projects_cache.json"
private const val MAX_CACHE_AGE_MS = 24 * 60 * 60 * 1000L

actual object PersistentProjectCache {

    actual suspend fun save(context: PlatformContext, projects: List<Project>) {
        withContext(Dispatchers.IO) {
            try {
                val data = CachedProjects(projects, System.currentTimeMillis())
                File(context.filesDir, CACHE_FILE).writeText(json.encodeToString(data))
            } catch (e: Exception) {
                Log.e("PersistentProjectCache", "Failed to save", e)
            }
        }
    }

    actual suspend fun load(context: PlatformContext): List<Project>? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null
            val data = json.decodeFromString<CachedProjects>(file.readText())
            if (System.currentTimeMillis() - data.cachedAt > MAX_CACHE_AGE_MS) {
                file.delete(); return@withContext null
            }
            data.projects
        } catch (e: Exception) {
            Log.e("PersistentProjectCache", "Failed to load", e)
            null
        }
    }

    actual suspend fun clear(context: PlatformContext) {
        withContext(Dispatchers.IO) {
            try { File(context.filesDir, CACHE_FILE).delete() }
            catch (e: Exception) { Log.e("PersistentProjectCache", "Failed to clear", e) }
        }
    }

    actual suspend fun getCacheAgeHours(context: PlatformContext): Long? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return@withContext null
            val data = json.decodeFromString<CachedProjects>(file.readText())
            (System.currentTimeMillis() - data.cachedAt) / 3_600_000L
        } catch (e: Exception) { null }
    }
}
