package crucible.lens.ui.detail

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshots.SnapshotStateSet
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Thumbnail

/**
 * Bundles the sibling-pager runtime caches owned by [ResourceDetailViewModel].
 * Passed as a single parameter to [ResourceDetailScreen] so the composable
 * contract stays clean while the maps live safely in the ViewModel.
 */
class ResourceDetailCache(
    val loadedResources: SnapshotStateMap<String, CrucibleResource>,
    val enrichedUuids: MutableSet<String>,
    val failedEnrichmentUuids: SnapshotStateSet<String>,
    val loadedThumbnails: SnapshotStateMap<String, List<Thumbnail>>,
    val seedThumbnails: (uuid: String, thumbnails: List<Thumbnail>) -> Unit
)
