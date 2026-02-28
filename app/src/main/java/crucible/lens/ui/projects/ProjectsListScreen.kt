package crucible.lens.ui.projects

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicInteger
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.cache.ProjectSummary
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.UiConstants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsListScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectClick: (String) -> Unit,
    pinnedProjects: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
    archivedProjects: Set<String> = emptySet(),
    onToggleArchive: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Map of projectId -> Pair(sampleCount, datasetCount), null means still loading
    var projectCounts by remember { mutableStateOf<Map<String, Pair<Int?, Int?>>>(emptyMap()) }
    // Persistent cache summaries - loaded immediately for instant display
    var persistentSummaries by remember { mutableStateOf<List<ProjectSummary>?>(null) }
    var archivedExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Track which projects were manually unarchived (so we don't auto-archive them again)
    var manuallyUnarchived by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Trigger for forcing background reload - increments on refresh
    var reloadTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    // Load persistent cache immediately on startup for instant display
    LaunchedEffect(Unit) {
        persistentSummaries = PersistentProjectCache.loadProjectData(context)
        // If we have persistent cache, populate counts immediately
        persistentSummaries?.let { summaries ->
            projectCounts = summaries.associate {
                it.projectId to Pair(it.sampleCount, it.datasetCount)
            }
        }
    }

    fun loadProjects(forceRefresh: Boolean = false) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Check cache first if not forcing refresh
                if (!forceRefresh) {
                    val cachedProjects = CacheManager.getProjects()
                    if (cachedProjects != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            projects = cachedProjects
                            isLoading = false
                            pullRefreshState.endRefresh()
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
                val response = ApiClient.service.getProjects()
                if (response.isSuccessful) {
                    val fetchedProjects = response.body()
                    // Cache the projects
                    fetchedProjects?.let { CacheManager.cacheProjects(it) }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        projects = fetchedProjects
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        error = "Failed to load projects: ${response.message()}"
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    error = "Error: ${e.message}"
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = false
                    pullRefreshState.endRefresh()
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
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Build maps of samples and datasets
                val samplesMap = mutableMapOf<String, List<crucible.lens.data.model.Sample>>()
                val datasetsMap = mutableMapOf<String, List<Dataset>>()

                currentProjects.forEach { project ->
                    CacheManager.getProjectSamples(project.projectId)?.let {
                        samplesMap[project.projectId] = it
                    }
                    CacheManager.getProjectDatasets(project.projectId)?.let {
                        datasetsMap[project.projectId] = it
                    }
                }

                PersistentProjectCache.saveProjectData(context, currentProjects, samplesMap, datasetsMap)
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    // Pre-load and cache samples/datasets per project in background (also populates counts).
    // Priority: pinned projects first, archived projects last (stage 2 skipped for archived).
    // This automatically cancels when the user navigates away from this screen.
    // Re-triggers when projects change OR when reloadTrigger increments (force refresh).
    LaunchedEffect(projects, reloadTrigger) {
        val projectList = projects ?: return@LaunchedEffect
        val prioritizedProjects = projectList
            .sortedWith(compareByDescending<Project> { it.projectId in pinnedProjects }
                .thenBy { it.projectId in archivedProjects })

        // Track consecutive failures to stop on network errors (thread-safe for concurrent launches)
        val consecutiveFailures = AtomicInteger(0)
        val maxConsecutiveFailures = 5

        // Process in small batches to avoid overwhelming the device
        prioritizedProjects.chunked(5).forEach { batch ->
            // Stop if we've had too many consecutive failures (likely network issue)
            if (consecutiveFailures.get() >= maxConsecutiveFailures) {
                return@LaunchedEffect
            }
            batch.forEach { project ->
                val isArchived = project.projectId in archivedProjects
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    // Use already-cached data if available — no API call needed
                    val cachedSamples = CacheManager.getProjectSamples(project.projectId)
                    val cachedDatasets = CacheManager.getProjectDatasets(project.projectId)
                    if (cachedSamples != null && cachedDatasets != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            projectCounts = projectCounts + (project.projectId to Pair(cachedSamples.size, cachedDatasets.size))

                            // Auto-archive empty projects (unless manually unarchived or already archived)
                            if (cachedSamples.isEmpty() && cachedDatasets.isEmpty() &&
                                project.projectId !in manuallyUnarchived &&
                                project.projectId !in archivedProjects) {
                                onToggleArchive(project.projectId)
                            }
                        }
                        return@launch
                    }

                    // Fetch samples and datasets with metadata for search functionality
                    var hadError = false
                    val sampleCount = try {
                        val resp = ApiClient.service.getSamplesByProject(project.projectId)
                        if (resp.isSuccessful) {
                            consecutiveFailures.set(0) // Reset on success
                            resp.body()?.also { CacheManager.cacheProjectSamples(project.projectId, it) }?.size
                        } else null
                    } catch (e: Exception) {
                        hadError = true
                        null
                    }

                    val datasetCount = try {
                        // Load with metadata for search functionality
                        val resp = ApiClient.service.getDatasetsByProject(project.projectId, includeMetadata = true)
                        if (resp.isSuccessful) {
                            consecutiveFailures.set(0) // Reset on success
                            resp.body()?.also { CacheManager.cacheProjectDatasets(project.projectId, it) }?.size
                        } else null
                    } catch (e: Exception) {
                        hadError = true
                        null
                    }

                    // Track failures (thread-safe increment)
                    if (hadError) {
                        consecutiveFailures.incrementAndGet()
                    }

                    // Show counts after loading
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        projectCounts = projectCounts + (project.projectId to Pair(sampleCount, datasetCount))

                        // Auto-archive empty projects (unless manually unarchived or already archived)
                        if (sampleCount == 0 && datasetCount == 0 &&
                            project.projectId !in manuallyUnarchived &&
                            project.projectId !in archivedProjects) {
                            onToggleArchive(project.projectId)
                        }
                    }
                }
            }
            // Small delay between batches to avoid overwhelming the device
            kotlinx.coroutines.delay(150)
        }

        // After all batches complete, save to persistent cache
        saveToPersistentCache()
    }

    Scaffold(
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
                        IconButton(
                            onClick = {
                                CacheManager.clearProjectsCache()
                                CacheManager.clearProjectDetailsCache()
                                projectCounts = emptyMap()
                                loadProjects(forceRefresh = true)
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onHome) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                    }
                )
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search by name, ID, or project lead...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        floatingActionButton = {
            // Show scroll to top button when scrolled down
            val showScrollToTop by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 }
            }
            if (showScrollToTop) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(bottom = UiConstants.ScrollToTopFabBottomPadding)
                        .size(UiConstants.ScrollToTopFabSize),
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
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            when {
                isLoading && persistentSummaries == null -> {
                    // Loading with no cached data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading projects...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error != null -> {
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
                                    text = "Error Loading Projects",
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
                projects?.isEmpty() == true -> {
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
                else -> {
                    // Use real projects if available, otherwise convert persistent summaries
                    val allProjects = projects ?: persistentSummaries?.map { summary ->
                        Project(
                            projectId = summary.projectId,
                            projectName = summary.projectName,
                            description = summary.description,
                            createdAt = summary.createdAt,
                            projectLeadEmail = summary.projectLeadEmail
                        )
                    } ?: emptyList()

                    // Filter projects based on search query (includes project, samples, and datasets with metadata)
                    val filteredProjects = if (searchQuery.isBlank()) {
                        allProjects
                    } else {
                        allProjects.filter { project ->
                            // Search in project properties
                            val matchesProject = project.projectName?.contains(searchQuery, ignoreCase = true) == true ||
                                project.projectId.contains(searchQuery, ignoreCase = true) ||
                                project.description?.contains(searchQuery, ignoreCase = true) == true ||
                                project.projectLeadEmail?.contains(searchQuery, ignoreCase = true) == true

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
                        .filter { it.projectId !in archivedProjects }
                        .sortedByDescending { it.projectId in pinnedProjects }
                    val archivedProjectsList = filteredProjects
                        .filter { it.projectId in archivedProjects }

                    // Show message when search returns no results
                    if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
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
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activeProjects, key = { it.projectId }) { project ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        onToggleArchive(project.projectId)
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
                                label = "archiveIconScale"
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                modifier = Modifier.animateItemPlacement(
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
                                            Icon(Icons.Default.Archive, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                                            Text("Archive", style = MaterialTheme.typography.labelSmall, color = contentColor)
                                        }
                                    }
                                }
                            ) {
                                ProjectCard(
                                    project = project,
                                    counts = projectCounts[project.projectId],
                                    onClick = { onProjectClick(project.projectId) },
                                    isPinned = project.projectId in pinnedProjects,
                                    onTogglePin = { onTogglePin(project.projectId) }
                                )
                            }
                        }

                        if (archivedProjectsList.isNotEmpty()) {
                            item(key = "__archived_header__") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { archivedExpanded = !archivedExpanded }
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Archive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Archived (${archivedProjectsList.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        if (archivedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (archivedExpanded) {
                                items(archivedProjectsList, key = { "arch_${it.projectId}" }) { project ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                // Mark as manually unarchived to prevent auto-archiving
                                                manuallyUnarchived = manuallyUnarchived + project.projectId
                                                onToggleArchive(project.projectId)
                                                true
                                            } else false
                                        },
                                        positionalThreshold = { totalDistance -> totalDistance * 0.65f }
                                    )
                                    val unarchiveIconScale by animateFloatAsState(
                                        targetValue = 0.75f + 0.5f * dismissState.progress,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "unarchiveIconScale"
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = false,
                                        modifier = Modifier.animateItemPlacement(
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
                                                    modifier = Modifier.scale(unarchiveIconScale)
                                                ) {
                                                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                                                    Text("Unarchive", style = MaterialTheme.typography.labelSmall, color = contentColor)
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
                                            isArchived = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Scrollbar for project list
                    LazyColumnScrollbar(
                        listState = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                    )
                }
            }

            // Only show pull-to-refresh indicator when not showing full loading screen
            if ((pullRefreshState.isRefreshing || pullRefreshState.verticalOffset > 0f) &&
                !(isLoading && persistentSummaries == null)) {
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
            CacheManager.clearProjectsCache()
            CacheManager.clearProjectDetailsCache()
            projectCounts = emptyMap()
            loadProjects(forceRefresh = true)
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
    isArchived: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArchived) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
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
                    if (isArchived) Icons.Default.Archive else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = project.projectName ?: project.projectId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (project.description != null) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Only show ID when it differs from the display name
                if (project.projectName != null && project.projectName != project.projectId) {
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
                        loading = counts == null,
                        contentDescription = "Samples"
                    )
                    CountChip(
                        icon = Icons.Default.Dataset,
                        count = counts?.second,
                        loading = counts == null,
                        contentDescription = "Datasets"
                    )
                }
            } // Column
            } // inner Row (icon + text)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((-8).dp)
            ) {
                if (!isArchived) {
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

// Search helper functions
private fun crucible.lens.data.model.Sample.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (sampleType?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true) ||
        (internalId?.toString()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (keywords?.any { it.lowercase().contains(q) } == true)
}

private fun crucible.lens.data.model.Dataset.matchesSearch(query: String): Boolean {
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
        (keywords?.any { it.lowercase().contains(q) } == true) ||
        (scientificMetadata?.containsQuery(q) == true)
}

private fun Map<String, Any?>.containsQuery(q: String): Boolean =
    entries.any { (key, value) -> key.lowercase().contains(q) || value.matchesQuery(q) }

private fun Any?.matchesQuery(q: String): Boolean = when (this) {
    null -> false
    is String -> lowercase().contains(q)
    is Number -> toString().contains(q)
    is Map<*, *> -> entries.any { (k, v) ->
        k.toString().lowercase().contains(q) || v.matchesQuery(q)
    }
    is List<*> -> any { it.matchesQuery(q) }
    else -> toString().lowercase().contains(q)
}

