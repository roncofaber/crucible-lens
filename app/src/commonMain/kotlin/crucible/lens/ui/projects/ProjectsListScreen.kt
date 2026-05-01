package crucible.lens.ui.projects
import crucible.lens.platform.*

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.SearchBar

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.cache.ProjectSummary
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ToggleHiddenMenuItem
import crucible.lens.ui.common.showFeedback
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.data.util.fetchProjectData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsListScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onProjectClick: (String) -> Unit,
    pinnedProjects: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
    hiddenProjects: Set<String> = emptySet(),
    onToggleHide: (String) -> Unit = {},
) {
    val platformContext = getPlatformContext()
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUserRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Map of projectId -> Pair(sampleCount, datasetCount), null means still loading
    var projectCounts by remember { mutableStateOf<Map<String, Pair<Int?, Int?>>>(emptyMap()) }
    // Persistent cache summaries - loaded immediately for instant display
    var persistentSummaries by remember { mutableStateOf<List<ProjectSummary>?>(null) }
    var hiddenExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(0) } // 0=Name A-Z, 1=Name Z-A
    // Track which projects were manually unarchived (so we don't auto-archive them again)
    var manuallyShown by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Trigger for forcing background reload - increments on refresh
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Load persistent cache immediately on startup for instant display
    LaunchedEffect(Unit) {
        persistentSummaries = PersistentProjectCache.loadProjectData(platformContext)
        // If we have persistent cache, populate counts immediately
        persistentSummaries?.let { summaries ->
            projectCounts = summaries.associate {
                it.projectId to Pair(it.sampleCount, it.datasetCount)
            }
        }
    }

    fun loadProjects(forceRefresh: Boolean = false) {
        if (forceRefresh) isUserRefreshing = true
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Check cache first if not forcing refresh
                if (!forceRefresh) {
                    val cachedProjects = CacheManager.getProjects()
                    if (cachedProjects != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            projects = cachedProjects
                            // Initialize all projects with null counts to show loading spinners
                            val newCounts = cachedProjects.associate { it.projectId to Pair<Int?, Int?>(null, null) }
                            projectCounts = projectCounts + newCounts
                            isLoading = false
                        }
                        return@launch
                    }
                } else {
                    // Clear cache and counts when force refreshing so fresh data is loaded
                    CacheManager.clearAll()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        projectCounts = emptyMap()
                        reloadTrigger++ // Trigger background reload
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = true
                    error = null
                }
                when (val response = ApiClient.service.getProjects()) {
                    is ApiResult.Success -> {
                        val fetchedProjects = response.data
                        // Cache the projects
                        CacheManager.cacheProjects(fetchedProjects)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            projects = fetchedProjects
                            // Initialize all projects with null counts to show loading spinners
                            val newCounts = fetchedProjects.associate { it.projectId to Pair<Int?, Int?>(null, null) }
                            projectCounts = projectCounts + newCounts
                        }
                    }
                    is ApiResult.Error -> {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            error = "Failed to load projects: ${response.message}"
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    error = "Error: ${e.message}"
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = false
                    isUserRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProjects()
    }

    // Function to save current state to persistent cache
    fun saveToPersistentCache() {
        val currentProjects = projects ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Build maps of samples and datasets
                val samplesMap = mutableMapOf<String, List<Sample>>()
                val datasetsMap = mutableMapOf<String, List<Dataset>>()

                currentProjects.forEach { project ->
                    CacheManager.getProjectSamples(project.projectId)?.let {
                        samplesMap[project.projectId] = it
                    }
                    CacheManager.getProjectDatasets(project.projectId)?.let {
                        datasetsMap[project.projectId] = it
                    }
                }

                PersistentProjectCache.saveProjectData(platformContext, currentProjects, samplesMap, datasetsMap)
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    // Preload and cache samples/datasets per project in background (also populates counts).
    // Priority: pinned projects first, archived projects last (stage 2 skipped for archived).
    // This automatically cancels when the user navigates away from this screen.
    // Re-triggers when projects change OR when reloadTrigger increments (force refresh).
    LaunchedEffect(projects, reloadTrigger) {
        val projectList = projects ?: return@LaunchedEffect
        val prioritizedProjects = projectList
            .sortedWith(compareByDescending<Project> { it.projectId in pinnedProjects }
                .thenBy { it.projectId in hiddenProjects })

        // Track consecutive failures to stop on network errors (thread-safe for concurrent launches)
        var consecutiveFailures = 0
        val maxConsecutiveFailures = 5

        // Process in small batches to avoid overwhelming the device
        prioritizedProjects.chunked(5).forEach { batch ->
            // Stop if we've had too many consecutive failures (likely network issue)
            if (consecutiveFailures >= maxConsecutiveFailures) {
                return@LaunchedEffect
            }
            batch.forEach { project ->
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    try {
                        fetchProjectData(
                            projectId = project.projectId,
                            onCountsAvailable = { sampleCount, datasetCount ->
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    projectCounts = projectCounts + (project.projectId to Pair(sampleCount, datasetCount))

                                    if (sampleCount == 0 && datasetCount == 0 &&
                                        project.projectId !in manuallyShown &&
                                        project.projectId !in hiddenProjects) {
                                        onToggleHide(project.projectId)
                                    }
                                }
                            }
                        )
                        consecutiveFailures = 0
                    } catch (_: Exception) {
                        consecutiveFailures++
                    }
                }
            }
            // Small delay between batches to avoid overwhelming the device
            kotlinx.coroutines.delay(150)
        }

        // After all batches complete, save to persistent cache
        saveToPersistentCache()
    }

    AppScaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        IconButton(
                            onClick = onSearch,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = onHome,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        var listMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { listMenuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                                ToggleHiddenMenuItem(hiddenExpanded) { hiddenExpanded = !hiddenExpanded; listMenuExpanded = false }
                                RefreshMenuItem { listMenuExpanded = false; loadProjects(forceRefresh = true) }
                            }
                        }
                    }
                }
            )
            } // end Column
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isUserRefreshing,
            onRefresh = { loadProjects(forceRefresh = true) },
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    stickyHeader(key = "search_bar") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    placeholder = "Search by name, ID, or project lead…",
                                    modifier = Modifier.weight(1f)
                                )
                                Box {
                                    IconButton(onClick = { sortMenuExpanded = true }) {
                                        Icon(
                                            Icons.Default.SwapVert,
                                            contentDescription = "Sort",
                                            tint = if (sortOrder != 0) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        listOf("Name A→Z", "Name Z→A").forEachIndexed { i, label ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                leadingIcon = {
                                                    if (sortOrder == i)
                                                        Icon(Icons.Default.Check, contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary)
                                                    else Spacer(Modifier.size(24.dp))
                                                },
                                                onClick = { sortOrder = i; sortMenuExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when {
                        isLoading && persistentSummaries == null -> item(key = "__loading__") {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.85f),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingContent(title = "Loading Projects")
                            }
                        }
                        error != null -> item(key = "__error__") {
                            Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                                ErrorCard(
                                    title = "Error Loading Projects",
                                    message = error ?: "Unknown error",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onRetry = { loadProjects(forceRefresh = true) }
                                )
                            }
                        }
                        projects?.isEmpty() == true -> item(key = "__empty__") {
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
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "No Projects Found",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "There are no projects available.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // Use real projects if available, otherwise convert persistent summaries
                            val allProjects = projects ?: persistentSummaries?.map { summary ->
                                Project(
                                    projectId = summary.projectId,
                                    title = summary.projectName
                                )
                            } ?: emptyList()

                            // Filter projects based on search query (includes project, samples, and datasets with metadata)
                            val filteredProjects = if (searchQuery.isBlank()) {
                                allProjects
                            } else {
                                allProjects.filter { project ->
                                    // Search in project properties
                                    val matchesProject = project.title?.contains(searchQuery, ignoreCase = true) == true ||
                                        project.projectId.contains(searchQuery, ignoreCase = true) ||
                                        project.organization?.contains(searchQuery, ignoreCase = true) == true ||
                                        project.lead?.email?.contains(searchQuery, ignoreCase = true) == true

                                    // Search in cached samples
                                    val matchesSamples = CacheManager.getProjectSamples(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true

                                    // Search in cached datasets (including metadata)
                                    val matchesDatasets = CacheManager.getProjectDatasets(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true

                                    matchesProject || matchesSamples || matchesDatasets
                                }
                            }

                            val activeProjects = filteredProjects
                                .filter { it.projectId !in hiddenProjects }
                                .let { list ->
                                    when (sortOrder) {
                                        1 -> list.sortedWith(compareByDescending<Project> { it.projectId in pinnedProjects }
                                            .thenByDescending { it.title?.lowercase() ?: it.projectId.lowercase() })
                                        else -> list.sortedWith(compareByDescending<Project> { it.projectId in pinnedProjects }
                                            .thenBy { it.title?.lowercase() ?: it.projectId.lowercase() })
                                    }
                                }
                            val hiddenProjectsList = filteredProjects
                                .filter { it.projectId in hiddenProjects }

                            // Show message when search returns no results
                            if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
                                item(key = "__no_search_results__") {
                                    Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(
                                                        Icons.Default.SearchOff,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "No Results Found",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = "No projects match \"$searchQuery\"",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(activeProjects, key = { it.projectId }) { project ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                showFeedback(platformContext, "Project hidden")
                                                onToggleHide(project.projectId)
                                                true
                                            } else false
                                        },
                                        positionalThreshold = { totalDistance -> totalDistance * 0.65f }
                                    )
                                    val iconScale by animateFloatAsState(
                                        targetValue = 0.75f + 0.5f * dismissState.progress,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "hideIconScale"
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        enableDismissFromEndToStart = true,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .animateItem(
                                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                            ),
                                        backgroundContent = {
                                            val color = MaterialTheme.colorScheme.secondaryContainer
                                            val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        color.copy(alpha = 0.4f + 0.6f * dismissState.progress),
                                                        MaterialTheme.shapes.medium
                                                    )
                                                    .padding(end = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.scale(iconScale)
                                                ) {
                                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                                                    Text("Hide", style = MaterialTheme.typography.labelSmall, color = contentColor)
                                                }
                                            }
                                        }
                                    ) {
                                        ProjectCard(
                                            project = project,
                                            counts = projectCounts[project.projectId],
                                            onClick = { onProjectClick(project.projectId) },
                                            isPinned = project.projectId in pinnedProjects,
                                            onTogglePin = {
                                                showFeedback(platformContext, if (project.projectId in pinnedProjects) "Project unpinned" else "Project pinned")
                                                onTogglePin(project.projectId)
                                            }
                                        )
                                    }
                                }

                                if (hiddenProjectsList.isNotEmpty()) {
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
                                                Icon(
                                                    Icons.Default.VisibilityOff,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    "Hidden (${hiddenProjectsList.size})",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                if (hiddenExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (hiddenExpanded) {
                                        items(hiddenProjectsList, key = { "hidden_${it.projectId}" }) { project ->
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { value ->
                                                    if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                        manuallyShown = manuallyShown + project.projectId
                                                        showFeedback(platformContext, "Project shown")
                                                        onToggleHide(project.projectId)
                                                        true
                                                    } else false
                                                },
                                                positionalThreshold = { totalDistance -> totalDistance * 0.65f }
                                            )
                                            val showIconScale by animateFloatAsState(
                                                targetValue = 0.75f + 0.5f * dismissState.progress,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                label = "showIconScale"
                                            )
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                enableDismissFromStartToEnd = true,
                                                enableDismissFromEndToStart = false,
                                                modifier = Modifier
                                                    .padding(horizontal = 16.dp)
                                                    .animateItem(
                                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                                                    ),
                                                backgroundContent = {
                                                    val color = MaterialTheme.colorScheme.primary
                                                    val contentColor = MaterialTheme.colorScheme.onPrimary
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(
                                                                color.copy(alpha = 0.4f + 0.6f * dismissState.progress),
                                                                MaterialTheme.shapes.medium
                                                            )
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
                                                ProjectCard(
                                                    project = project,
                                                    counts = projectCounts[project.projectId],
                                                    onClick = { onProjectClick(project.projectId) },
                                                    isPinned = false,
                                                    onTogglePin = {},
                                                    isHidden = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LazyColumnScrollbar(
                    listState = listState,
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd).padding(end = 4.dp)
                )
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
private fun ProjectCard(
    project: Project,
    counts: Pair<Int?, Int?>?,
    onClick: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    isHidden: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHidden) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = project.title ?: project.projectId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // Only show ID when it differs from the display name
                if (project.title != null && project.title != project.projectId) {
                    Text(
                        text = "ID: ${project.projectId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Count chips — spinner while loading, numbers once ready
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountChip(
                        icon = Icons.Default.Science,
                        count = counts?.first,
                        loading = counts?.first == null,
                        contentDescription = "Samples"
                    )
                    CountChip(
                        icon = Icons.Default.Dataset,
                        count = counts?.second,
                        loading = counts?.second == null,
                        contentDescription = "Datasets"
                    )
                }
            } // Column
            } // inner Row (icon + text)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                if (!isHidden) {
                    IconButton(
                        onClick = { onTogglePin() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            modifier = Modifier.size(24.dp),
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CountChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    loading: Boolean,
    contentDescription: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = count?.toString() ?: "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


