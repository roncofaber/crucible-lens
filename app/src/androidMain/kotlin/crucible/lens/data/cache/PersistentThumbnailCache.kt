package crucible.lens.data.cache

import android.util.Log
import crucible.lens.platform.PlatformContext
import java.io.File

private const val TAG = "PersistentThumbnailCache"
private const val DIR = "thumbnails"
private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L

actual object PersistentThumbnailCache {
    private fun dir(context: PlatformContext) = File(context.cacheDir, DIR).also { it.mkdirs() }
    private fun file(context: PlatformContext, uuid: String) = File(dir(context), "$uuid.txt")

    actual fun save(context: PlatformContext, uuid: String, thumbnails: List<String>) {
        if (thumbnails.isEmpty()) return
        try { file(context, uuid).writeText(thumbnails.joinToString("\n")) }
        catch (e: Exception) { Log.e(TAG, "Failed to save thumbnails for $uuid", e) }
    }

    actual fun load(context: PlatformContext, uuid: String): List<String>? {
        return try {
            val f = file(context, uuid)
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) { f.delete(); return null }
            f.readLines().filter { it.isNotBlank() }.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnails for $uuid", e)
            null
        }
    }

    actual fun clear(context: PlatformContext, uuid: String?) {
        try {
            if (uuid != null) file(context, uuid).delete()
            else dir(context).deleteRecursively()
        } catch (e: Exception) { Log.e(TAG, "Failed to clear thumbnail cache", e) }
    }
}
