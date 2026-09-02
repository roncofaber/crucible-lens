@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("CAST_NEVER_SUCCEEDS")

package crucible.lens.data.cache

import crucible.lens.data.model.Project
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.*

private const val LEGACY_CACHE_FILE = "projects_cache.json"
private const val LEGACY_CONTENT_CACHE_FILE = "project_contents_cache.json"
private const val CACHE_FILE_PREFIX = "projects_cache_"
private const val CONTENT_CACHE_FILE_PREFIX = "project_contents_cache_"
private val storageMutex = Mutex()

private fun libraryDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, true)
    return paths.firstOrNull() as? String ?: ""
}

private fun cacheDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    return paths.firstOrNull() as? String ?: ""
}

private fun legacyCacheFile(): String = "${libraryDirectory()}/$LEGACY_CACHE_FILE"

private fun legacyContentCacheFile(): String = "${libraryDirectory()}/$LEGACY_CONTENT_CACHE_FILE"

private fun cacheFile(owner: ProjectCacheOwner): String =
    "${cacheDirectory()}/$CACHE_FILE_PREFIX${owner.storageKey()}.json"

private fun contentCacheFile(owner: ProjectCacheOwner): String =
    "${cacheDirectory()}/$CONTENT_CACHE_FILE_PREFIX${owner.storageKey()}.json"

private fun readContents(path: String): CachedProjectContents? = runCatching {
    val text = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        ?: return null
    decodeProjectContents(text)
}.getOrNull()

private fun writeContents(path: String, contents: CachedProjectContents) {
    val encoded = encodeProjectContents(contents)
    check((encoded as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null))
}

actual object PersistentProjectCache {

    actual suspend fun save(context: PlatformContext, owner: ProjectCacheOwner, projects: List<Project>): Unit = withContext(Dispatchers.Default) {
        try {
            storageMutex.withLock {
                val data = CachedProjects(owner.accountId, owner.serverUrl, projects, NSDate().timeIntervalSince1970.toLong() * 1000)
                val encoded = Json.encodeToString(data)
                check((encoded as NSString).writeToFile(cacheFile(owner), atomically = true, encoding = NSUTF8StringEncoding, error = null))
            }
        } catch (_: Exception) {}
    }

    actual suspend fun load(context: PlatformContext, owner: ProjectCacheOwner): List<Project>? = withContext(Dispatchers.Default) {
        try {
            storageMutex.withLock {
                val path = cacheFile(owner)
                val legacyPath = legacyCacheFile()
                val source = if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else legacyPath
                val text = NSString.stringWithContentsOfFile(source, encoding = NSUTF8StringEncoding, error = null)
                    ?: return@withLock null
                val data = Json.decodeFromString<CachedProjects>(text).migrate(owner)
                if (data == null) {
                    NSFileManager.defaultManager.removeItemAtPath(source, error = null)
                    return@withLock null
                }
                if (source == legacyPath) {
                    check((Json.encodeToString(data) as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null))
                    NSFileManager.defaultManager.removeItemAtPath(legacyPath, error = null)
                }
                data.projects
            }
        } catch (_: Exception) { null }
    }

    actual suspend fun saveContent(context: PlatformContext, owner: ProjectCacheOwner, content: CachedProjectContent): Unit = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val path = contentCacheFile(owner)
            val legacyPath = legacyContentCacheFile()
            val source = if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else legacyPath
            val current = readContents(source)?.migrate(owner)
                ?: CachedProjectContents(accountId = owner.accountId, serverUrl = owner.serverUrl)
            writeContents(path, current.copy(projects = current.projects + (content.projectMfid to content)))
            if (source == legacyPath) NSFileManager.defaultManager.removeItemAtPath(legacyPath, error = null)
        }
    }

    actual suspend fun loadContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent? = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val path = contentCacheFile(owner)
            val legacyPath = legacyContentCacheFile()
            val source = if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else legacyPath
            val contents = readContents(source)
            val migrated = contents?.migrate(owner)
            if (contents != null && migrated == null) {
                NSFileManager.defaultManager.removeItemAtPath(source, error = null)
            } else if (migrated != null && source == legacyPath) {
                writeContents(path, migrated)
                NSFileManager.defaultManager.removeItemAtPath(legacyPath, error = null)
            }
            migrated?.contentFor(owner, projectMfid)
        }
    }

    actual suspend fun loadContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent> = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val path = contentCacheFile(owner)
            val legacyPath = legacyContentCacheFile()
            val source = if (NSFileManager.defaultManager.fileExistsAtPath(path)) path else legacyPath
            val contents = readContents(source)
            val migrated = contents?.migrate(owner)
            if (contents != null && migrated == null) {
                NSFileManager.defaultManager.removeItemAtPath(source, error = null)
            } else if (migrated != null && source == legacyPath) {
                writeContents(path, migrated)
                NSFileManager.defaultManager.removeItemAtPath(legacyPath, error = null)
            }
            migrated
                ?.projects
                ?.filterKeys { it in projectMfids }
                ?.values
                ?.toList()
                ?: emptyList()
        }
    }

    actual suspend fun removeContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String): Unit = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val path = contentCacheFile(owner)
            val current = readContents(path)?.migrate(owner) ?: return@withLock
            writeContents(path, current.copy(projects = current.projects - projectMfid))
        }
    }

    actual suspend fun retainContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>): Unit = withContext(Dispatchers.Default) {
        storageMutex.withLock {
            val path = contentCacheFile(owner)
            val current = readContents(path)?.migrate(owner) ?: return@withLock
            writeContents(path, current.retaining(projectMfids))
        }
    }

    actual suspend fun clear(context: PlatformContext): Unit = withContext(Dispatchers.Default) {
        try {
            storageMutex.withLock {
                val manager = NSFileManager.defaultManager
                val cache = cacheDirectory()
                manager.removeItemAtPath(legacyCacheFile(), error = null)
                manager.removeItemAtPath(legacyContentCacheFile(), error = null)
                val files = manager.contentsOfDirectoryAtPath(cache, error = null)?.filterIsInstance<String>().orEmpty()
                files.filter {
                    it.startsWith(CACHE_FILE_PREFIX) || it.startsWith(CONTENT_CACHE_FILE_PREFIX)
                }.forEach { manager.removeItemAtPath("$cache/$it", error = null) }
            }
        }
        catch (_: Exception) {}
    }

    actual suspend fun getCacheAgeHours(context: PlatformContext, owner: ProjectCacheOwner): Long? = withContext(Dispatchers.Default) {
        try {
            storageMutex.withLock {
                val text = NSString.stringWithContentsOfFile(cacheFile(owner), encoding = NSUTF8StringEncoding, error = null)
                    ?: return@withLock null
                val data = Json.decodeFromString<CachedProjects>(text)
                if (!data.belongsTo(owner)) return@withLock null
                val now = NSDate().timeIntervalSince1970.toLong() * 1000
                (now - data.cachedAt) / 3_600_000L
            }
        } catch (_: Exception) { null }
    }
}
