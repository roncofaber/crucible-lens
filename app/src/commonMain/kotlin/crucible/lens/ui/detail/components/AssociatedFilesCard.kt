package crucible.lens.ui.detail.components
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.data.util.formatFileSize
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal sealed class AssociatedFilesState {
    object Idle    : AssociatedFilesState()
    object Loading : AssociatedFilesState()
    object Empty   : AssociatedFilesState()
    data class Success(val files: List<crucible.lens.data.model.AssociatedFile>) : AssociatedFilesState()
    data class Err(val message: String) : AssociatedFilesState()
}

internal fun displayName(path: String): String = path.substringAfterLast('/').ifBlank { path }

internal fun fileIcon(name: String): AppIconToken {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "h5", "hdf5", "nc", "netcdf", "nxs" -> AppIcons.FileStorage
        "tiff", "tif", "png", "jpg", "jpeg", "bmp", "gif" -> AppIcons.FileImage
        "pdf" -> AppIcons.FilePdf
        "csv", "tsv", "txt", "dat", "log" -> AppIcons.Notes
        "json", "yaml", "yml", "xml", "toml" -> AppIcons.FileJson
        "zip", "tar", "gz", "bz2", "xz" -> AppIcons.FileArchive
        else -> AppIcons.FileGeneric
    }
}

@Composable
internal fun AssociatedFilesCard(
    datasetUuid: String,
    initialExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var state by remember { mutableStateOf<AssociatedFilesState>(AssociatedFilesState.Idle) }
    val loadingFiles = remember { mutableStateMapOf<String, Boolean>() }
    val errorFiles = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()
    val apiClient = koinInject<ApiClient>()
    val cacheManager = koinInject<CacheManager>()

    fun fetch() {
        scope.launch {
            state = AssociatedFilesState.Loading
            loadingFiles.clear()
            val cached = cacheManager.getDatasetFiles(datasetUuid)
            val newState = if (cached != null) {
                if (cached.isEmpty()) AssociatedFilesState.Empty else AssociatedFilesState.Success(cached)
            } else {
                when (val result = apiClient.service.getDatasetFiles(datasetUuid)) {
                    is ApiResult.Success -> {
                        if (result.data.isEmpty()) AssociatedFilesState.Empty
                        else AssociatedFilesState.Success(result.data).also {
                            cacheManager.cacheDatasetFiles(datasetUuid, result.data)
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
            errorFiles.remove(file.mfid)
            try {
                val cached = cacheManager.getFileUrl(file.mfid)
                val url = if (cached != null) cached else {
                    when (val r = apiClient.service.getFileDownloadLink(file.mfid)) {
                        is ApiResult.Success -> r.data.url.also { cacheManager.cacheFileUrl(file.mfid, it) }
                        is ApiResult.Error -> null
                    }
                }
                if (url != null) {
                    val name = displayName(file.filename)
                    if (share) shareText(platformCtx, url, name) else openUrl(platformCtx, url)
                } else {
                    errorFiles[file.mfid] = true
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
            Column(modifier = Modifier.padding(16.dp).animateContentSize(StandardSizeAnim)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpandChevron(expanded = expanded, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    AppIcon(AppIcons.AttachFile, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Files (${filesState.files.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesState.files.sortedBy { it.filename }.forEach { file ->
                            val name = displayName(file.filename)
                            val ingested = file.storagePath != null
                            val isLoadingFile = loadingFiles[file.mfid] == true
                            val hasError = errorFiles[file.mfid] == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppIcon(fileIcon(name), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (file.size != null) {
                                        Text(formatFileSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isLoadingFile) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(1.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(32.dp))
                                } else if (hasError) {
                                    AppIcon(AppIcons.Unreachable, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Text("Unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 4.dp))
                                } else if (ingested) {
                                    IconButton(onClick = { openFile(file, false) }, modifier = Modifier.size(32.dp)) {
                                        AppIcon(AppIcons.Download, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { openFile(file, true) }, modifier = Modifier.size(32.dp)) {
                                        AppIcon(AppIcons.Share, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                                    AppIcon(AppIcons.Pending, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
