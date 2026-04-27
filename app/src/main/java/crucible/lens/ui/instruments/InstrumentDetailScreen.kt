package crucible.lens.ui.instruments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AnimatedPullToRefreshIndicator

import crucible.lens.ui.common.ScrollToTopButton
import kotlinx.coroutines.launch

private enum class DatasetSortOrder { NEWEST, OLDEST, NAME }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InstrumentDetailScreen(
    instrumentId: String,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit = {},
    onDatasetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var datasets by remember { mutableStateOf<List<Dataset>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(DatasetSortOrder.NEWEST) }

    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var fromCache by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mainScrollState = rememberScrollState()
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { mainScrollState.value > 0 } }

    val contentTranslation by animateFloatAsState(
        targetValue = if (pullRefreshState.isRefreshing) 0f else pullRefreshState.verticalOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ptr_translation"
    )

    val filteredDatasets = remember(datasets, searchQuery, sortOrder) {
        val list = datasets ?: emptyList()
        val filtered = if (searchQuery.isBlank()) list else list.filter { it.matchesSearch(searchQuery) }
        when (sortOrder) {
            DatasetSortOrder.NEWEST -> filtered.sortedByDescending { it.timestamp }
            DatasetSortOrder.OLDEST -> filtered.sortedBy { it.timestamp }
            DatasetSortOrder.NAME   -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            error = null
            try {
                // Resolve instrument
                val resolvedInstrument = if (!forceRefresh) {
                    CacheManager.getInstruments()?.find { it.uniqueId == instrumentId }
                        ?: run {
                            val r = ApiClient.service.getInstrument(instrumentId)
                            r.body()
                        }
                } else {
                    val r = ApiClient.service.getInstrument(instrumentId)
                    r.body()
                }

                if (resolvedInstrument == null) {
                    error = "Instrument not found"
                    return@launch
                }
                instrument = resolvedInstrument

                val instrName = resolvedInstrument.instrumentName ?: resolvedInstrument.uniqueId

                // Resolve datasets
                if (!forceRefresh) {
                    val cached = CacheManager.getInstrumentDatasets(instrName)
                    if (cached != null) {
                        datasets = cached
                        fromCache = true
                        isLoading = false
                        pullRefreshState.endRefresh()
                        return@launch
                    }
                }

                fromCache = false
                val response = ApiClient.service.getDatasetsByInstrument(instrName)
                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    CacheManager.cacheInstrumentDatasets(instrName, body)
                    datasets = body
                } else {
                    error = "Failed to load datasets"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
                pullRefreshState.endRefresh()
            }
        }
    }

    LaunchedEffect(instrumentId) { loadData() }

    if (pullRefreshState.isRefreshing && !isLoading) {
        LaunchedEffect(Unit) {
            instrument?.instrumentName?.let { CacheManager.clearInstrumentsCache() }
            loadData(forceRefresh = true)
        }
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instrument") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                        Box {
                            IconButton(
                                onClick = { overflowMenuExpanded = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                instrument?.let { instr ->
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            val text = "${instr.instrumentName ?: instr.uniqueId}\nID: ${instr.uniqueId}"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Instrument"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh") },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            CacheManager.clearInstrumentsCache()
                                            loadData(forceRefresh = true)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(mainScrollState)
                    .graphicsLayer { translationY = contentTranslation }
            ) {
                // Header
                instrument?.let { instr ->
                    InstrumentHeader(instrument = instr)
                }

                // Search bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Filter datasets…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).clickable { searchQuery = "" })
                        }
                    }
                }

                // Sort chips
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DatasetSortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = sortOrder == order,
                            onClick = { sortOrder = order },
                            label = {
                                Text(when (order) {
                                    DatasetSortOrder.NEWEST -> "Newest"
                                    DatasetSortOrder.OLDEST -> "Oldest"
                                    DatasetSortOrder.NAME   -> "Name A→Z"
                                }, style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = if (sortOrder == order) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                // Body
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    error != null -> {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                                }
                                Text(error ?: "Unknown error", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    filteredDatasets.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
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
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredDatasets.forEach { dataset ->
                                DatasetCard(
                                    dataset = dataset,
                                    onClick = { onDatasetClick(dataset.uniqueId) }
                                )
                            }
                            if (fromCache) {
                                Text(
                                    text = "Loaded from cache",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            AnimatedPullToRefreshIndicator(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                visible = pullRefreshState.isRefreshing || pullRefreshState.verticalOffset > 0f
            )
            ScrollToTopButton(
                visible = showScrollToTop,
                onClick = { scope.launch { mainScrollState.animateScrollTo(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }

}

@Composable
private fun InstrumentHeader(instrument: Instrument) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Biotech, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val nameScrollState = rememberScrollState()
                    val showFade = nameScrollState.canScrollForward
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                if (showFade) drawRect(
                                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width * 0.75f, endX = size.width),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        Text(
                            text = instrument.instrumentName ?: instrument.uniqueId,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.horizontalScroll(nameScrollState)
                        )
                    }
                }
            }
            val type = instrument.instrumentType?.takeIf { it.isNotBlank() }
            val location = instrument.location?.takeIf { it.isNotBlank() }
            if (type != null || location != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (type != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (location != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatasetCard(
    dataset: Dataset,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Dataset, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = dataset.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    val subtitle = listOfNotNull(dataset.measurement, dataset.sessionName).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(text = dataset.uniqueId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("MFID", dataset.uniqueId))
                    Toast.makeText(context, "ID copied", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

private fun Dataset.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (measurement?.lowercase()?.contains(q) == true) ||
        (sessionName?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q)
}
