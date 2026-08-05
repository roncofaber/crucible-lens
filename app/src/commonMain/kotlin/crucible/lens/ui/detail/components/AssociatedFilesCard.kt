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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.data.util.formatFileSize
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import crucible.lens.ui.theme.emphasizedTitleMedium

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

/** Visual state of one fixed-size action slot — never changes the slot's size, only its content. */
private enum class FileActionVisual { Enabled, Disabled, Loading }

/**
 * One trailing action (download or share), always occupying the same 48dp square regardless of
 * [visual] — the same footprint `IconButton` already reserves for its minimum touch target, so
 * this doesn't shrink the tap area, it just keeps that exact size even when showing a spinner or
 * a disabled icon instead of a clickable one. Two of these placed side by side is what keeps the
 * download/share icons in the same two screen columns for every row, in every state (idle,
 * loading, pending, errored) — nothing before or after this slot ever changes width, so pressing
 * one to trigger its spinner can't shift anything else in the row.
 */
@Composable
private fun FileActionSlot(icon: AppIconToken, visual: FileActionVisual, onClick: () -> Unit) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when (visual) {
            FileActionVisual.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            FileActionVisual.Disabled -> AppIcon(icon, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            FileActionVisual.Enabled -> IconButton(onClick = onClick) {
                AppIcon(icon, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
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
    // Separate per-action maps (not one per-file flag) so tapping Share shows its spinner only in
    // the share slot, leaving the download slot's icon undisturbed, and vice versa.
    val downloadingFiles = remember { mutableStateMapOf<String, Boolean>() }
    val sharingFiles = remember { mutableStateMapOf<String, Boolean>() }
    val errorFiles = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()
    val repository = koinInject<CrucibleRepository>()

    fun fetch() {
        scope.launch {
            state = AssociatedFilesState.Loading
            downloadingFiles.clear()
            sharingFiles.clear()
            val newState = when (val result = repository.fetchDatasetFiles(datasetUuid)) {
                is ApiResult.Success -> if (result.data.isEmpty()) AssociatedFilesState.Empty
                                        else AssociatedFilesState.Success(result.data)
                is ApiResult.Error -> if (result.code == 404) AssociatedFilesState.Empty
                                     else AssociatedFilesState.Err(result.message)
            }
            state = newState
        }
    }

    fun openFile(file: crucible.lens.data.model.AssociatedFile, share: Boolean) {
        scope.launch {
            val loadingMap = if (share) sharingFiles else downloadingFiles
            loadingMap[file.mfid] = true
            errorFiles.remove(file.mfid)
            try {
                val url = (repository.fetchFileUrl(file.mfid) as? ApiResult.Success)?.data
                if (url != null) {
                    val name = displayName(file.filename)
                    if (share) shareText(platformCtx, url, name) else openUrl(platformCtx, url)
                } else {
                    errorFiles[file.mfid] = true
                }
            } finally {
                loadingMap.remove(file.mfid)
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
                        style = MaterialTheme.typography.emphasizedTitleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesState.files.sortedBy { it.filename }.forEach { file ->
                            val name = displayName(file.filename)
                            val ingested = file.storagePath != null
                            val isDownloading = downloadingFiles[file.mfid] == true
                            val isSharing = sharingFiles[file.mfid] == true
                            val hasError = errorFiles[file.mfid] == true
                            val actionsEnabled = ingested && !hasError
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppIcon(fileIcon(name), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    // One status line beneath the (possibly 2-line) name — error takes
                                    // priority over pending, which takes priority over the plain size,
                                    // so there's always at most one line here regardless of state.
                                    val statusText = when {
                                        hasError -> "Unavailable"
                                        !ingested -> "Pending"
                                        file.size != null -> formatFileSize(file.size)
                                        else -> null
                                    }
                                    if (statusText != null) {
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FileActionSlot(
                                    icon = AppIcons.Download,
                                    visual = when {
                                        isDownloading -> FileActionVisual.Loading
                                        !actionsEnabled -> FileActionVisual.Disabled
                                        else -> FileActionVisual.Enabled
                                    },
                                    onClick = { openFile(file, share = false) }
                                )
                                FileActionSlot(
                                    icon = AppIcons.Share,
                                    visual = when {
                                        isSharing -> FileActionVisual.Loading
                                        !actionsEnabled -> FileActionVisual.Disabled
                                        else -> FileActionVisual.Enabled
                                    },
                                    onClick = { openFile(file, share = true) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
