package crucible.lens.ui.projects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Project
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsListScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectClick: (String) -> Unit,
    onSearch: () -> Unit = {},
    pinnedProjects: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
    archivedProjects: Set<String> = emptySet(),
    onToggleArchive: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Map of projectId -> Pair(sampleCount, datasetCount), null means still loading
    var projectCounts by remember { mutableStateOf<Map<String, Pair<Int?, Int?>>>(emptyMap()) }
    var archivedExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadProjects(forceRefresh: Boolean = false) {
        scope.launch {
            try {
                // Check cache first if not forcing refresh
                if (!forceRefresh) {
                    val cachedProjects = CacheManager.getProjects()
                    if (cachedProjects != null) {
                        projects = cachedProjects
                        isLoading = false
                        return@launch
                    }
                }

                isLoading = true
                error = null
                val response = ApiClient.service.getProjects()
                if (response.isSuccessful) {
                    val fetchedProjects = response.body()
                    projects = fetchedProjects
                    // Cache the projects
                    fetchedProjects?.let { CacheManager.cacheProjects(it) }
                } else {
                    error = "Failed to load projects: ${response.message()}"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadProjects()
    }

    // Pre-load and cache samples/datasets per project in background (also populates counts).
    // Priority: pinned projects first, archived projects last (stage 2 skipped for archived).
    LaunchedEffect(projects) {
        val projectList = projects ?: return@LaunchedEffect
        val prioritizedProjects = projectList
            .sortedWith(compareByDescending<Project> { it.projectId in pinnedProjects }
                .thenBy { it.projectId in archivedProjects })

        prioritizedProjects.forEach { project ->
            val isArchived = project.projectId in archivedProjects
            scope.launch {
                // Use already-cached data if available — no API call needed
                val cachedSamples = CacheManager.getProjectSamples(project.projectId)
                val cachedDatasets = CacheManager.getProjectDatasets(project.projectId)
                if (cachedSamples != null && cachedDatasets != null) {
                    projectCounts = projectCounts + (project.projectId to Pair(cachedSamples.size, cachedDatasets.size))
                    return@launch
                }

                // Stage 1 — fast: fetch samples (cacheable) + datasets without metadata (count only)
                val sampleCount = try {
                    val resp = ApiClient.service.getSamplesByProject(project.projectId)
                    if (resp.isSuccessful) {
                        resp.body()?.also { CacheManager.cacheProjectSamples(project.projectId, it) }?.size
                    } else null
                } catch (e: Exception) { null }

                val datasetCount = try {
                    val resp = ApiClient.service.getDatasetsByProject(project.projectId, includeMetadata = false)
                    if (resp.isSuccessful) resp.body()?.size else null
                } catch (e: Exception) { null }

                // Show counts immediately without waiting for heavy metadata
                projectCounts = projectCounts + (project.projectId to Pair(sampleCount, datasetCount))

                // Stage 2 — background: fetch full datasets to warm navigation cache.
                // Skipped for archived projects since they are rarely browsed.
                if (!isArchived) {
                    launch {
                        try {
                            val resp = ApiClient.service.getDatasetsByProject(project.projectId, includeMetadata = true)
                            if (resp.isSuccessful) {
                                resp.body()?.let { CacheManager.cacheProjectDatasets(project.projectId, it) }
                            }
                        } catch (e: Exception) { /* best effort */ }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
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
                    val allProjects = projects ?: emptyList()
                    val activeProjects = allProjects
                        .filter { it.projectId !in archivedProjects }
                        .sortedByDescending { it.projectId in pinnedProjects }
                    val archivedProjectsList = allProjects
                        .filter { it.projectId in archivedProjects }

                    LazyColumn(
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
                                }
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = 0.75f + 0.5f * dismissState.progress,
                                animationSpec = spring(),
                                label = "archiveIconScale"
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                modifier = Modifier.animateItemPlacement(spring()),
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
                                                onToggleArchive(project.projectId)
                                                true
                                            } else false
                                        }
                                    )
                                    val unarchiveIconScale by animateFloatAsState(
                                        targetValue = 0.75f + 0.5f * dismissState.progress,
                                        animationSpec = spring(),
                                        label = "unarchiveIconScale"
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = true,
                                        enableDismissFromEndToStart = false,
                                        modifier = Modifier.animateItemPlacement(spring()),
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
                }
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
