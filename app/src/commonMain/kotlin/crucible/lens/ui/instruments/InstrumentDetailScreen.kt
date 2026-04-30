package crucible.lens.ui.instruments
import crucible.lens.platform.*





import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.util.MONTH_NAMES
import crucible.lens.data.util.dateGroupKey
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingItem
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.showFeedback
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.fadeEndEdge
import kotlinx.coroutines.launch

private enum class InstrumentDatasetGroupBy(val label: String) {
    NONE("None"), MEASUREMENT("Measurement"), PROJECT("Project"), DATE("Date"),
    SESSION("Session"), FORMAT("Format"), OWNER("Owner")
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstrumentDetailScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onDatasetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onSearch: () -> Unit = {}
) {
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var datasets by remember { mutableStateOf<List<Dataset>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortState by remember { mutableStateOf(SortState(SortField.DATE, false)) }
    var groupBy by remember { mutableStateOf(InstrumentDatasetGroupBy.MEASUREMENT) }
    val expandedGroups = remember(groupBy) { mutableStateMapOf<String, Boolean>() }

    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var fromCache by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val platformCtx = getPlatformContext()

    var isRefreshingNow by remember { mutableStateOf(false) }

    val filteredDatasets = remember(datasets, searchQuery, sortState) {
        val list = datasets ?: emptyList()
        val filtered = if (searchQuery.isBlank()) list else list.filter { it.matchesSearch(searchQuery) }
        filtered.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { timestamp ?: creationTime ?: "" })
    }

    val groupedDatasets = remember(filteredDatasets, groupBy) {
        if (groupBy == InstrumentDatasetGroupBy.NONE) emptyList()
        else filteredDatasets.groupBy { d ->
            when (groupBy) {
                InstrumentDatasetGroupBy.NONE        -> ""
                InstrumentDatasetGroupBy.MEASUREMENT -> d.measurement ?: "No measurement"
                InstrumentDatasetGroupBy.PROJECT     -> d.projectId?.let { pid ->
                    CacheManager.getProjects()?.find { it.projectId == pid }?.title ?: pid
                } ?: "No project"
                InstrumentDatasetGroupBy.DATE        -> dateGroupKey(d.timestamp)
                InstrumentDatasetGroupBy.SESSION     -> d.sessionName ?: "No session"
                InstrumentDatasetGroupBy.FORMAT      -> d.dataFormat ?: "No format"
                InstrumentDatasetGroupBy.OWNER       -> d.ownerOrcid ?: "Unknown owner"
            }
        }.entries.sortedBy { it.key.lowercase() }
    }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            error = null
            try {
                val resolvedInstrument = if (!forceRefresh) {
                    CacheManager.getInstruments()?.find { it.uniqueId == instrumentId }
                        ?: (ApiClient.service.getInstrument(instrumentId) as? crucible.lens.data.api.ApiResult.Success)?.data
                } else {
                    (ApiClient.service.getInstrument(instrumentId) as? crucible.lens.data.api.ApiResult.Success)?.data
                }
                if (resolvedInstrument == null) { error = "Instrument not found"; return@launch }
                instrument = resolvedInstrument
                val instrName = resolvedInstrument.instrumentName ?: resolvedInstrument.uniqueId
                if (!forceRefresh) {
                    val cached = CacheManager.getInstrumentDatasets(instrName)
                    if (cached != null) { datasets = cached; fromCache = true; isLoading = false; return@launch }
                }
                fromCache = false
                when (val response = ApiClient.service.getDatasetsByInstrument(instrName)) {
                    is crucible.lens.data.api.ApiResult.Success -> {
                        val body = response.data
                        CacheManager.cacheInstrumentDatasets(instrName, body)
                        datasets = body
                    }
                    is crucible.lens.data.api.ApiResult.Error -> { error = "Failed to load datasets" }
                }
            } catch (e: Exception) {
                error = "Connection error — check your network"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(instrumentId) { loadData() }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instrument") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp)) }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp)) }
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp)) }
                            DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                                instrument?.let { instr ->
                                    CopyIdMenuItem {
                                        overflowMenuExpanded = false
                                        copyToClipboard(platformCtx, instr.uniqueId)
                                        showFeedback(platformCtx, "ID copied")
                                    }
                                    ShareMenuItem {
                                        overflowMenuExpanded = false
                                        val text = "${instr.instrumentName ?: instr.uniqueId}\nID: ${instr.uniqueId}"
                                        shareText(platformCtx, text, instr.instrumentName ?: instr.uniqueId)
                                    }
                                    HorizontalDivider()
                                    RefreshMenuItem { overflowMenuExpanded = false; CacheManager.clearInstrumentsCache(); loadData(forceRefresh = true) }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshingNow,
            onRefresh = { loadData(forceRefresh = true) },
            state = pullRefreshState,
            modifier = modifier.fillMaxSize().padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Fixed header — stays in place during pull-to-refresh
                instrument?.let { instr ->
                    InstrumentHeader(
                        instrument = instr,
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        sortState = sortState,
                        onSortStateChange = { sortState = it; showFeedback(platformCtx, "Sorted by ${it.field.label} ${if (it.ascending) "↑" else "↓"}") },
                        groupBy = groupBy,
                        onGroupByChange = { groupBy = it; showFeedback(platformCtx, "Grouped by ${it.label}") }
                    )
                }

                // Sliding content — offsets down during pull, snaps back on release
                val ptrFraction by animateFloatAsState(
                    targetValue = if (isRefreshingNow) 0f else pullRefreshState.distanceFraction,
                    label = "ptr"
                )
                Box(modifier = Modifier.weight(1f).offset {
                    IntOffset(0, (ptrFraction * 80.dp.toPx()).coerceAtMost(80.dp.toPx()).roundToInt())
                }) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {

                // ── States ────────────────────────────────────────────────────
                when {
                    isLoading -> item(key = "loading") {
                        LoadingItem(label = "Loading datasets…")
                    }
                    error != null -> item(key = "error") {
                        ErrorCard(
                            title = "Error Loading Datasets",
                            message = error ?: "Unknown error",
                            modifier = Modifier.padding(16.dp),
                            onRetry = { loadData(forceRefresh = true) }
                        )
                    }
                    filteredDatasets.isEmpty() -> item(key = "empty") {
                        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Dataset, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (searchQuery.isNotBlank()) "No matching datasets" else "No datasets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Text(if (searchQuery.isNotBlank()) "No datasets match your filter." else "No datasets found for this instrument.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    else -> {
                        if (groupBy == InstrumentDatasetGroupBy.NONE) {
                            // Flat list — no group headers
                            items(filteredDatasets, key = { it.uniqueId }) { dataset ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    DatasetCard(dataset = dataset, onClick = { onDatasetClick(dataset.uniqueId) })
                                }
                            }
                        } else {
                        groupedDatasets.forEach { (groupKey, datasetsInGroup) ->
                            val expanded = expandedGroups[groupKey] == true
                            stickyHeader(key = "header_$groupKey") {
                                GroupStickyHeader(
                                    title = groupKey,
                                    count = datasetsInGroup.size,
                                    expanded = expanded,
                                    onToggle = { expandedGroups[groupKey] = !expanded }
                                )
                            }
                            if (expanded) {
                                items(datasetsInGroup, key = { it.uniqueId }) { dataset ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                        DatasetCard(dataset = dataset, onClick = { onDatasetClick(dataset.uniqueId) })
                                    }
                                }
                            }
                        }
                        }
                        if (fromCache) {
                            item(key = "cache_age") {
                                Text(
                                    text = "Loaded from cache",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
                LazyColumnScrollbar(listState = listState, modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
                ScrollToTopButton(visible = showScrollToTop, onClick = { scope.launch { listState.animateScrollToItem(0) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
                } // end offset Box
            } // end Column
        }
    }

    LaunchedEffect(isLoading) {
        isRefreshingNow = isLoading
    }
}

@Composable
private fun GroupStickyHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Dataset, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                    Text(text = "$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun InstrumentHeader(
    instrument: Instrument,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    sortState: SortState = SortState(),
    onSortStateChange: (SortState) -> Unit = {},
    groupBy: InstrumentDatasetGroupBy = InstrumentDatasetGroupBy.MEASUREMENT,
    onGroupByChange: (InstrumentDatasetGroupBy) -> Unit = {}
) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Name + pin
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Biotech, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val nameScrollState = rememberScrollState()
                        Box(modifier = Modifier.fillMaxWidth().fadeEndEdge(nameScrollState.canScrollForward)) {
                            Text(text = instrument.instrumentName ?: instrument.uniqueId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.horizontalScroll(nameScrollState))
                        }
                    }
                }
                IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                    Icon(if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = if (isPinned) "Unpin" else "Pin", tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current, modifier = Modifier.size(20.dp))
                }
            }

            // Type + location
            val type = instrument.instrumentType?.takeIf { it.isNotBlank() }
            val location = instrument.location?.takeIf { it.isNotBlank() }
            if (type != null || location != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (type != null) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (location != null) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Filter bar + group-by + sort
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) Text("Filter datasets…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            BasicTextField(value = searchQuery, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true)
                        }
                        if (searchQuery.isNotEmpty()) Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).clickable { onSearchChange("") })
                    }
                }
                // Group-by button
                Box {
                    IconButton(onClick = { groupMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = "Group by", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Group by", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp))
                        InstrumentDatasetGroupBy.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (opt == groupBy) Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(6.dp))
                                        else Spacer(modifier = Modifier.size(6.dp))
                                        Text(opt.label)
                                    }
                                },
                                onClick = { onGroupByChange(opt); groupMenuExpanded = false },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                // Sort button
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Sort", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Sort by", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {}, enabled = false, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp))
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (sortState.field == field) Icon(if (sortState.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp))
                                        else Spacer(modifier = Modifier.size(14.dp))
                                        Text(field.label)
                                    }
                                },
                                onClick = {
                                    onSortStateChange(if (sortState.field == field) sortState.copy(ascending = !sortState.ascending) else SortState(field, true))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatasetCard(dataset: Dataset, onClick: () -> Unit) {
    val platformCtx = getPlatformContext()
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Dataset, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = dataset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val subtitle = listOfNotNull(dataset.projectId, dataset.sessionName).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Copy ID") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    copyToClipboard(platformCtx, dataset.uniqueId)
                }
            )
        }
    }
}

