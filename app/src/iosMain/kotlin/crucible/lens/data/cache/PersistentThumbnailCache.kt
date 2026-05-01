@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package crucible.lens.data.cache

import crucible.lens.platform.PlatformContext
import platform.Foundation.*

private const val DIR = "thumbnails"
private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L

private fun cacheDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val caches = paths.firstOrNull() as? String ?: ""
    val dir = "$caches/$DIR"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

actual object PersistentThumbnailCache {

    actual fun save(context: PlatformContext, uuid: String, thumbnails: List<String>) {
        if (thumbnails.isEmpty()) return
        val path = "${cacheDir()}/$uuid.txt"
        (thumbnails.joinToString("\n") as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun load(context: PlatformContext, uuid: String): List<String>? {
        val path = "${cacheDir()}/$uuid.txt"
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) return null
        val attrs = fm.attributesOfItemAtPath(path, error = null)
        val modified = (attrs?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970?.toLong()?.times(1000) ?: 0L
        if (NSDate().timeIntervalSince1970.toLong() * 1000 - modified > MAX_AGE_MS) {
            fm.removeItemAtPath(path, error = null); return null
        }
        val content = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)?.toString()
            ?: return null
        return content.split("\n").filter { it.isNotBlank() }.ifEmpty { null }
    }

    actual fun clear(context: PlatformContext, uuid: String?) {
        val fm = NSFileManager.defaultManager
        if (uuid != null) fm.removeItemAtPath("${cacheDir()}/$uuid.txt", error = null)
        else fm.removeItemAtPath(cacheDir(), error = null)
    }
}
