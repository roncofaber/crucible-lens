package crucible.lens.ui.projects

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingMessage
import crucible.lens.ui.common.UiConstants
import crucible.lens.ui.common.openUrlInBrowser
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit = {},
    onResourceClick: (String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val project = remember(projectId) {
        CacheManager.getProjects()?.find { it.projectId == projectId }
    }

    var samples by remember { mutableStateOf<List<Sample>?>(null) }
    var datasets by remember { mutableStateOf<List<Dataset>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var fromCache by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val samplesListState = rememberLazyListState()
    val datasetsListState = rememberLazyListState()
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

                if (!forceRefresh) {
                    val cachedSamples = CacheManager.getProjectSamples(projectId)
                    val cachedDatasets = CacheManager.getProjectDatasets(projectId)

                    if (cachedSamples != null && cachedDatasets != null) {
                        samples = cachedSamples
                        datasets = cachedDatasets
                        fromCache = true
                        isLoading = false
                        pullRefreshState.endRefresh()
                        return@launch
                    }
                }
                fromCache = false

                val (samplesResponse, datasetsResponse) = coroutineScope {
                    val s = async { ApiClient.service.getSamplesByProject(projectId) }
                    val d = async { ApiClient.service.getDatasetsByProject(projectId, includeMetadata = false) }
                    s.await() to d.await()
                }

                if (samplesResponse.isSuccessful && datasetsResponse.isSuccessful) {
                    val loadedSamples = samplesResponse.body()
                    val loadedDatasets = datasetsResponse.body()

                    if (loadedSamples != null && loadedDatasets != null) {
                        CacheManager.cacheProjectSamples(projectId, loadedSamples)
                        CacheManager.cacheProjectDatasets(projectId, loadedDatasets)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(
                            onClick = {
                                CacheManager.clearProjectDetail(projectId)
                                loadProjectData(forceRefresh = true)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(24.dp))
                        }
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId"
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Check out this project in Crucible: $url")
                                    putExtra(Intent.EXTRA_SUBJECT, "Crucible Project: ${project?.projectName ?: projectId}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(24.dp))
                        }
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId"
                                openUrlInBrowser(context, url)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = "Open in browser", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val currentListState = if (pagerState.currentPage == 0) samplesListState else datasetsListState
            val showScrollToTop by remember {
                derivedStateOf { currentListState.firstVisibleItemIndex > 0 }
            }
            if (showScrollToTop && !isLoading) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            currentListState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(UiConstants.ScrollToTopFabSize),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.offset(y = (-2).dp)
                    ) {
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).offset(y = 5.dp)
                        )
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = "Scroll to top",
                            modifier = Modifier.size(20.dp).offset(y = (-5).dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            Column(
                modifier = modifier.fillMaxSize()
            ) {
            // Project header with integrated search
            ProjectHeader(
                project = project,
                projectId = projectId,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                fromCache = fromCache,
                isPinned = isPinned,
                onTogglePin = onTogglePin
            )

            // Tabs
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(0)
                            samplesListState.animateScrollToItem(0)
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
                            pagerState.animateScrollToPage(1)
                            datasetsListState.animateScrollToItem(0)
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

            when {
                isLoading -> {
                    val loadingMessage = LoadingMessage()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Loading Project Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedContent(
                                        targetState = loadingMessage,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(500)) togetherWith
                                                fadeOut(animationSpec = tween(500))
                                        },
                                        label = "loading message"
                                    ) { message ->
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
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
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> SamplesList(
                                samples = filteredSamples,
                                isFiltered = searchQuery.isNotBlank(),
                                listState = samplesListState,
                                onSampleClick = onResourceClick
                            )
                            1 -> DatasetsList(
                                datasets = filteredDatasets,
                                isFiltered = searchQuery.isNotBlank(),
                                listState = datasetsListState,
                                onDatasetClick = onResourceClick
                            )
                        }
                    }
                }
            }
            }

            // Only show pull-to-refresh indicator when not showing full loading screen
            if ((pullRefreshState.isRefreshing || pullRefreshState.verticalOffset > 0f) && !isLoading) {
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
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
    fromCache: Boolean = false,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name row: folder icon + text (weight 1f) + pin button top-right
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
                        Text(
                            text = project?.projectName ?: projectId,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        project?.projectLeadEmail?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = "Lead: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    val ageMin = if (fromCache) CacheManager.getProjectDataAgeMinutes(projectId) ?: 0 else 0
                    if (fromCache) {
                        Text(
                            text = "Cached ${ageMin}m ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            // Description
            val description = project?.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Creation date chip
            project?.createdAt?.let { date ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(
                        icon = Icons.Default.CalendarToday,
                        label = date.take(10)
                    )
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

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SamplesList(
    samples: List<Sample>,
    isFiltered: Boolean,
    listState: LazyListState = rememberLazyListState(),
    onSampleClick: (String) -> Unit
) {
    if (samples.isEmpty()) {
        // Wrap in scrollable Box so pull-to-refresh works even with empty state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            if (isFiltered) Icons.Default.SearchOff else Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isFiltered) "No Matching Samples" else "No Samples",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isFiltered) "No samples match your search." else "This project has no samples.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        val groupedSamples = samples.groupBy { it.sampleType ?: "Unspecified Type" }
            .entries.sortedBy { it.key.lowercase() }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedSamples.forEach { (sampleType, samplesInGroup) ->
                    item(key = "sample_group_$sampleType") {
                        CollapsibleGroup(
                            title = sampleType,
                            count = samplesInGroup.size,
                            icon = Icons.Default.Science
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                samplesInGroup.sortedBy { it.internalId ?: Int.MAX_VALUE }.forEach { sample ->
                                    ResourceCard(
                                        title = sample.name,
                                        subtitle = null,
                                        uniqueId = sample.uniqueId,
                                        icon = Icons.Default.Science,
                                        onClick = { onSampleClick(sample.uniqueId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            LazyColumnScrollbar(
                listState = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .padding(end = 0.dp)
            )
        }
    }
}

@Composable
private fun DatasetsList(
    datasets: List<Dataset>,
    isFiltered: Boolean,
    listState: LazyListState = rememberLazyListState(),
    onDatasetClick: (String) -> Unit
) {
    if (datasets.isEmpty()) {
        // Wrap in scrollable Box so pull-to-refresh works even with empty state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            if (isFiltered) Icons.Default.SearchOff else Icons.Default.Dataset,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isFiltered) "No Matching Datasets" else "No Datasets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isFiltered) "No datasets match your search." else "This project has no datasets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        val groupedDatasets = datasets.groupBy { it.measurement ?: "Unspecified Measurement" }
            .entries.sortedBy { it.key.lowercase() }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedDatasets.forEach { (measurement, datasetsInGroup) ->
                    item(key = "dataset_group_$measurement") {
                        CollapsibleGroup(
                            title = measurement,
                            count = datasetsInGroup.size,
                            icon = Icons.Default.Dataset
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                datasetsInGroup.sortedBy { it.internalId ?: Int.MAX_VALUE }.forEach { dataset ->
                                    ResourceCard(
                                        title = dataset.name,
                                        subtitle = null,
                                        uniqueId = dataset.uniqueId,
                                        icon = Icons.Default.Dataset,
                                        onClick = { onDatasetClick(dataset.uniqueId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            LazyColumnScrollbar(
                listState = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .padding(end = 0.dp)
            )
        }
    }
}

@Composable
private fun CollapsibleGroup(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(key = title) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$count item${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    title: String,
    subtitle: String?,
    uniqueId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
    }
}

private fun Sample.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (sampleType?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true) ||
        (internalId?.toString()?.contains(q) == true)
}

private fun Dataset.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (measurement?.lowercase()?.contains(q) == true) ||
        (instrumentName?.lowercase()?.contains(q) == true) ||
        (instrumentId?.toString()?.contains(q) == true) ||
        (sessionName?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true) ||
        (internalId?.toString()?.contains(q) == true) ||
        (dataFormat?.lowercase()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (sourceFolder?.lowercase()?.contains(q) == true) ||
        (fileToUpload?.lowercase()?.contains(q) == true) ||
        (jsonLink?.lowercase()?.contains(q) == true) ||
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

