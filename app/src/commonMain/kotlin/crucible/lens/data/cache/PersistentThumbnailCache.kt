package crucible.lens.data.cache

import crucible.lens.platform.PlatformContext

expect object PersistentThumbnailCache {
    fun save(context: PlatformContext, uuid: String, thumbnails: List<String>)
    fun load(context: PlatformContext, uuid: String): List<String>?
    fun clear(context: PlatformContext, uuid: String? = null)
}
