@file:OptIn(ExperimentalMaterial3Api::class)

package crucible.lens.ui.instruments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.dateGroupKey
import crucible.lens.data.util.matchesSearch
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.shareText
import crucible.lens.platform.showToast
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.CollapsingAppTopBar
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.EmptyListCard
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.GroupByOption
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.LoadingItem
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ResourceControlsBar
import crucible.lens.ui.common.ResourceRow
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.stateMapSaver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class InstrumentDatasetGroupBy(val label: String) {
    NONE("None"), MEASUREMENT("Measurement"), PROJECT("Project"), DATE("Date"),
    SESSION("Session"), FORMAT("Format"), OWNER("Owner")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InstrumentDetailScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onDatasetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onManageInstrument: () -> Unit = {},
    graphExplorerUrl: String = ""
) {
    val viewModel: InstrumentDetailViewModel = koinViewModel()
    val repository = koinInject<CrucibleRepository>()
    val prefs = koinInject<AppPreferences>()
    val instrument by viewModel.instrument.collectAsStateWithLifecycle()
    val datasetsState by viewModel.datasetsState.collectAsStateWithLifecycle()
    // Saveable, like ProjectDetailScreen's, so a typed filter survives opening a dataset and
    // coming back — the same reason expandedGroups below is saveable.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortState by remember { mutableStateOf(SortState(SortField.DATE, false)) }
    var groupBy by remember { mutableStateOf(InstrumentDatasetGroupBy.MEASUREMENT) }
    val expandedGroups = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Boolean>() }

    var overflowMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val platformCtx = getPlatformContext()

    val datasets = (datasetsState as? LoadState.Success)?.data ?: emptyList()
    val filteredDatasets = remember(datasetsState, searchQuery, sortState) {
        val filtered = if (searchQuery.isBlank()) datasets else datasets.filter { it.matchesSearch(searchQuery) }
        filtered.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { timestamp ?: creationTime ?: "" })
    }

    val groupedDatasets = remember(filteredDatasets, groupBy) {
        if (groupBy == InstrumentDatasetGroupBy.NONE) emptyList()
        else filteredDatasets.groupBy { d ->
            when (groupBy) {
                InstrumentDatasetGroupBy.NONE        -> ""
                InstrumentDatasetGroupBy.MEASUREMENT -> d.measurement ?: "No measurement"
                InstrumentDatasetGroupBy.PROJECT     -> d.projectId?.let { pid ->
                    repository.getCachedProjects()?.find { it.projectId == pid }?.title ?: pid
                } ?: "No project"
                InstrumentDatasetGroupBy.DATE        -> dateGroupKey(d.timestamp)
                InstrumentDatasetGroupBy.SESSION     -> d.sessionName ?: "No session"
                InstrumentDatasetGroupBy.FORMAT      -> d.dataFormat ?: "No format"
                InstrumentDatasetGroupBy.OWNER       -> d.ownerOrcid ?: "Unknown owner"
            }
        }.entries.sortedBy { it.key.lowercase() }
    }

    // Restore the persisted grouping on first composition, mirroring ProjectDetailScreen. valueOf
    // is guarded because a stored name can outlive its enum entry across an app update.
    LaunchedEffect(Unit) {
        groupBy = runCatching { InstrumentDatasetGroupBy.valueOf(prefs.instrumentGroupBy.first()) }
            .getOrDefault(InstrumentDatasetGroupBy.MEASUREMENT)
    }

    LaunchedEffect(instrumentId) { viewModel.load(instrumentId) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        modifier = modifier,
        topBar = {
            CollapsingAppTopBar(
                name = instrument?.instrumentName ?: instrumentId,
                icon = AppIcons.Instrument,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onTitleClick = onManageInstrument,
                expandedContent = {
                    val type = instrument?.instrumentType?.takeIf { it.isNotBlank() }
                    val location = instrument?.location?.takeIf { it.isNotBlank() }
                    if (type != null || location != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (type != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.Instrument, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (type != null && location != null) {
                                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (location != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.LocationAlt, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onTogglePin) {
                        AppIcon(AppIcons.Pinned, filled = isPinned, tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) { AppIcon(AppIcons.MoreVert) }
                        DropdownMenu(expanded = overflowMenuExpanded, onDismissRequest = { overflowMenuExpanded = false }) {
                            instrument?.let { instr ->
                                CopyIdMenuItem {
                                    overflowMenuExpanded = false
                                    copyToClipboard(platformCtx, instr.uniqueId)
                                }
                                ShareMenuItem {
                                    overflowMenuExpanded = false
                                    val text = "${instr.instrumentName ?: instr.uniqueId}\nID: ${instr.uniqueId}"
                                    shareText(platformCtx, text, instr.instrumentName ?: instr.uniqueId)
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Manage instrument") },
                                    leadingIcon = { AppIcon(AppIcons.Settings) },
                                    onClick = { overflowMenuExpanded = false; onManageInstrument() }
                                )
                                HorizontalDivider()
                                RefreshMenuItem {
                                    overflowMenuExpanded = false
                                    repository.invalidateInstruments()
                                    viewModel.load(instrumentId, forceRefresh = true)
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = datasetsState.isRefreshingNow,
            onRefresh = { viewModel.load(instrumentId, forceRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Name, pin, and type/location live in the collapsing top bar (see CollapsingAppTopBar
            // above) — nothing left to show above the list itself.
            // nestedScroll attached here, inside PullToRefreshBox's content rather than on its own
            // modifier, so it's closer to the list than PullToRefreshBox's own connection and gets
            // first refusal on a downward drag before that connection starts a refresh gesture.
            Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    stickyHeader(key = "instrument_controls") {
                        ResourceControlsBar(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            searchPlaceholder = "Search datasets…",
                            groupOptions = InstrumentDatasetGroupBy.entries.map { opt ->
                                GroupByOption(opt.label, opt == groupBy) {
                                    groupBy = opt
                                    scope.launch { prefs.saveInstrumentGroupBy(opt.name) }
                                    showToast(platformCtx, "Grouped by ${opt.label}")
                                }
                            },
                            sortState = sortState,
                            onSortStateChange = {
                                sortState = it
                                showToast(platformCtx, "Sorted by ${it.field.label} ${if (it.ascending) "↑" else "↓"}")
                            }
                        )
                    }

                    when (val state = datasetsState) {
                        is LoadState.Loading -> item(key = "loading") {
                            LoadingItem(label = "Loading datasets…")
                        }

                        is LoadState.Error -> item(key = "error") {
                            ErrorCard(
                                title = "Error Loading Datasets",
                                message = state.message,
                                modifier = Modifier.padding(16.dp),
                                onRetry = { viewModel.load(instrumentId, forceRefresh = true) }
                            )
                        }

                        is LoadState.Success -> {
                            if (filteredDatasets.isEmpty()) {
                                item(key = "empty") {
                                    EmptyListCard(
                                        resourceName = "Datasets",
                                        defaultIcon = AppIcons.Dataset,
                                        isFiltered = searchQuery.isNotBlank(),
                                        emptyMessage = "No datasets found for this instrument.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else if (groupBy == InstrumentDatasetGroupBy.NONE) {
                                items(filteredDatasets, key = { it.uniqueId }) { dataset ->
                                    ResourceRow(
                                        title = dataset.name,
                                        subtitle = dataset.projectId ?: "No project",
                                        uniqueId = dataset.uniqueId,
                                        subtitleMonospace = false,
                                        graphExplorerUrl = graphExplorerUrl,
                                        projectId = dataset.projectId,
                                        resourceType = "dataset",
                                        onClick = { onDatasetClick(dataset.uniqueId) }
                                    )
                                }
                            } else {
                                groupedDatasets.forEach { (groupKey, datasetsInGroup) ->
                                    val expanded = expandedGroups[groupKey] == true
                                    stickyHeader(key = "header_$groupKey") {
                                        SectionHeader(
                                            title = groupKey,
                                            count = datasetsInGroup.size,
                                            icon = AppIcons.Dataset,
                                            expanded = expanded,
                                            onToggle = { expandedGroups[groupKey] = !expanded }
                                        )
                                    }
                                    if (expanded) {
                                        items(datasetsInGroup, key = { it.uniqueId }) { dataset ->
                                            ResourceRow(
                                                title = dataset.name,
                                                subtitle = dataset.projectId ?: "No project",
                                                uniqueId = dataset.uniqueId,
                                                subtitleMonospace = false,
                                                graphExplorerUrl = graphExplorerUrl,
                                                projectId = dataset.projectId,
                                                resourceType = "dataset",
                                                onClick = { onDatasetClick(dataset.uniqueId) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.fromCache) {
                                item(key = "cache_age") {
                                    Text(
                                        text = "Loaded from cache",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                LazyColumnScrollbar(listState = listState, modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
                ScrollToTopButton(
                    visible = showScrollToTop,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }
}
