@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.history
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.ListRowDividerInset
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.model.resolvedProjectName
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.IdText
import crucible.lens.ui.common.LongPressMenuBox
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.buildCrucibleWebUrl
import crucible.lens.platform.openInBrowser
import org.koin.compose.koinInject
import crucible.lens.platform.shareText
import crucible.lens.ui.common.AppScaffold
import kotlin.math.abs
import kotlin.time.Clock

private enum class HistorySortOrder { NEWEST, OLDEST }

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    history: List<HistoryItem>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onItemClick: (String) -> Unit,
    onClearHistory: () -> Unit = {},
    graphExplorerUrl: String = "",
    modifier: Modifier = Modifier
) {
    var sortOrder by remember { mutableStateOf(HistorySortOrder.NEWEST) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val sortedHistory = remember(history, sortOrder) {
        if (sortOrder == HistorySortOrder.NEWEST) history else history.reversed()
    }

    if (showClearConfirm) {
        ConfirmationDialog(
            icon = AppIcons.ClearAll,
            title = "Clear history?",
            text = "Remove all ${history.size} items from your browsing history?",
            confirmLabel = "Clear all",
            isDestructive = true,
            onConfirm = { showClearConfirm = false; onClearHistory() },
            onDismiss = { showClearConfirm = false }
        )
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "History",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (sortOrder == HistorySortOrder.NEWEST) "Show oldest first" else "Show newest first") },
                                leadingIcon = { AppIcon(AppIcons.Sort) },
                                onClick = {
                                    sortOrder = if (sortOrder == HistorySortOrder.NEWEST) HistorySortOrder.OLDEST else HistorySortOrder.NEWEST
                                    overflowExpanded = false
                                }
                            )
                            if (history.isNotEmpty()) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Clear all history") },
                                    leadingIcon = { AppIcon(AppIcons.ClearAll, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { overflowExpanded = false; showClearConfirm = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            if (history.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(32.dp).align(Alignment.Center),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AppIcon(AppIcons.HistoryEmpty, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No history yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Resources you view will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(sortedHistory, key = { it.uuid }) { item ->
                        HistoryCard(
                            item = item,
                            graphExplorerUrl = graphExplorerUrl,
                            onClick = { onItemClick(item.uuid) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = ListRowDividerInset))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    graphExplorerUrl: String = "",
    onClick: () -> Unit
) {
    val platformContext = getPlatformContext()
    val repository = koinInject<CrucibleRepository>()

    // Best-effort cache lookups for display enrichment
    val cached = remember(item.uuid) { repository.getCachedResource(item.uuid) }
    val resourceType = remember(item.uuid) { repository.getCachedResourceType(item.uuid) }
    // item.projectId is recorded directly when a history entry is created (see onSaveToHistory in
    // NavGraph.kt), so it's reliable regardless of whether the resource is still in the short-lived
    // resource cache by the time History renders. Falls back to the old cache-derived lookup only
    // for entries recorded before this field existed.
    val projectId = remember(item.projectId, cached) {
        when (cached) {
            is Sample -> cached.resolvedProjectId ?: item.projectId
            is Dataset -> cached.resolvedProjectId ?: item.projectId
            else -> item.projectId
        }
    }
    val projectName = remember(projectId, cached) {
        when (cached) {
            is Sample -> cached.resolvedProjectName
            is Dataset -> cached.resolvedProjectName
            else -> null
        } ?: projectId?.let { pid ->
            repository.getCachedProjects()?.find { it.projectId == pid }?.title ?: pid
        }
    }
    val resolvedResourceType = item.resourceType ?: resourceType
    val icon = when (resolvedResourceType) {
        "sample" -> AppIcons.Sample
        "dataset" -> AppIcons.Dataset
        else -> AppIcons.History
    }
    val webUrl = if (projectId != null && graphExplorerUrl.isNotBlank()) {
        val resourcePath = if (resolvedResourceType == "dataset") "datasets" else "samples"
        buildCrucibleWebUrl(graphExplorerUrl, projectId, resourcePath, item.uuid)
    } else null

    LongPressMenuBox(
        menu = { dismiss ->
            CopyIdMenuItem { dismiss(); copyToClipboard(platformContext, item.uuid, "ID") }
            if (webUrl != null) {
                OpenInWebMenuItem { dismiss(); openInBrowser(platformContext, webUrl) }
                ShareMenuItem { dismiss(); shareText(platformContext, webUrl, item.name) }
            }
        }
    ) { onLongClick ->
        ListItem(
            headlineContent = {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (projectName != null) {
                        Text(projectName, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IdText(item.uuid)
                }
            },
            // Time sits under the icon rather than sharing the ID's line - frees the supporting
            // content to a single consistent line and keeps the relative time visible without
            // competing with the ID for width.
            leadingContent = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatRelativeTime(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = { AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diffMs = abs(Clock.System.now().toEpochMilliseconds() - timestamp)
    val diffMinutes = diffMs / 60000
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffHours < 24 -> "$diffHours hr ago"
        diffDays < 7 -> "$diffDays day${if (diffDays > 1) "s" else ""} ago"
        else -> "${diffDays / 7} wk${if (diffDays / 7 > 1) "s" else ""} ago"
    }
}
