package crucible.lens.ui.instruments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Instrument
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.ScrollToTopButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentListScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onInstrumentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    pinnedInstruments: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {}
) {
    var instruments by remember { mutableStateOf<List<Instrument>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val filteredInstruments = remember(instruments, searchQuery) {
        val list = instruments ?: emptyList()
        if (searchQuery.isBlank()) list
        else list.filter { it.matchesSearch(searchQuery) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
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
                // Search bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search by name, type, manufacturer…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).clickable { searchQuery = "" })
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("Loading instruments…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                    }
                                    Text(error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    filteredInstruments.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Biotech, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (searchQuery.isNotBlank()) "No matching instruments" else "No instruments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Text(if (searchQuery.isNotBlank()) "No instruments match your search." else "No instruments found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                items(filteredInstruments, key = { it.uniqueId }) { instrument ->
                                    InstrumentCard(
                                        instrument = instrument,
                                        isPinned = instrument.uniqueId in pinnedInstruments,
                                        onTogglePin = { onTogglePin(instrument.uniqueId) },
                                        onClick = { onInstrumentClick(instrument.uniqueId) }
                                    )
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

@Composable
private fun InstrumentCard(
    instrument: Instrument,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Biotech, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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

private fun Instrument.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return (instrumentName?.lowercase()?.contains(q) == true) ||
        (instrumentType?.lowercase()?.contains(q) == true) ||
        (manufacturer?.lowercase()?.contains(q) == true) ||
        (model?.lowercase()?.contains(q) == true) ||
        (location?.lowercase()?.contains(q) == true) ||
        (owner?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q)
}
