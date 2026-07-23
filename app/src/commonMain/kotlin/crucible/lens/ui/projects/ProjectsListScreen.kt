@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.projects
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.ResourceListDividerInset
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.SwipeAction
import crucible.lens.ui.common.SwipeToHideItem
import crucible.lens.ui.common.hideWithUndo
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.SearchBar

import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ToggleHiddenMenuItem
import crucible.lens.platform.showToast
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingContent
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.ScrollToTopButton
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalFoundationApi::class)
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
    val viewModel: ProjectsListViewModel = koinViewModel()
    val repository = koinInject<CrucibleRepository>()
    val cacheManager = koinInject<CacheManager>()
    val loadState by viewModel.loadState.collectAsState()
    val projectCounts by viewModel.projectCounts.collectAsState()
    // Persistent cache summaries - loaded immediately for instant display
    var hiddenExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortState by remember { mutableStateOf(SortState(SortField.NAME, true)) }
    // Track which projects were manually unarchived (so we don't auto-archive them again)
    var manuallyShown by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Projects pending hide — excluded from activeProjects so LazyColumn animates the removal
    // cleanly. onToggleHide is only called after the snackbar window closes without undo.
    val pendingHide = remember { mutableStateMapOf<String, Boolean>() }
    // Generation counter per project — bumped on undo so the re-shown item's items() key changes,
    // giving it a fresh SwipeToDismissBoxState (there's no supported way to reset a committed
    // SwipeToDismissBoxState back to Settled without fighting an in-progress drag).
    val undoGenerations = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) { /* ViewModel loads on init */ }

    // Preload and cache samples/datasets per project in background (also populates counts).
    // Priority: pinned projects first. Hidden projects are skipped entirely — no network call
    // is made for them until the user unhides them (this effect re-runs on the next hiddenProjects
    // change and naturally picks up newly-unhidden projects).
    // This automatically cancels when the user navigates away from this screen.
    // Re-triggers when projects change, hiddenProjects changes, OR reloadTrigger increments.
    LaunchedEffect(loadState, hiddenProjects) {
        val projectList = (loadState as? LoadState.Success)?.data ?: return@LaunchedEffect
        val prioritizedProjects = projectList
            .filter { it.projectId !in hiddenProjects }
            .sortedByDescending { it.projectId in pinnedProjects }

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
                        repository.fetchProjectData(
                            projectId = project.projectId,
                            onCountsAvailable = { sampleCount, datasetCount ->
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    viewModel.updateCount(project.projectId, sampleCount, datasetCount)

                                    if (sampleCount == 0 && datasetCount == 0 &&
                                        project.projectId !in manuallyShown &&
                                        project.projectId !in hiddenProjects) {
                                        onToggleHide(project.projectId)
                                    }
                                }
                            }
                        )
                        consecutiveFailures = 0
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        consecutiveFailures++
                    }
                }
            }
            // Small delay between batches to avoid overwhelming the device
            kotlinx.coroutines.delay(150)
        }

    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Projects",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    var listMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { listMenuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                            ToggleHiddenMenuItem(hiddenExpanded) { hiddenExpanded = !hiddenExpanded; listMenuExpanded = false }
                            RefreshMenuItem { listMenuExpanded = false; viewModel.load(forceRefresh = true) }
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
                                    placeholder = "Search by name, ID, or project lead…",
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
                                        listOf(SortField.NAME to "Name", SortField.DATE to "Date created").forEach { (field, label) ->
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

                    when {
                        loadState is LoadState.Loading -> item(key = "__loading__") {
                            Box(
                                modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.85f),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingContent(title = "Loading Projects")
                            }
                        }
                        loadState is LoadState.Error -> item(key = "__error__") {
                            Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                                ErrorCard(
                                    title = "Error Loading Projects",
                                    message = (loadState as LoadState.Error).message,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onRetry = { viewModel.load(forceRefresh = true) }
                                )
                            }
                        }
                        (loadState as? LoadState.Success)?.data?.isEmpty() == true -> item(key = "__empty__") {
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
                                            AppIcon(AppIcons.FolderOpen,
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
                            val allProjects = (loadState as? LoadState.Success)?.data ?: emptyList()

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
                                    val matchesSamples = cacheManager.getProjectSamples(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true

                                    // Search in cached datasets (including metadata)
                                    val matchesDatasets = cacheManager.getProjectDatasets(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true

                                    matchesProject || matchesSamples || matchesDatasets
                                }
                            }

                            val activeProjects = filteredProjects
                                .filter { it.projectId !in hiddenProjects && pendingHide[it.projectId] != true }
                                .applySortState(
                                    sortState,
                                    name = { title?.lowercase() ?: projectId.lowercase() },
                                    mfid = { projectId },
                                    date = { createdAt ?: "" }
                                )
                                // Pinned always float to top regardless of sort
                                .sortedByDescending { it.projectId in pinnedProjects }
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
                                                    AppIcon(AppIcons.SearchOff,
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
                                items(activeProjects, key = { "${it.projectId}:${undoGenerations[it.projectId] ?: 0}" }) { project ->
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
                                                itemLabel = project.title ?: project.projectId,
                                                onPending = { pending ->
                                                    if (pending) pendingHide[project.projectId] = true
                                                    else pendingHide.remove(project.projectId)
                                                },
                                                onConfirmedHide = { onToggleHide(project.projectId) },
                                                onUndone = {
                                                    onToggleHide(project.projectId)
                                                    undoGenerations[project.projectId] = (undoGenerations[project.projectId] ?: 0) + 1
                                                }
                                            )
                                        }
                                    ) {
                                        ProjectCard(
                                            project = project,
                                            counts = projectCounts[project.projectId],
                                            onClick = { onProjectClick(project.projectId) },
                                            isPinned = project.projectId in pinnedProjects,
                                            onTogglePin = {
                                                showToast(platformContext, if (project.projectId in pinnedProjects) "Project unpinned" else "Project pinned")
                                                onTogglePin(project.projectId)
                                            }
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
                                }

                                if (hiddenProjectsList.isNotEmpty()) {
                                    item(key = "__hidden_header__") {
                                        SectionHeader(
                                            title = "Hidden",
                                            count = hiddenProjectsList.size,
                                            icon = AppIcons.HideContent,
                                            expanded = hiddenExpanded,
                                            onToggle = { hiddenExpanded = !hiddenExpanded }
                                        )
                                    }

                                    if (hiddenExpanded) {
                                        items(hiddenProjectsList, key = { "hidden_${it.projectId}" }) { project ->
                                            SwipeToHideItem(
                                                direction = SwipeToDismissBoxValue.StartToEnd,
                                                action = SwipeAction(
                                                    icon = AppIcons.ShowContent,
                                                    label = "Show",
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                onDismiss = {
                                                    manuallyShown = manuallyShown + project.projectId
                                                    showToast(platformContext, "Project shown")
                                                    onToggleHide(project.projectId)
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
                                            HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
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
    // Only show ID when it differs from the display name
    val showId = project.title != null && project.title != project.projectId
    ListItem(
        headlineContent = {
            Text(
                text = project.title ?: project.projectId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (showId) {
            {
                Text(
                    text = "ID: ${project.projectId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        leadingContent = {
            AppIcon(if (isHidden) AppIcons.HideContent else AppIcons.Project,
                tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isHidden) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                        CountChip(icon = AppIcons.Sample, count = counts?.first, loading = counts?.first == null)
                        CountChip(icon = AppIcons.Dataset, count = counts?.second, loading = counts?.second == null)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    if (!isHidden) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                            AppIcon(AppIcons.Pinned, filled = isPinned,
                                modifier = Modifier.size(20.dp),
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun CountChip(
    icon: AppIconToken,
    count: Int?,
    loading: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            AppIcon(
                icon,
                modifier = Modifier.size(11.dp),
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


