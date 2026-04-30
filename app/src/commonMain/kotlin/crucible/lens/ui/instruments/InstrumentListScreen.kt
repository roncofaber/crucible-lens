package crucible.lens.ui.instruments

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Instrument
import crucible.lens.data.util.matchesSearch
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.SearchBar
import crucible.lens.ui.common.showFeedback
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    var instruments by remember { mutableStateOf<List<Instrument>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var hiddenExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val filteredInstruments = remember(instruments, searchQuery) {
        val list = instruments ?: emptyList()
        if (searchQuery.isBlank()) list
        else list.filter { it.matchesSearch(searchQuery) }
    }

    val activeInstruments = remember(filteredInstruments, hiddenInstruments) {
        filteredInstruments.filter { it.uniqueId !in hiddenInstruments }
    }

    val hiddenInstrumentsList = remember(instruments, hiddenInstruments) {
        instruments?.filter { it.uniqueId in hiddenInstruments } ?: emptyList()
    }

    fun loadInstruments(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            error = null
            try {
                if (!forceRefresh) {
                    val cached = CacheManager.getInstruments()
                    if (cached != null) {
                        instruments = cached
                        isLoading = false
                        return@launch
                    }
                }
                when (val response = ApiClient.service.getInstruments()) {
                    is crucible.lens.data.api.ApiResult.Success -> {
                        val body = response.data
                        CacheManager.cacheInstruments(body)
                        instruments = body
                    }
                    is crucible.lens.data.api.ApiResult.Error -> {
                        error = "Failed to load instruments"
                    }
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadInstruments() }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instruments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (hiddenExpanded) "Collapse hidden" else "Show hidden") },
                                    leadingIcon = {
                                        Icon(
                                            if (hiddenExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = { hiddenExpanded = !hiddenExpanded; menuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = { menuExpanded = false; loadInstruments(forceRefresh = true) }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { loadInstruments(forceRefresh = true) },
            state = pullRefreshState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search bar — stays fixed during pull-to-refresh
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search by name, type, manufacturer…"
                )

                Box(modifier = Modifier.weight(1f).offset {
                    IntOffset(0, (pullRefreshState.distanceFraction * 80.dp.toPx()).coerceAtMost(80.dp.toPx()).roundToInt())
                }) {
                    when {
                        isLoading -> LoadingContent(title = "Loading Instruments")
                        error != null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ErrorCard(
                                    title = "Error Loading Instruments",
                                    message = error ?: "Unknown error",
                                    modifier = Modifier.padding(16.dp),
                                    onRetry = { loadInstruments(forceRefresh = true) }
                                )
                            }
                        }
                        activeInstruments.isEmpty() && hiddenInstrumentsList.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Biotech,
                                                contentDescription = null,
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
                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Active instruments with swipe-to-hide
                                    items(activeInstruments, key = { it.uniqueId }) { instrument ->
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    showFeedback(platformContext, "Instrument hidden")
                                                    onToggleHide(instrument.uniqueId)
                                                    true
                                                } else false
                                            },
                                            positionalThreshold = { it * 0.65f }
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
                                            modifier = Modifier.animateItem(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)),
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
                                                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                                                        Text("Hide", style = MaterialTheme.typography.labelSmall, color = contentColor)
                                                    }
                                                }
                                            }
                                        ) {
                                            InstrumentCard(
                                                instrument = instrument,
                                                isPinned = instrument.uniqueId in pinnedInstruments,
                                                onTogglePin = {
                                                    showFeedback(platformContext, if (instrument.uniqueId in pinnedInstruments) "Instrument unpinned" else "Instrument pinned")
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
                                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                                    Text("Hidden (${hiddenInstrumentsList.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Icon(
                                                    if (hiddenExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (hiddenExpanded) {
                                            items(hiddenInstrumentsList, key = { "hidden_${it.uniqueId}" }) { instrument ->
                                                val dismissState = rememberSwipeToDismissBoxState(
                                                    confirmValueChange = { value ->
                                                        if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                            showFeedback(platformContext, "Instrument shown")
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
                                                    modifier = Modifier.animateItem(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)),
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
                                                                Icon(Icons.Default.Visibility, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
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
            Icon(
                if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Biotech,
                contentDescription = null,
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
                        Icon(
                            if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
