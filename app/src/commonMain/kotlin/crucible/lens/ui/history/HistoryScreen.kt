package crucible.lens.ui.history
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import crucible.lens.platform.showToast
import crucible.lens.ui.common.AppScaffold
import kotlin.math.abs
import kotlin.time.Clock

private enum class HistorySortOrder { NEWEST, OLDEST }

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear history") },
            text = { Text("Remove all ${history.size} items from your browsing history?") },
            confirmButton = {
                Button(
                    onClick = { showClearConfirm = false; onClearHistory() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (sortOrder == HistorySortOrder.NEWEST) "Show oldest first" else "Show newest first") },
                                    leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                                    onClick = {
                                        sortOrder = if (sortOrder == HistorySortOrder.NEWEST) HistorySortOrder.OLDEST else HistorySortOrder.NEWEST
                                        overflowExpanded = false
                                    }
                                )
                                if (history.isNotEmpty()) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Clear all history") },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { overflowExpanded = false; showClearConfirm = true }
                                    )
                                }
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.HistoryToggleOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No history yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Resources you view will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedHistory, key = { it.uuid }) { item ->
                        HistoryCard(
                            item = item,
                            graphExplorerUrl = graphExplorerUrl,
                            onClick = { onItemClick(item.uuid) }
                        )
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
    var menuExpanded by remember { mutableStateOf(false) }

    // Best-effort cache lookups for display enrichment
    val cached = remember(item.uuid) { CacheManager.getResource(item.uuid) }
    val resourceType = remember(item.uuid) { CacheManager.getResourceType(item.uuid) }
    val projectId = remember(cached) {
        when (cached) {
            is Sample -> cached.projectId
            is Dataset -> cached.projectId
            else -> null
        }
    }
    val projectName = remember(projectId) {
        projectId?.let { pid ->
            CacheManager.getProjects()?.find { it.projectId == pid }?.title ?: pid
        }
    }
    val icon = when (item.resourceType ?: resourceType) {
        "sample" -> Icons.Default.Science
        "dataset" -> Icons.Default.Dataset
        else -> Icons.Default.History
    }
    val webUrl = if (projectId != null && graphExplorerUrl.isNotBlank()) {
        if (resourceType == "dataset") "$graphExplorerUrl/$projectId/dataset/${item.uuid}"
        else "$graphExplorerUrl/$projectId/sample-graph/${item.uuid}"
    } else null

    Box {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (projectName != null) {
                        Text(projectName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatRelativeTime(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.uuid, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            CopyIdMenuItem {
                menuExpanded = false
                copyToClipboard(platformContext, item.uuid, "ID")
                showToast(platformContext, "ID copied")
            }
            if (webUrl != null) {
                OpenInWebMenuItem { menuExpanded = false; openUrl(platformContext, webUrl) }
                ShareMenuItem { menuExpanded = false; shareText(platformContext, webUrl, item.name) }
            }
        }
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
