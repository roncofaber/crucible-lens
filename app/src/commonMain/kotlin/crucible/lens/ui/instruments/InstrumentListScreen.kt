@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.instruments

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.Instrument
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ToggleHiddenMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.SearchBar
import crucible.lens.ui.common.ResourceListDividerInset
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.SwipeAction
import crucible.lens.ui.common.SwipeToHideItem
import crucible.lens.ui.common.hideWithUndo
import crucible.lens.platform.showToast
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InstrumentListScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onInstrumentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    pinnedInstruments: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
    hiddenInstruments: Set<String> = emptySet(),
    onToggleHide: (String) -> Unit = {}
) {
    val platformContext = getPlatformContext()
    val viewModel: InstrumentListViewModel = koinViewModel()
    val loadState by viewModel.loadState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hiddenExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortState by remember { mutableStateOf(SortState(SortField.NAME, true)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Instruments pending hide — excluded from activeInstruments so LazyColumn animates the
    // removal cleanly. onToggleHide is only called after the snackbar window closes without undo.
    val pendingHide = remember { mutableStateMapOf<String, Boolean>() }
    // Generation counter per instrument — bumped on undo so the re-shown item's items() key
    // changes, giving it a fresh SwipeToDismissBoxState (see SwipeToHideItem's doc).
    val undoGenerations = remember { mutableStateMapOf<String, Int>() }

    val instruments = (loadState as? LoadState.Success)?.data ?: emptyList()
    val filteredInstruments = remember(instruments, searchQuery) {
        val list = instruments
        if (searchQuery.isBlank()) list
        else list.filter { it.matchesSearch(searchQuery) }
    }

    // Not wrapped in remember: pendingHide is a SnapshotStateMap, and reading it directly here
    // (during composition) is what makes this recompute correctly when a hide/undo toggles it —
    // wrapping in remember(pendingHide) would need a stable equality key, which a mutable map
    // doesn't give us.
    val activeInstrumentsUnsorted = filteredInstruments
        .filter { it.uniqueId !in hiddenInstruments && pendingHide[it.uniqueId] != true }
    val activeInstruments = remember(activeInstrumentsUnsorted, sortState) {
        activeInstrumentsUnsorted.applySortState(
            sortState,
            name = { instrumentName?.lowercase() ?: uniqueId.lowercase() },
            mfid = { instrumentType?.lowercase() ?: "" },
            date = { "" }
        )
    }

    val hiddenInstrumentsList = remember(loadState, hiddenInstruments) {
        instruments.filter { it.uniqueId in hiddenInstruments }
    }



    LaunchedEffect(Unit) { /* ViewModel loads on init */ }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Instruments",
                onBack = onBack,
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            ToggleHiddenMenuItem(hiddenExpanded) { hiddenExpanded = !hiddenExpanded; menuExpanded = false }
                            RefreshMenuItem { menuExpanded = false; viewModel.load(forceRefresh = true) }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loadState.isRefreshingNow,
            onRefresh = { viewModel.load(forceRefresh = true) },
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Bottom padding clears the ScrollToTopButton FAB (42dp + 16dp margin) so the
                    // last item — including the Hidden section header/rows — is never obscured.
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    stickyHeader(key = "search_bar") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    placeholder = "Search by name, type, manufacturer…",
                                    modifier = Modifier.weight(1f),
                                    accentStyle = true
                                )
                                Box {
                                    IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                                        AppIcon(AppIcons.Sort,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                        // Name and Type (mapped to mfid slot) are the two meaningful instrument sort axes
                                        listOf(SortField.NAME to "Name", SortField.MFID to "Type").forEach { (field, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                leadingIcon = {
                                                    if (sortState.field == field)
                                                        AppIcon(if (sortState.ascending) AppIcons.ParentResource else AppIcons.ChildResource,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    else Spacer(Modifier.size(14.dp))
                                                },
                                                onClick = {
                                                    sortState = if (sortState.field == field)
                                                        sortState.copy(ascending = !sortState.ascending)
                                                    else SortState(field, true)
                                                    sortMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (val state = loadState) {
                        is LoadState.Loading -> item(key = "__loading__") {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.85f),
                                contentAlignment = Alignment.Center
                            ) { LoadingContent(title = "Loading Instruments") }
                        }
                        is LoadState.Error -> item(key = "__error__") {
                            Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                                ErrorCard(
                                    title = "Error Loading Instruments",
                                    message = state.message,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onRetry = { viewModel.load(forceRefresh = true) }
                                )
                            }
                        }
                        is LoadState.Success -> if (activeInstruments.isEmpty() && hiddenInstrumentsList.isEmpty()) item(key = "__empty__") {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.7f),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AppIcon(
                                                if (searchQuery.isNotBlank()) AppIcons.SearchOff else AppIcons.Instrument,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                if (searchQuery.isNotBlank()) "No matching instruments" else "No instruments",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            if (searchQuery.isNotBlank()) "No instruments match your search." else "No instruments found.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        else {
                            // Active instruments with swipe-to-hide
                            items(activeInstruments, key = { "${it.uniqueId}:${undoGenerations[it.uniqueId] ?: 0}" }) { instrument ->
                                SwipeToHideItem(
                                    direction = SwipeToDismissBoxValue.EndToStart,
                                    action = SwipeAction(
                                        icon = AppIcons.HideContent,
                                        label = "Hide",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    onDismiss = {
                                        hideWithUndo(
                                            scope = scope,
                                            snackbarHostState = snackbarHostState,
                                            itemLabel = instrument.instrumentName ?: instrument.uniqueId,
                                            onPending = { pending ->
                                                if (pending) pendingHide[instrument.uniqueId] = true
                                                else pendingHide.remove(instrument.uniqueId)
                                            },
                                            onConfirmedHide = { onToggleHide(instrument.uniqueId) },
                                            onUndone = {
                                                onToggleHide(instrument.uniqueId)
                                                undoGenerations[instrument.uniqueId] = (undoGenerations[instrument.uniqueId] ?: 0) + 1
                                            }
                                        )
                                    }
                                ) {
                                    InstrumentCard(
                                        instrument = instrument,
                                        isPinned = instrument.uniqueId in pinnedInstruments,
                                        onTogglePin = {
                                            showToast(platformContext, if (instrument.uniqueId in pinnedInstruments) "Instrument unpinned" else "Instrument pinned")
                                            onTogglePin(instrument.uniqueId)
                                        },
                                        onClick = { onInstrumentClick(instrument.uniqueId) }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
                            }

                            // Hidden instruments section
                            if (hiddenInstrumentsList.isNotEmpty()) {
                                item(key = "__hidden_header__") {
                                    SectionHeader(
                                        title = "Hidden",
                                        count = hiddenInstrumentsList.size,
                                        icon = AppIcons.HideContent,
                                        expanded = hiddenExpanded,
                                        onToggle = { hiddenExpanded = !hiddenExpanded }
                                    )
                                }

                                if (hiddenExpanded) {
                                    items(hiddenInstrumentsList, key = { "hidden_${it.uniqueId}" }) { instrument ->
                                        SwipeToHideItem(
                                            direction = SwipeToDismissBoxValue.StartToEnd,
                                            action = SwipeAction(
                                                icon = AppIcons.ShowContent,
                                                label = "Show",
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            onDismiss = {
                                                showToast(platformContext, "Instrument shown")
                                                onToggleHide(instrument.uniqueId)
                                            }
                                        ) {
                                            InstrumentCard(
                                                instrument = instrument,
                                                isPinned = false,
                                                isHidden = true,
                                                onTogglePin = {},
                                                onClick = { onInstrumentClick(instrument.uniqueId) }
                                            )
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
                                    }
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

@Composable
private fun InstrumentCard(
    instrument: Instrument,
    isPinned: Boolean = false,
    isHidden: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = instrument.instrumentName ?: instrument.uniqueId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (!instrument.location.isNullOrBlank()) {
            {
                Text(instrument.location, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else null,
        leadingContent = {
            AppIcon(if (isHidden) AppIcons.HideContent else AppIcons.Instrument,
                tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                if (!isHidden) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                        AppIcon(AppIcons.Pinned, filled = isPinned,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
