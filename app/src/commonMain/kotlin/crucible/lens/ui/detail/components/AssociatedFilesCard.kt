package crucible.lens.ui.detail.components
import crucible.lens.ui.common.AppContentAlpha
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
import crucible.lens.ui.detail.AssociatedFileAction
import crucible.lens.ui.detail.AssociatedFileActionKey
import crucible.lens.ui.detail.AssociatedFileActionState
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
            FileActionVisual.Disabled -> AppIcon(icon, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AppContentAlpha.Disabled))
            FileActionVisual.Enabled -> IconButton(onClick = onClick) {
                AppIcon(icon, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun AssociatedFilesCard(
    datasetUuid: String,
    actionStates: Map<AssociatedFileActionKey, AssociatedFileActionState>,
    onResolveAction: (AssociatedFileActionKey) -> Unit,
    onClearAction: (AssociatedFileActionKey) -> Unit,
    initialExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var state by remember { mutableStateOf<AssociatedFilesState>(AssociatedFilesState.Idle) }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()
    val repository = koinInject<CrucibleRepository>()

    fun fetch() {
        scope.launch {
            state = AssociatedFilesState.Loading
            val newState = when (val result = repository.fetchDatasetFiles(datasetUuid)) {
                is ApiResult.Success -> if (result.data.isEmpty()) AssociatedFilesState.Empty
                                        else AssociatedFilesState.Success(result.data)
                is ApiResult.Error -> if (result.code == 404) AssociatedFilesState.Empty
                                     else AssociatedFilesState.Err(result.message)
            }
            state = newState
        }
    }

    LaunchedEffect(datasetUuid) { fetch() }

    val filesState = state
    if (filesState !is AssociatedFilesState.Success) return

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
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
                        modifier = Modifier.weight(1f)
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesState.files.sortedBy { it.filename }.forEach { file ->
                            val name = displayName(file.filename)
                            val ingested = file.storagePath != null
                            val downloadKey = AssociatedFileActionKey(datasetUuid, file.mfid, AssociatedFileAction.DOWNLOAD)
                            val shareKey = AssociatedFileActionKey(datasetUuid, file.mfid, AssociatedFileAction.SHARE)
                            val downloadState = actionStates[downloadKey]
                            val shareState = actionStates[shareKey]

                            LaunchedEffect(downloadState) {
                                val ready = downloadState as? AssociatedFileActionState.Ready ?: return@LaunchedEffect
                                try {
                                    openUrl(platformCtx, ready.url)
                                } finally {
                                    onClearAction(downloadKey)
                                }
                            }
                            LaunchedEffect(shareState) {
                                val ready = shareState as? AssociatedFileActionState.Ready ?: return@LaunchedEffect
                                try {
                                    shareText(platformCtx, ready.url, name)
                                } finally {
                                    onClearAction(shareKey)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppIcon(fileIcon(name), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val statusText = when {
                                        !ingested -> "Pending"
                                        file.size != null -> formatFileSize(file.size)
                                        else -> null
                                    }
                                    if (statusText != null) {
                                        Text(
                                            statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    (downloadState as? AssociatedFileActionState.Error)?.let { error ->
                                        FileActionErrorRow("Download", error.message) { onResolveAction(downloadKey) }
                                    }
                                    (shareState as? AssociatedFileActionState.Error)?.let { error ->
                                        FileActionErrorRow("Share", error.message) { onResolveAction(shareKey) }
                                    }
                                }
                                FileActionSlot(
                                    icon = AppIcons.Download,
                                    visual = when {
                                        downloadState is AssociatedFileActionState.Resolving -> FileActionVisual.Loading
                                        !ingested -> FileActionVisual.Disabled
                                        else -> FileActionVisual.Enabled
                                    },
                                    onClick = { onResolveAction(downloadKey) }
                                )
                                FileActionSlot(
                                    icon = AppIcons.Share,
                                    visual = when {
                                        shareState is AssociatedFileActionState.Resolving -> FileActionVisual.Loading
                                        !ingested -> FileActionVisual.Disabled
                                        else -> FileActionVisual.Enabled
                                    },
                                    onClick = { onResolveAction(shareKey) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileActionErrorRow(label: String, message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label: $message",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
