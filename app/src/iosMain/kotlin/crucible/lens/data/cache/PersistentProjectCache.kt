@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crucible.lens.data.cache

import crucible.lens.data.model.Project
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

    actual suspend fun save(context: PlatformContext, projects: List<Project>): Unit = withContext(Dispatchers.Default) {
        try {
            val data = CachedProjects(projects, NSDate().timeIntervalSince1970.toLong() * 1000)
            val encoded = Json.encodeToString(data)
            (encoded as NSString).writeToFile(cacheFile(), atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) {}
    }

    actual suspend fun load(context: PlatformContext): List<Project>? = withContext(Dispatchers.Default) {
        try {
            val text = NSString.stringWithContentsOfFile(cacheFile(), encoding = NSUTF8StringEncoding, error = null)
                ?.toString() ?: return@withContext null
            val data = Json.decodeFromString<CachedProjects>(text)
            val now = NSDate().timeIntervalSince1970.toLong() * 1000
            if (now - data.cachedAt > MAX_CACHE_AGE_MS) {
                NSFileManager.defaultManager.removeItemAtPath(cacheFile(), error = null)
                return@withContext null
            }
            data.projects
        } catch (_: Exception) { null }
    }

    actual suspend fun clear(context: PlatformContext): Unit = withContext(Dispatchers.Default) {
        try { NSFileManager.defaultManager.removeItemAtPath(cacheFile(), error = null) }
        catch (_: Exception) {}
    }

    actual suspend fun getCacheAgeHours(context: PlatformContext): Long? = withContext(Dispatchers.Default) {
        try {
            val text = NSString.stringWithContentsOfFile(cacheFile(), encoding = NSUTF8StringEncoding, error = null)
                ?.toString() ?: return@withContext null
            val data = Json.decodeFromString<CachedProjects>(text)
            val now = NSDate().timeIntervalSince1970.toLong() * 1000
            (now - data.cachedAt) / 3_600_000L
        } catch (_: Exception) { null }
    }
}
