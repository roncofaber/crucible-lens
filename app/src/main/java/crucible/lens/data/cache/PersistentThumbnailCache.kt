package crucible.lens.data.cache

import android.content.Context
import android.util.Log
import java.io.File

object PersistentThumbnailCache {

    private const val TAG = "PersistentThumbnailCache"
    private const val DIR = "thumbnails"
    private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    private fun dir(context: Context) = File(context.cacheDir, DIR).also { it.mkdirs() }
    private fun file(context: Context, uuid: String) = File(dir(context), "$uuid.txt")

    fun save(context: Context, uuid: String, thumbnails: List<String>) {
        if (thumbnails.isEmpty()) return
        try {
            file(context, uuid).writeText(thumbnails.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save thumbnails for $uuid", e)
        }
    }

    fun load(context: Context, uuid: String): List<String>? {
        return try {
            val f = file(context, uuid)
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) {
                f.delete()
                return null
            }
            val lines = f.readLines().filter { it.isNotBlank() }
            lines.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnails for $uuid", e)
            null
        }
    }

    fun clear(context: Context, uuid: String? = null) {
        try {
            if (uuid != null) file(context, uuid).delete()
            else dir(context).deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear thumbnail cache", e)
        }
    }

}
