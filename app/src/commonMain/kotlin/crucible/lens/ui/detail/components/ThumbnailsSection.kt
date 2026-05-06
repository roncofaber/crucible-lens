package crucible.lens.ui.detail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Thumbnail
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ThumbnailsSection(
    uuid: String,
    thumbnails: List<Thumbnail>,
    onDelete: (thumbnailId: Int) -> Unit = {}
) {
    thumbnails.forEachIndexed { index, thumbnail ->
        var showDeleteDialog by remember { mutableStateOf(false) }

        if (showDeleteDialog && thumbnail.id >= 0) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                title = { Text("Delete thumbnail?") },
                text = { Text("This thumbnail will be permanently removed from the dataset.") },
                confirmButton = {
                    TextButton(onClick = { showDeleteDialog = false; onDelete(thumbnail.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            var imageState by remember { mutableStateOf<String?>(null) }

            var base64Data by remember(uuid, index) { mutableStateOf<ByteArray?>(null) }
            LaunchedEffect(uuid, index) {
                base64Data = withContext(Dispatchers.Default) {
                    try { PlatformBase64.decode(thumbnail.thumbnailB64) } catch (_: Exception) { null }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .then(
                        if (thumbnail.id >= 0)
                            Modifier.combinedClickable(onClick = {}, onLongClick = { showDeleteDialog = true })
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (base64Data != null) {
                    AsyncImage(
                        model = base64Data,
                        contentDescription = "Dataset image ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        onLoading = { imageState = "loading" },
                        onSuccess = { imageState = null },
                        onError = { imageState = "error: ${it.result.throwable.message}" }
                    )
                } else {
                    imageState = "error: Failed to decode base64"
                }

                when {
                    imageState == "loading" -> CircularProgressIndicator()
                    imageState?.startsWith("error") == true -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Failed to load image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (thumbnail.id >= 0) {
                    Text(
                        "Hold to delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

internal sealed class DownloadLinksState {
    object Idle    : DownloadLinksState()
    object Loading : DownloadLinksState()
    object Empty   : DownloadLinksState()
    data class Success(val links: Map<String, String>) : DownloadLinksState()
    data class Err(val message: String) : DownloadLinksState()
}

internal fun displayName(path: String): String =
    path.split("/").drop(1).joinToString("/").ifBlank { path }

internal fun fileIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "h5", "hdf5", "nc", "netcdf", "nxs" -> Icons.Default.Storage
        "tiff", "tif", "png", "jpg", "jpeg", "bmp", "gif" -> Icons.Default.Image
        "pdf" -> Icons.Default.Description
        "csv", "tsv", "txt", "dat", "log" -> Icons.AutoMirrored.Filled.Notes
        "json", "yaml", "yml", "xml", "toml" -> Icons.Default.DataObject
        "zip", "tar", "gz", "bz2", "xz" -> Icons.Default.FolderZip
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
internal fun DownloadLinksCard(
    datasetUuid: String,
    initialExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var state by remember { mutableStateOf<DownloadLinksState>(DownloadLinksState.Idle) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()

    fun fetch(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) isRefreshing = true else state = DownloadLinksState.Loading
            // Check in-memory cache first (55-min TTL matches signed URL expiry)
            val cached = if (!isRefresh) CacheManager.getDownloadLinks(datasetUuid) else null
            val newState = if (cached != null) {
                if (cached.isEmpty()) DownloadLinksState.Empty else DownloadLinksState.Success(cached)
            } else {
                when (val result = ApiClient.service.getDownloadLinks(datasetUuid)) {
                    is ApiResult.Success -> {
                        if (result.data.isEmpty()) DownloadLinksState.Empty
                        else DownloadLinksState.Success(result.data).also {
                            CacheManager.cacheDownloadLinks(datasetUuid, result.data)
                        }
                    }
                    is ApiResult.Error -> if (result.code == 404) DownloadLinksState.Empty
                                         else DownloadLinksState.Err(result.message)
                }
            }
            isRefreshing = false
            state = newState
        }
    }

    // Silently fetch on first composition — cache hit is instant, API call is once per hour
    LaunchedEffect(datasetUuid) { fetch() }

    // Render nothing until we have confirmed files
    val linksState = state
    if (linksState !is DownloadLinksState.Success && !isRefreshing) return

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download Links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { fetch(isRefresh = true) },
                        modifier = Modifier.size(32.dp),
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing)
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (expanded && linksState is DownloadLinksState.Success) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        linksState.links.entries.sortedBy { it.key }.forEach { (path, url) ->
                            val name = displayName(path)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(fileIcon(name), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { openUrl(platformCtx, url) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { shareText(platformCtx, url, name) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Share link", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
