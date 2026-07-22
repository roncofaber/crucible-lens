package crucible.lens.ui.detail.components
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.util.formatDateTime
import crucible.lens.data.util.formatFileSize
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.showToast
import crucible.lens.ui.common.StandardSizeAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun DatasetDetailsCard(
    dataset: Dataset,
    onProjectClick: (String) -> Unit,
    onUserClick: (String) -> Unit = {},
    onInstrumentClick: (String) -> Unit = {},
    onShowQr: () -> Unit = {},
    initialAdvanced: Boolean = false,
    onAdvancedChange: (Boolean) -> Unit = {}
) {
    val platformCtx = getPlatformContext()
    var advanced by remember { mutableStateOf(initialAdvanced) }
    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(StandardSizeAnim)) {
            val projectId = dataset.projectId

            // Header: title + action icons (copy, open, share, QR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dataset Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            copyToClipboard(platformCtx, dataset.uniqueId)
                            showToast(platformCtx, "ID copied")
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        AppIcon(AppIcons.CopyToClipboard,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onShowQr, modifier = Modifier.size(38.dp)) {
                        AppIcon(AppIcons.ShowQrCode,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // MFID left-aligned below title
            Text(
                text = dataset.uniqueId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Deletion warning
            val datasetDeletionStatus = (dataset.deletionRequest?.get("status") as? JsonPrimitive)?.content
            if (datasetDeletionStatus != null) {
                Surface(
                    color = when (datasetDeletionStatus) {
                        "approved" -> MaterialTheme.colorScheme.errorContainer
                        "pending"  -> MaterialTheme.colorScheme.tertiaryContainer
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(AppIcons.Warning, modifier = Modifier.size(16.dp))
                        Text(
                            "Deletion ${datasetDeletionStatus.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Basic fields
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                InfoRow(icon = AppIcons.Sample, label = "Measurement", value = dataset.measurement ?: "None")
                InfoRow(icon = AppIcons.Tag, label = "Session", value = dataset.sessionName ?: "None")
                if (dataset.instrumentName != null) {
                    val instrumentScope = rememberCoroutineScope()
                    ClickableInfoRow(
                        icon = AppIcons.Instrument,
                        label = "Instrument",
                        value = dataset.instrumentName,
                        onClick = {
                            instrumentScope.launch {
                                val instruments = CacheManager.getInstruments()
                                    ?: withContext(Dispatchers.Default) {
                                        when (val resp = ApiClient.service.getInstruments()) {
                                            is ApiResult.Success -> resp.data.also { CacheManager.cacheInstruments(it) }
                                            is ApiResult.Error -> null
                                        }
                                    }
                                val instrument = instruments?.find { it.instrumentName == dataset.instrumentName }
                                if (instrument != null) onInstrumentClick(instrument.uniqueId)
                            }
                        }
                    )
                } else {
                    InfoRow(icon = AppIcons.Instrument, label = "Instrument", value = "None")
                }
                if (projectId != null) {
                    ClickableInfoRow(icon = AppIcons.Project, label = "Project", value = projectId, onClick = { onProjectClick(projectId) })
                } else {
                    InfoRow(icon = AppIcons.Project, label = "Project", value = "None")
                }
                InfoRow(icon = AppIcons.Timestamp, label = "Timestamp", value = formatDateTime(dataset.timestamp))
            }

            // Advanced fields
            if (advanced) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                AdvancedGroupLabel("Ownership")
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    InfoRow(
                        icon = when (dataset.isPublic) {
                            true  -> AppIcons.Public
                            false -> AppIcons.Private
                            null  -> AppIcons.UnknownVisibility
                        },
                        label = "Visibility",
                        value = when (dataset.isPublic) { true -> "Public"; false -> "Private"; null -> "None" }
                    )
                    when {
                        dataset.owner?.username != null -> {
                            val ownerLabel = buildString {
                                val name = listOfNotNull(dataset.owner.firstName?.firstOrNull()?.let { "$it." }, dataset.owner.lastName).joinToString(" ")
                                if (name.isNotBlank()) append(name)
                                else append("@${dataset.owner.username}")
                            }
                            ClickableInfoRow(
                                icon = AppIcons.User, label = "Owner", value = ownerLabel,
                                onClick = { onUserClick(dataset.owner.username) }
                            )
                        }
                        dataset.ownerOrcid != null -> ClickableInfoRow(
                            icon = AppIcons.User, label = "Owner", value = dataset.ownerOrcid,
                            onClick = { onUserClick(dataset.ownerOrcid) }
                        )
                        else -> InfoRow(icon = AppIcons.User, label = "Owner", value = "None")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                AdvancedGroupLabel("Files")
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    InfoRow(icon = AppIcons.FilePdf, label = "Format", value = dataset.dataFormat ?: "None")
                    InfoRow(icon = AppIcons.DataType, label = "Data Type", value = dataset.dataType ?: "None")
                    InfoRow(icon = AppIcons.FileStorage, label = "Size", value = dataset.size?.let { formatFileSize(it) } ?: "None")
                    InfoRow(icon = AppIcons.SourceFolder, label = "Source Folder", value = dataset.sourceFolder?.takeIf { it.isNotBlank() } ?: "None")
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

                AdvancedGroupLabel("Dates")
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    InfoRow(icon = AppIcons.CreationDate, label = "Created", value = formatDateTime(dataset.creationTime))
                    InfoRow(icon = AppIcons.ModificationDate, label = "Modified", value = formatDateTime(dataset.modificationTime))
                }
            }

            // Advanced/Basic toggle at bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clickable { val new = !advanced; advanced = new; onAdvancedChange(new) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (advanced) "Basic" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AppIcon(
                        if (advanced) AppIcons.HideContent else AppIcons.ShowContent,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
