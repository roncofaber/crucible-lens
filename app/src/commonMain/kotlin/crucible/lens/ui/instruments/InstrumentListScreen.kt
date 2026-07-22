@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.instruments

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
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
import crucible.lens.platform.showToast
import kotlinx.coroutines.launch

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
    val viewModel: InstrumentListViewModel = viewModel()
    val loadState by viewModel.loadState.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hiddenExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortState by remember { mutableStateOf(SortState(SortField.NAME, true)) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val instruments = (loadState as? LoadState.Success)?.data ?: emptyList()
    val filteredInstruments = remember(instruments, searchQuery) {
        val list = instruments
        if (searchQuery.isBlank()) list
        else list.filter { it.matchesSearch(searchQuery) }
    }

    val activeInstruments = remember(filteredInstruments, hiddenInstruments, sortState) {
        filteredInstruments
            .filter { it.uniqueId !in hiddenInstruments }
            .applySortState(
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
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            items(activeInstruments, key = { it.uniqueId }) { instrument ->
                                @Suppress("DEPRECATION")

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            onToggleHide(instrument.uniqueId)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "${instrument.instrumentName ?: instrument.uniqueId} hidden",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    onToggleHide(instrument.uniqueId)
                                                }
                                            }
                                            true
                                        } else false
                                    },
                                    positionalThreshold = { it * 0.5f }
                                )
                                val hideIconScale by animateFloatAsState(
                                    targetValue = 0.75f + 0.5f * dismissState.progress,
                                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                                    label = "hideIconScale"
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = true,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)),
                                    backgroundContent = {
                                        val color = MaterialTheme.colorScheme.secondaryContainer
                                        val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(color.copy(alpha = 0.4f + 0.6f * dismissState.progress), MaterialTheme.shapes.medium)
                                                .padding(end = 20.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.scale(hideIconScale)
                                            ) {
                                                AppIcon(AppIcons.HideContent, tint = contentColor, modifier = Modifier.size(24.dp))
                                                Text("Hide", style = MaterialTheme.typography.labelSmall, color = contentColor)
                                            }
                                        }
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
                            }

                            // Hidden instruments section
                            if (hiddenInstrumentsList.isNotEmpty()) {
                                item(key = "__hidden_header__") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { hiddenExpanded = !hiddenExpanded }
                                            .padding(vertical = 4.dp, horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            AppIcon(AppIcons.HideContent, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            Text("Hidden (${hiddenInstrumentsList.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        AppIcon(if (hiddenExpanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (hiddenExpanded) {
                                    items(hiddenInstrumentsList, key = { "hidden_${it.uniqueId}" }) { instrument ->
                                        @Suppress("DEPRECATION")

                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                    showToast(platformContext, "Instrument shown")
                                                    onToggleHide(instrument.uniqueId)
                                                    true
                                                } else false
                                            },
                                            positionalThreshold = { it * 0.65f }
                                        )
                                        val showIconScale by animateFloatAsState(
                                            targetValue = 0.75f + 0.5f * dismissState.progress,
                                            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                                            label = "showIconScale"
                                        )
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            enableDismissFromStartToEnd = true,
                                            enableDismissFromEndToStart = false,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp)
                                                .animateItem(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)),
                                            backgroundContent = {
                                                val color = MaterialTheme.colorScheme.primary
                                                val contentColor = MaterialTheme.colorScheme.onPrimary
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(color.copy(alpha = 0.4f + 0.6f * dismissState.progress), MaterialTheme.shapes.medium)
                                                        .padding(start = 20.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.scale(showIconScale)
                                                    ) {
                                                        AppIcon(AppIcons.ShowContent, tint = contentColor, modifier = Modifier.size(24.dp))
                                                        Text("Show", style = MaterialTheme.typography.labelSmall, color = contentColor)
                                                    }
                                                }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHidden) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(if (isHidden) AppIcons.HideContent else AppIcons.Instrument,
                tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = instrument.instrumentName ?: instrument.uniqueId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(instrument.instrumentType, instrument.manufacturer).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!instrument.location.isNullOrBlank()) {
                    Text(text = instrument.location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!isHidden) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        AppIcon(AppIcons.Pinned, filled = isPinned,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
