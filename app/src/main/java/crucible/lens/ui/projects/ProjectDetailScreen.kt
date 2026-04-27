package crucible.lens.ui.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AnimatedPullToRefreshIndicator

import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.openUrlInBrowser
import androidx.compose.ui.graphics.SolidColor
import crucible.lens.data.preferences.PreferencesManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class SampleGroupBy(val label: String) {
    TYPE("Type"), DATE("Date"), OWNER("Owner")
}

private enum class DatasetGroupBy(val label: String) {
    MEASUREMENT("Measurement"), INSTRUMENT("Instrument"),
    DATE("Date"), FORMAT("Format"), SESSION("Session"), OWNER("Owner")
}

private fun ownerDisplayName(firstName: String?, lastName: String?) =
    "${firstName?.firstOrNull()?.uppercaseChar() ?: '?'}. ${lastName ?: "Unknown"}"

@Composable
private fun rememberOwnerNames(
    isOwnerGroupBy: Boolean,
    projectId: String
): Pair<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, Boolean> {
    val ownerNames = remember { mutableStateMapOf<String, String>() }
    var ownerNamesReady by remember(isOwnerGroupBy) {
        mutableStateOf(!isOwnerGroupBy || ownerNames.isNotEmpty())
    }
    LaunchedEffect(isOwnerGroupBy, projectId) {
        if (!isOwnerGroupBy || ownerNames.isNotEmpty()) { ownerNamesReady = true; return@LaunchedEffect }
        try {
            val resp = ApiClient.service.getProjectUsers(projectId)
            if (resp.isSuccessful) {
                resp.body()?.forEach { u -> u.uniqueId?.let { id -> ownerNames[id] = ownerDisplayName(u.firstName, u.lastName) } }
            }
        } catch (_: Exception) { }
        ownerNamesReady = true
    }
    return ownerNames to ownerNamesReady
}

@Composable
private fun EmptyListCard(
    resourceName: String,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isFiltered: Boolean
) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (isFiltered) Icons.Default.SearchOff else defaultIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isFiltered) "No Matching $resourceName" else "No $resourceName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isFiltered) "No ${resourceName.lowercase()} match your search."
                           else "This project has no ${resourceName.lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.LoadMoreItem(
    keyPrefix: String,
    groupKey: String,
    displayed: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    if (displayed < total) {
        val remaining = total - displayed
        item(key = "load_more_${keyPrefix}_$groupKey") {
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    "Load ${minOf(50, remaining)} more… ($remaining remaining)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun dateGroupKey(raw: String?): String {
    if (raw == null) return "No date"
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
    return try { fmt.format(java.time.OffsetDateTime.parse(raw.trim())) }
    catch (_: Exception) { try { fmt.format(java.time.LocalDateTime.parse(raw.trim())) }
    catch (_: Exception) { "No date" } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit = {},
    onResourceClick: (uuid: String, groupBy: String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    isArchived: Boolean = false,
    onCreateSample: () -> Unit = {},
    onCreateDataset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val project = remember(projectId) {
        CacheManager.getProjects()?.find { it.projectId == projectId }
    }

    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    var samples by remember { mutableStateOf<List<Sample>?>(null) }
    var datasets by remember { mutableStateOf<List<Dataset>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sampleGroupBy by remember { mutableStateOf(SampleGroupBy.TYPE) }
    var datasetGroupBy by remember { mutableStateOf(DatasetGroupBy.MEASUREMENT) }
    var fromCache by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Load persisted group-by choices and default tab on first composition
    LaunchedEffect(Unit) {
        sampleGroupBy = SampleGroupBy.valueOf(prefs.sampleGroupBy.first())
        datasetGroupBy = DatasetGroupBy.valueOf(prefs.datasetGroupBy.first())
        val tab = prefs.defaultProjectTab.first()
        if (tab == crucible.lens.data.preferences.PreferencesManager.PROJECT_TAB_DATASETS) {
            pagerState.scrollToPage(1)
        }
    }
    val mainScrollState = rememberScrollState()
    val pullRefreshState = rememberPullToRefreshState()

    val filteredSamples = remember(samples, searchQuery) {
        if (searchQuery.isBlank()) samples ?: emptyList()
        else (samples ?: emptyList()).filter { it.matchesSearch(searchQuery) }
    }
    val filteredDatasets = remember(datasets, searchQuery) {
        if (searchQuery.isBlank()) datasets ?: emptyList()
        else (datasets ?: emptyList()).filter { it.matchesSearch(searchQuery) }
    }

    fun loadProjectData(forceRefresh: Boolean = false) {
        scope.launch {
            try {
                isLoading = true
                error = null

                // Always try cache first
                val cachedSamples = CacheManager.getProjectSamples(projectId)
                val cachedDatasets = CacheManager.getProjectDatasets(projectId)

                if (cachedSamples != null && cachedDatasets != null && !forceRefresh) {
                    samples = cachedSamples
                    datasets = cachedDatasets
                    fromCache = true
                    isLoading = false
                    pullRefreshState.endRefresh()
                    return@launch
                }

                // Skip API calls for archived projects (use cache only)
                if (isArchived) {
                    // Use cache if available, otherwise show empty
                    samples = cachedSamples ?: emptyList()
                    datasets = cachedDatasets ?: emptyList()
                    fromCache = cachedSamples != null && cachedDatasets != null
                    isLoading = false
                    pullRefreshState.endRefresh()
                    return@launch
                }

                // Only make API calls for non-archived projects
                fromCache = false

                val (samplesResponse, datasetsResponse) = coroutineScope {
                    val s = async { ApiClient.service.getSamplesByProject(projectId) }
                    val d = async { ApiClient.service.getDatasetsByProject(projectId) }
                    s.await() to d.await()
                }

                if (samplesResponse.isSuccessful && datasetsResponse.isSuccessful) {
                    val loadedSamples = samplesResponse.body()
                    val loadedDatasets = datasetsResponse.body()

                    if (loadedSamples != null && loadedDatasets != null) {
                        CacheManager.cacheProjectSamples(projectId, loadedSamples)
                        CacheManager.cacheProjectDatasets(projectId, loadedDatasets)
                        // Populate resource cache with metadata-rich versions for each dataset,
                        // but only if a richer individually-fetched version isn't already there.
                        loadedDatasets.forEach { dataset ->
                            if (CacheManager.getResource(dataset.uniqueId) == null) {
                                CacheManager.cacheResource(dataset.uniqueId, dataset)
                            }
                        }
                        samples = loadedSamples
                        datasets = loadedDatasets
                    } else {
                        error = "Failed to load project data"
                    }
                } else {
                    error = "Failed to load project data"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
                pullRefreshState.endRefresh()
            }
        }
    }

    LaunchedEffect(projectId) {
        loadProjectData()
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    val topBarContext = LocalContext.current
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                        var topBarMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { topBarMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(expanded = topBarMenuExpanded, onDismissRequest = { topBarMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("New Sample") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = { topBarMenuExpanded = false; onCreateSample() }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Dataset") },
                                    leadingIcon = { Icon(Icons.Default.Dataset, contentDescription = null) },
                                    onClick = { topBarMenuExpanded = false; onCreateDataset() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Open in web") },
                                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        openUrlInBrowser(topBarContext, "$graphExplorerUrl/$projectId")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "$graphExplorerUrl/$projectId")
                                            putExtra(Intent.EXTRA_SUBJECT, project?.title ?: projectId)
                                            type = "text/plain"
                                        }
                                        topBarContext.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Animate back to 0 when refresh starts so the header springs back up
        val contentTranslation by animateFloatAsState(
            targetValue = if (pullRefreshState.isRefreshing) 0f else pullRefreshState.verticalOffset,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "ptr_translation"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(mainScrollState)
                    .graphicsLayer { translationY = contentTranslation }
            ) {
            // Project header with integrated search
            ProjectHeader(
                project = project,
                projectId = projectId,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                isPinned = isPinned,
                onTogglePin = onTogglePin,

            )

            // Tabs + group-by overlay
            var groupMenuExpanded by remember { mutableStateOf(false) }
            Box {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 0,
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    },
                    text = {
                        val count = filteredSamples.size
                        val total = samples?.size
                        val label = when {
                            total == null -> "Samples (--)"
                            searchQuery.isBlank() -> "Samples ($total)"
                            else -> "Samples ($count/$total)"
                        }
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 200))
                            },
                            label = "samples_tab_label"
                        ) { text ->
                            Text(text)
                        }
                    },
                    icon = { Icon(Icons.Default.Science, contentDescription = null) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = 1,
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                    },
                    text = {
                        val count = filteredDatasets.size
                        val total = datasets?.size
                        val label = when {
                            total == null -> "Datasets (--)"
                            searchQuery.isBlank() -> "Datasets ($total)"
                            else -> "Datasets ($count/$total)"
                        }
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 200))
                            },
                            label = "datasets_tab_label"
                        ) { text ->
                            Text(text)
                        }
                    },
                    icon = { Icon(Icons.Default.Dataset, contentDescription = null) }
                )
            }
                // Group-by button overlaid at end of tab row
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    val label = if (pagerState.currentPage == 0) sampleGroupBy.label else datasetGroupBy.label
                    IconButton(
                        onClick = { groupMenuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Group by $label", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                        if (pagerState.currentPage == 0) {
                            SampleGroupBy.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    leadingIcon = if (opt == sampleGroupBy) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                    onClick = { sampleGroupBy = opt; groupMenuExpanded = false; scope.launch { prefs.saveSampleGroupBy(opt.name) } }
                                )
                            }
                        } else {
                            DatasetGroupBy.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    leadingIcon = if (opt == datasetGroupBy) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                    onClick = { datasetGroupBy = opt; groupMenuExpanded = false; scope.launch { prefs.saveDatasetGroupBy(opt.name) } }
                                )
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> {
                    LoadingContent(
                        title = "Loading Project Data",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Error Loading Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    }
                }
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> SamplesList(
                                samples = filteredSamples,
                                isFiltered = searchQuery.isNotBlank(),
                                fromCache = fromCache,
                                projectId = projectId,
                                graphExplorerUrl = graphExplorerUrl,
                                groupBy = sampleGroupBy,
                                onSampleClick = { uuid -> onResourceClick(uuid, sampleGroupBy.name) }
                            )
                            1 -> DatasetsList(
                                datasets = filteredDatasets,
                                isFiltered = searchQuery.isNotBlank(),
                                fromCache = fromCache,
                                projectId = projectId,
                                graphExplorerUrl = graphExplorerUrl,
                                groupBy = datasetGroupBy,
                                onDatasetClick = { uuid -> onResourceClick(uuid, datasetGroupBy.name) }
                            )
                        }
                    }
                }
            }
            }

            // Pull-to-refresh indicator with spring animation
            // Only show when not showing full loading screen
            AnimatedPullToRefreshIndicator(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                visible = (pullRefreshState.isRefreshing || pullRefreshState.verticalOffset > 0f) && !isLoading
            )

        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            pullRefreshState.startRefresh()
        } else {
            pullRefreshState.endRefresh()
        }
    }

    if (pullRefreshState.isRefreshing && !isLoading) {
        LaunchedEffect(Unit) {
            CacheManager.clearProjectDetail(projectId)
            loadProjectData(forceRefresh = true)
        }
    }

}

@Composable
private fun ProjectHeader(
    project: Project?,
    projectId: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name row: folder + [name + pin] (weight 1f) | [copy, open, share, QR]
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val nameScrollState = rememberScrollState()
                                val showRightFade = nameScrollState.canScrollForward
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                        .drawWithContent {
                                            drawContent()
                                            if (showRightFade) {
                                                drawRect(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(Color.Black, Color.Transparent),
                                                        startX = size.width * 0.75f,
                                                        endX = size.width
                                                    ),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                        }
                                ) {
                                    Text(
                                        text = project?.title ?: projectId,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.horizontalScroll(nameScrollState)
                                    )
                                }
                                IconButton(
                                    onClick = onTogglePin,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (isPinned) "Unpin" else "Pin",
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            val lead = project?.lead
                            val org = project?.organization?.takeIf { it.isNotBlank() }
                            if (lead != null || org != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (lead != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(ownerDisplayName(lead.firstName, lead.lastName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (org != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(org, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            // Integrated search field
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search samples and datasets…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSearchChange("") }
                        )
                    }
                }
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SamplesList(
    samples: List<Sample>,
    isFiltered: Boolean,
    fromCache: Boolean = false,
    projectId: String = "",
    graphExplorerUrl: String = "",
    groupBy: SampleGroupBy = SampleGroupBy.TYPE,
    onSampleClick: (String) -> Unit
) {
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == SampleGroupBy.OWNER, projectId)
    if (samples.isEmpty()) {
        EmptyListCard(resourceName = "Samples", defaultIcon = Icons.Default.Science, isFiltered = isFiltered)
    } else if (!ownerNamesReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    } else {
        val groupedSamples = remember(samples, groupBy, ownerNames.toMap()) {
            samples.groupBy { s -> when (groupBy) {
                SampleGroupBy.TYPE  -> s.sampleType ?: "No type"
                SampleGroupBy.DATE  -> dateGroupKey(s.timestamp)
                SampleGroupBy.OWNER -> s.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
            } }.entries.sortedBy { it.key.lowercase() }
        }
        // Reset expanded state whenever the grouping changes
        val expandedGroups = remember(groupBy) { mutableStateMapOf<String, Boolean>() }
        val displayedCounts = remember(groupBy) { mutableStateMapOf<String, Int>() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedSamples.forEach { (groupKey, samplesInGroup) ->
                    val expanded = expandedGroups[groupKey] == true
                    val sortedSamples = samplesInGroup.sortedBy { it.uniqueId }
                    val displayedCount = displayedCounts[groupKey] ?: 50

                    stickyHeader(key = "header_sample_$groupKey") {
                        GroupStickyHeader(
                            title = groupKey,
                            count = samplesInGroup.size,
                            icon = Icons.Default.Science,
                            expanded = expanded,
                            onToggle = { expandedGroups[groupKey] = !expanded }
                        )
                    }

                    if (expanded) {
                        items(sortedSamples.take(displayedCount), key = { it.uniqueId }) { sample ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                ResourceCard(
                                    title = sample.name,
                                    subtitle = null,
                                    uniqueId = sample.uniqueId,
                                    icon = Icons.Default.Science,
                                    graphExplorerUrl = graphExplorerUrl,
                                    projectId = projectId,
                                    resourceType = "sample",
                                    onClick = { onSampleClick(sample.uniqueId) }
                                )
                            }
                        }
                        LoadMoreItem("sample", groupKey, displayedCount, sortedSamples.size) {
                            displayedCounts[groupKey] = displayedCount + 50
                        }
                    }
                }
                if (fromCache) {
                    val ageMin = CacheManager.getProjectDataAgeMinutes(projectId) ?: 0
                    item(key = "cache_age") {
                        Text(
                            text = "Cached ${ageMin}m ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DatasetsList(
    datasets: List<Dataset>,
    isFiltered: Boolean,
    fromCache: Boolean = false,
    projectId: String = "",
    graphExplorerUrl: String = "",
    groupBy: DatasetGroupBy = DatasetGroupBy.MEASUREMENT,
    onDatasetClick: (String) -> Unit
) {
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == DatasetGroupBy.OWNER, projectId)
    if (datasets.isEmpty()) {
        EmptyListCard(resourceName = "Datasets", defaultIcon = Icons.Default.Dataset, isFiltered = isFiltered)
    } else if (!ownerNamesReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    } else {
        val groupedDatasets = remember(datasets, groupBy, ownerNames.toMap()) {
            datasets.groupBy { d -> when (groupBy) {
                DatasetGroupBy.MEASUREMENT -> d.measurement ?: "No measurement"
                DatasetGroupBy.INSTRUMENT  -> d.instrumentName ?: "No instrument"
                DatasetGroupBy.DATE        -> dateGroupKey(d.timestamp)
                DatasetGroupBy.FORMAT      -> d.dataFormat ?: "No format"
                DatasetGroupBy.SESSION     -> d.sessionName ?: "No session"
                DatasetGroupBy.OWNER       -> d.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
            } }.entries.sortedBy { it.key.lowercase() }
        }
        // Reset expanded/pagination state whenever grouping changes
        val expandedGroups = remember(groupBy) { mutableStateMapOf<String, Boolean>() }
        val displayedCounts = remember(groupBy) { mutableStateMapOf<String, Int>() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedDatasets.forEach { (groupKey, datasetsInGroup) ->
                    val expanded = expandedGroups[groupKey] == true
                    val sortedDatasets = datasetsInGroup.sortedBy { it.uniqueId }
                    val displayedCount = displayedCounts[groupKey] ?: 50

                    stickyHeader(key = "header_dataset_$groupKey") {
                        GroupStickyHeader(
                            title = groupKey,
                            count = datasetsInGroup.size,
                            icon = Icons.Default.Dataset,
                            expanded = expanded,
                            onToggle = { expandedGroups[groupKey] = !expanded }
                        )
                    }

                    if (expanded) {
                        items(sortedDatasets.take(displayedCount), key = { it.uniqueId }) { dataset ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                ResourceCard(
                                    title = dataset.name,
                                    subtitle = null,
                                    uniqueId = dataset.uniqueId,
                                    icon = Icons.Default.Dataset,
                                    graphExplorerUrl = graphExplorerUrl,
                                    projectId = projectId,
                                    resourceType = "dataset",
                                    onClick = { onDatasetClick(dataset.uniqueId) }
                                )
                            }
                        }
                        LoadMoreItem("dataset", groupKey, displayedCount, sortedDatasets.size) {
                            displayedCounts[groupKey] = displayedCount + 50
                        }
                    }
                }
                if (fromCache) {
                    val ageMin = CacheManager.getProjectDataAgeMinutes(projectId) ?: 0
                    item(key = "cache_age") {
                        Text(
                            text = "Cached ${ageMin}m ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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

@Composable
private fun GroupStickyHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ResourceCard(
    title: String,
    subtitle: String?,
    uniqueId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    graphExplorerUrl: String = "",
    projectId: String? = null,
    resourceType: String = "sample",
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val webUrl = if (projectId != null && graphExplorerUrl.isNotBlank()) {
        if (resourceType == "dataset") "$graphExplorerUrl/$projectId/dataset/$uniqueId"
        else "$graphExplorerUrl/$projectId/sample-graph/$uniqueId"
    } else null

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = uniqueId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        } // end Card

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Copy ID") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("MFID", uniqueId))
                    android.widget.Toast.makeText(context, "ID copied", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            if (webUrl != null) {
                DropdownMenuItem(
                    text = { Text("Open in web") },
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webUrl)))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        val intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, webUrl)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                    }
                )
            }
        }
    } // end Box
}

private fun Sample.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (sampleType?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true)
}

private fun Dataset.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (measurement?.lowercase()?.contains(q) == true) ||
        (instrumentName?.lowercase()?.contains(q) == true) ||
        (sessionName?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (timestamp?.lowercase()?.contains(q) == true) ||
        (dataFormat?.lowercase()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (sourceFolder?.lowercase()?.contains(q) == true) ||
        (fileToUpload?.lowercase()?.contains(q) == true) ||
        (sha256Hash?.lowercase()?.contains(q) == true) ||
        (scientificMetadata?.matchesSearch(q) == true)
}

private fun Map<String, Any?>.matchesSearch(query: String): Boolean =
    entries.any { (key, value) ->
        key.lowercase().contains(query) || value.matchesSearchValue(query)
    }

private fun Any?.matchesSearchValue(query: String): Boolean = when (this) {
    null -> false
    is String -> lowercase().contains(query)
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (this as Map<String, Any?>).matchesSearch(query)
    is List<*> -> any { it.matchesSearchValue(query) }
    else -> toString().lowercase().contains(query)
}

