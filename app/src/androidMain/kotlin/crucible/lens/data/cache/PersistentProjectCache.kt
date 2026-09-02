package crucible.lens.data.cache

import android.util.AtomicFile
import android.util.Log
import crucible.lens.data.model.Project
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }
private const val LEGACY_CACHE_FILE = "projects_cache.json"
private const val LEGACY_CONTENT_CACHE_FILE = "project_contents_cache.json"
private const val CACHE_FILE_PREFIX = "projects_cache_"
private const val CONTENT_CACHE_FILE_PREFIX = "project_contents_cache_"
private const val CACHE_DIRECTORY = "project_cache"
private val storageMutex = Mutex()

private fun cacheDirectory(context: PlatformContext) = File(context.filesDir, CACHE_DIRECTORY).also(File::mkdirs)

private fun projectsFile(context: PlatformContext, owner: ProjectCacheOwner) =
    File(cacheDirectory(context), "$CACHE_FILE_PREFIX${owner.storageKey()}.json")

private fun contentsFile(context: PlatformContext, owner: ProjectCacheOwner) =
    File(cacheDirectory(context), "$CONTENT_CACHE_FILE_PREFIX${owner.storageKey()}.json")

private fun readContents(file: File): CachedProjectContents? = runCatching {
    if (!file.exists()) return null
    decodeProjectContents(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
}.getOrNull()

private fun writeContents(file: File, contents: CachedProjectContents) {
    val atomicFile = AtomicFile(file)
    val output = atomicFile.startWrite()
    try {
        output.write(encodeProjectContents(contents).encodeToByteArray())
        atomicFile.finishWrite(output)
    } catch (error: Exception) {
        atomicFile.failWrite(output)
        throw error
    }
}

private fun writeProjects(file: File, projects: CachedProjects) {
    val atomicFile = AtomicFile(file)
    val output = atomicFile.startWrite()
    try {
        output.write(json.encodeToString(projects).encodeToByteArray())
        atomicFile.finishWrite(output)
    } catch (error: Exception) {
        atomicFile.failWrite(output)
        throw error
    }
}

actual object PersistentProjectCache {

    actual suspend fun save(context: PlatformContext, owner: ProjectCacheOwner, projects: List<Project>) {
        withContext(Dispatchers.IO) {
            try {
                storageMutex.withLock {
                    val data = CachedProjects(owner.accountId, owner.serverUrl, projects, System.currentTimeMillis())
                    writeProjects(projectsFile(context, owner), data)
                }
            } catch (e: Exception) {
                Log.e("PersistentProjectCache", "Failed to save", e)
            }
        }
    }

    actual suspend fun load(context: PlatformContext, owner: ProjectCacheOwner): List<Project>? = withContext(Dispatchers.IO) {
        try {
            storageMutex.withLock {
                val file = projectsFile(context, owner)
                val legacyFile = File(context.filesDir, LEGACY_CACHE_FILE)
                val source = file.takeIf(File::exists) ?: legacyFile.takeIf(File::exists) ?: return@withLock null
                val data = json.decodeFromString<CachedProjects>(AtomicFile(source).openRead().bufferedReader().use { it.readText() }).migrate(owner)
                if (data == null) {
                    source.delete()
                    return@withLock null
                }
                if (source == legacyFile) {
                    writeProjects(file, data)
                    legacyFile.delete()
                }
                data.projects
            }
        } catch (e: Exception) {
            Log.e("PersistentProjectCache", "Failed to load", e)
            null
        }
    }

    actual suspend fun saveContent(context: PlatformContext, owner: ProjectCacheOwner, content: CachedProjectContent) {
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                val file = contentsFile(context, owner)
                val legacyFile = File(context.filesDir, LEGACY_CONTENT_CACHE_FILE)
                val source = file.takeIf(File::exists) ?: legacyFile.takeIf(File::exists)
                val current = source?.let(::readContents)?.migrate(owner)
                    ?: CachedProjectContents(accountId = owner.accountId, serverUrl = owner.serverUrl)
                writeContents(file, current.copy(projects = current.projects + (content.projectMfid to content)))
                if (source == legacyFile) legacyFile.delete()
            }
        }
    }

    actual suspend fun loadContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String): CachedProjectContent? =
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                val file = contentsFile(context, owner)
                val legacyFile = File(context.filesDir, LEGACY_CONTENT_CACHE_FILE)
                val source = file.takeIf(File::exists) ?: legacyFile.takeIf(File::exists)
                val contents = source?.let(::readContents)
                val migrated = contents?.migrate(owner)
                if (contents != null && migrated == null) source.delete()
                if (migrated != null && source == legacyFile) {
                    writeContents(file, migrated)
                    legacyFile.delete()
                }
                migrated?.contentFor(owner, projectMfid)
            }
        }

    actual suspend fun loadContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>): List<CachedProjectContent> =
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                val file = contentsFile(context, owner)
                val legacyFile = File(context.filesDir, LEGACY_CONTENT_CACHE_FILE)
                val source = file.takeIf(File::exists) ?: legacyFile.takeIf(File::exists)
                val contents = source?.let(::readContents)
                val migrated = contents?.migrate(owner)
                if (contents != null && migrated == null) source.delete()
                if (migrated != null && source == legacyFile) {
                    writeContents(file, migrated)
                    legacyFile.delete()
                }
                migrated
                    ?.projects
                    ?.filterKeys { it in projectMfids }
                    ?.values
                    ?.toList()
                    ?: emptyList()
            }
        }

    actual suspend fun removeContent(context: PlatformContext, owner: ProjectCacheOwner, projectMfid: String) {
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                val file = contentsFile(context, owner)
                val current = readContents(file)?.migrate(owner) ?: return@withLock
                writeContents(file, current.copy(projects = current.projects - projectMfid))
            }
        }
    }

    actual suspend fun retainContents(context: PlatformContext, owner: ProjectCacheOwner, projectMfids: Set<String>) {
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                val file = contentsFile(context, owner)
                val current = readContents(file)?.migrate(owner) ?: return@withLock
                writeContents(file, current.retaining(projectMfids))
            }
        }
    }

    actual suspend fun clear(context: PlatformContext) {
        withContext(Dispatchers.IO) {
            try {
                storageMutex.withLock {
                    File(context.filesDir, LEGACY_CACHE_FILE).delete()
                    File(context.filesDir, LEGACY_CONTENT_CACHE_FILE).delete()
                    cacheDirectory(context).deleteRecursively()
                }
            }
            catch (e: Exception) { Log.e("PersistentProjectCache", "Failed to clear", e) }
        }
    }

    actual suspend fun getCacheAgeHours(context: PlatformContext, owner: ProjectCacheOwner): Long? = withContext(Dispatchers.IO) {
        try {
            storageMutex.withLock {
                val file = projectsFile(context, owner)
                if (!file.exists()) return@withLock null
                val data = json.decodeFromString<CachedProjects>(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
                if (!data.belongsTo(owner)) return@withLock null
                (System.currentTimeMillis() - data.cachedAt) / 3_600_000L
            }
        } catch (e: Exception) { null }
    }
}
