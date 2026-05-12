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

internal sealed class AssociatedFilesState {
    object Idle    : AssociatedFilesState()
    object Loading : AssociatedFilesState()
    object Empty   : AssociatedFilesState()
    data class Success(val files: List<crucible.lens.data.model.AssociatedFile>) : AssociatedFilesState()
    data class Err(val message: String) : AssociatedFilesState()
}

// Extract just the basename from staging paths like crucible-uploads/api-uploads/{user}/{name}
internal fun displayName(path: String): String = path.substringAfterLast('/').ifBlank { path }

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
    var state by remember { mutableStateOf<AssociatedFilesState>(AssociatedFilesState.Idle) }
    // Per-file loading state: mfid -> isLoading
    val loadingFiles = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()

    fun fetch() {
        scope.launch {
            state = AssociatedFilesState.Loading
            loadingFiles.clear()
            val cached = CacheManager.getDatasetFiles(datasetUuid)
            val newState = if (cached != null) {
                if (cached.isEmpty()) AssociatedFilesState.Empty else AssociatedFilesState.Success(cached)
            } else {
                when (val result = ApiClient.service.getDatasetFiles(datasetUuid)) {
                    is ApiResult.Success -> {
                        if (result.data.isEmpty()) AssociatedFilesState.Empty
                        else AssociatedFilesState.Success(result.data).also {
                            CacheManager.cacheDatasetFiles(datasetUuid, result.data)
                        }
                    }
                    is ApiResult.Error -> if (result.code == 404) AssociatedFilesState.Empty
                                         else AssociatedFilesState.Err(result.message)
                }
            }
            state = newState
        }
    }

    fun openFile(file: crucible.lens.data.model.AssociatedFile, share: Boolean) {
        scope.launch {
            loadingFiles[file.mfid] = true
            try {
                val cached = CacheManager.getFileUrl(file.mfid)
                val url = if (cached != null) cached else {
                    when (val r = ApiClient.service.getFileDownloadLink(file.mfid)) {
                        is ApiResult.Success -> r.data.url.also { CacheManager.cacheFileUrl(file.mfid, it) }
                        is ApiResult.Error -> null
                    }
                }
                if (url != null) {
                    val name = displayName(file.filename)
                    if (share) shareText(platformCtx, url, name) else openUrl(platformCtx, url)
                }
            } finally {
                loadingFiles.remove(file.mfid)
            }
        }
    }

    LaunchedEffect(datasetUuid) { fetch() }

    val filesState = state
    if (filesState !is AssociatedFilesState.Success) return

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    val fileCount = (filesState as? AssociatedFilesState.Success)?.files?.size
                    Text(
                        if (fileCount != null) "Files ($fileCount)" else "Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                }

                if (expanded && filesState is AssociatedFilesState.Success) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesState.files.sortedBy { it.filename }.forEach { file ->
                            val name = displayName(file.filename)
                            val ingested = file.storagePath != null
                            val isLoadingFile = loadingFiles[file.mfid] == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(fileIcon(name), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (file.size != null) {
                                        Text(crucible.lens.data.util.formatFileSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isLoadingFile) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(1.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(32.dp))
                                } else if (ingested) {
                                    IconButton(onClick = { openFile(file, false) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Download, "Download", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { openFile(file, true) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Share, "Share", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                                    Icon(Icons.Default.HourglassEmpty, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
