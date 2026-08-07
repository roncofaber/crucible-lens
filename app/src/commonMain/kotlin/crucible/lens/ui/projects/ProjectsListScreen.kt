@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.projects
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.IdText
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.SearchBar

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ManageSyncedProjectsMenuItem
import crucible.lens.ui.common.ManageProjectMenuItem
import crucible.lens.ui.common.ToggleSyncMenuItem
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.LongPressMenuBox
import crucible.lens.platform.showToast
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.NotificationDot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.ScrollToTopButtonClearance
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import crucible.lens.ui.theme.emphasizedTitleMedium

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectsListScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onProjectClick: (String) -> Unit,
    pinnedProjects: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
    syncedProjects: Set<String> = emptySet(),
    onToggleSync: (String) -> Unit = {},
    onManageSyncedProjects: () -> Unit = {},
    onCreateProject: () -> Unit = {},
    onManageProject: (String) -> Unit = {},
    currentUserOrcid: String? = null,
) {
    val platformContext = getPlatformContext()
    val viewModel: ProjectsListViewModel = koinViewModel()
    val repository = koinInject<CrucibleRepository>()
    val loadState by viewModel.loadState.collectAsState()
    val refreshScope = rememberCoroutineScope()
    // Pending-request counts are cheap enough to refresh alongside the project list itself
    // (see fetchPendingJoinRequestCounts) — one extra call per refresh, not per project.
    fun refreshProjects() {
        viewModel.load(forceRefresh = true)
        val ledProjectIds = (loadState as? LoadState.Success)?.data
            ?.filter { it.projectLeadOrcid == currentUserOrcid }
            ?.map { it.projectId }
            ?: emptyList()
        if (currentUserOrcid != null && ledProjectIds.isNotEmpty()) {
            refreshScope.launch {
                try { repository.fetchPendingJoinRequestCounts(ledProjectIds) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }
    val projectCounts by viewModel.projectCounts.collectAsState()
    // Persistent cache summaries - loaded immediately for instant display
    var syncedExpanded by remember { mutableStateOf(true) }
    var unsyncedExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortState by remember { mutableStateOf(SortState(SortField.NAME, true)) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Projects pending unsync — excluded from syncedProjectsList so LazyColumn animates the removal
    // cleanly. onToggleSync is only called after the snackbar window closes without undo.
    val pendingUnsync = remember { mutableStateMapOf<String, Boolean>() }
    // Generation counter per project — bumped on undo so the re-shown item's items() key changes,
    // giving it a fresh SwipeToDismissBoxState (there's no supported way to reset a committed
    // SwipeToDismissBoxState back to Settled without fighting an in-progress drag).
    val undoGenerations = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) { /* ViewModel loads on init */ }

    // Preload and cache samples/datasets per project in background (also populates counts).
    // Priority: pinned projects first. Only synced projects are preloaded; everything else
    // fetches on demand when opened (this effect re-runs on the next syncedProjects change and
    // naturally picks up newly-synced projects).
    // This automatically cancels when the user navigates away from this screen.
    // Re-triggers when projects change, syncedProjects changes, OR reloadTrigger increments.
    LaunchedEffect(loadState, syncedProjects) {
        val projectList = (loadState as? LoadState.Success)?.data ?: return@LaunchedEffect
        val prioritizedProjects = projectList
            .filter { it.projectId in syncedProjects }
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

    // Use real projects if available, otherwise convert persistent summaries
    val allProjects = (loadState as? LoadState.Success)?.data ?: emptyList()

    // Search does per-project cache lookups and scans dataset metadata - expensive for many
    // synced projects, so memoize it instead of re-scanning on every recomposition (e.g. every
    // keystroke). Computed here rather than inline inside the LazyColumn below because that
    // builder's content lambda (`LazyListScope.() -> Unit`) isn't itself a composable scope, so
    // `remember` can't be called from inside it - only from `item {}`/`stickyHeader {}` blocks.
    // `projectCounts` doubles as a "the cache this reads from just got new project data" signal -
    // it's updated the same moment fetchProjectData populates the sample/dataset cache in the
    // preload effect above, so results still improve as background preload completes rather than
    // going stale until searchQuery changes again.
    val filteredProjects = remember(allProjects, searchQuery, projectCounts) {
        if (searchQuery.isBlank()) {
            allProjects
        } else {
            allProjects.filter { project ->
                // Search in project properties
                val matchesProject = project.title?.contains(searchQuery, ignoreCase = true) == true ||
                    project.projectId.contains(searchQuery, ignoreCase = true) ||
                    project.organization?.contains(searchQuery, ignoreCase = true) == true ||
                    project.lead?.email?.contains(searchQuery, ignoreCase = true) == true

                // Search in cached samples
                val matchesSamples = repository.getCachedProjectSamples(project.projectId)
                    ?.any { it.matchesSearch(searchQuery) } == true

                // Search in cached datasets (including metadata)
                val matchesDatasets = repository.getCachedProjectDatasets(project.projectId)
                    ?.any { it.matchesSearch(searchQuery) } == true

                matchesProject || matchesSamples || matchesDatasets
            }
        }
    }
    // Cheap bookkeeping (sort/pin/pending-unsync), unlike the search above - recomputed on every
    // recomposition is fine since it's just comparisons over the already-filtered project list.
    val syncedProjectsList = filteredProjects
        .filter { it.projectId in syncedProjects && pendingUnsync[it.projectId] != true }
        .applySortState(
            sortState,
            name = { title?.lowercase() ?: projectId.lowercase() },
            mfid = { projectId },
            date = { createdAt ?: "" }
        )
        // Pinned always float to top regardless of sort
        .sortedByDescending { it.projectId in pinnedProjects }
    val unsyncedProjectsList = filteredProjects
        .filter { it.projectId !in syncedProjects }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Projects",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    var listMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { listMenuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("New project") },
                                leadingIcon = { AppIcon(AppIcons.Add) },
                                onClick = { listMenuExpanded = false; onCreateProject() }
                            )
                            ManageSyncedProjectsMenuItem { listMenuExpanded = false; onManageSyncedProjects() }
                            RefreshMenuItem { listMenuExpanded = false; refreshProjects() }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loadState.isRefreshingNow,
            onRefresh = { refreshProjects() },
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar sits outside LazyColumn so group headers can stick
                    // without pushing the search bar off-screen
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
                                modifier = Modifier.weight(1f)
                            )
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
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
                    // Scrollbar is scoped to this Box, not the outer one: the outer Box now
                    // also contains the search bar, and a scrollbar spanning it would be taller
                    // than the list it represents.
                    Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Clears the ScrollToTopButton FAB so the last item — including the Hidden
                        // section header/rows — is never obscured.
                        contentPadding = PaddingValues(bottom = ScrollToTopButtonClearance)
                    ) {

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
                                    onRetry = { refreshProjects() }
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
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AppIcon(AppIcons.FolderOpen,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "No Projects Found",
                                                style = MaterialTheme.typography.emphasizedTitleMedium
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
                            // Show message when search returns no results
                            if (searchQuery.isNotBlank() && filteredProjects.isEmpty()) {
                                item(key = "__no_search_results__") {
                                    Box(modifier = Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    AppIcon(AppIcons.SearchOff,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "No Results Found",
                                                        style = MaterialTheme.typography.emphasizedTitleMedium
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
                                if (syncedProjectsList.isNotEmpty()) {
                                    stickyHeader(key = "__synced_header__") {
                                        SectionHeader(
                                            title = "Syncing",
                                            count = syncedProjectsList.size,
                                            icon = AppIcons.Syncing,
                                            expanded = syncedExpanded,
                                            onToggle = { syncedExpanded = !syncedExpanded }
                                        )
                                    }
                                }

                                if (syncedExpanded) {
                                    itemsIndexed(syncedProjectsList, key = { _, it -> "${it.projectId}:${undoGenerations[it.projectId] ?: 0}" }) { index, project ->
                                    if (index > 0) {
                                        HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
                                    }
                                    SwipeToHideItem(
                                        direction = SwipeToDismissBoxValue.EndToStart,
                                        action = SwipeAction(
                                            icon = AppIcons.SyncPaused,
                                            label = "Stop syncing",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        onDismiss = {
                                            hideWithUndo(
                                                scope = scope,
                                                snackbarHostState = snackbarHostState,
                                                itemLabel = project.title ?: project.projectId,
                                                message = "\"${project.title ?: project.projectId}\" will stop syncing",
                                                onPending = { pending ->
                                                    if (pending) pendingUnsync[project.projectId] = true
                                                    else pendingUnsync.remove(project.projectId)
                                                },
                                                onConfirmedHide = { onToggleSync(project.projectId) },
                                                onUndone = {
                                                    onToggleSync(project.projectId)
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
                                            },
                                            onManage = { onManageProject(project.projectId) },
                                            onToggleSyncAction = { onToggleSync(project.projectId) }
                                        )
                                    }
                                    }
                                }

                                if (unsyncedProjectsList.isNotEmpty()) {
                                    stickyHeader(key = "__unsynced_header__") {
                                        SectionHeader(
                                            title = "Not syncing",
                                            count = unsyncedProjectsList.size,
                                            icon = AppIcons.SyncPaused,
                                            expanded = unsyncedExpanded,
                                            onToggle = { unsyncedExpanded = !unsyncedExpanded }
                                        )
                                    }

                                    if (unsyncedExpanded) {
                                        itemsIndexed(unsyncedProjectsList, key = { _, it -> "unsynced_${it.projectId}" }) { index, project ->
                                            if (index > 0) {
                                                HorizontalDivider(modifier = Modifier.padding(start = ResourceListDividerInset))
                                            }
                                            SwipeToHideItem(
                                                direction = SwipeToDismissBoxValue.StartToEnd,
                                                action = SwipeAction(
                                                    icon = AppIcons.Syncing,
                                                    label = "Start syncing",
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                onDismiss = {
                                                    showToast(platformContext, "Syncing ${project.title ?: project.projectId}")
                                                    onToggleSync(project.projectId)
                                                }
                                            ) {
                                                ProjectCard(
                                                    project = project,
                                                    counts = projectCounts[project.projectId],
                                                    onClick = { onProjectClick(project.projectId) },
                                                    isPinned = false,
                                                    onTogglePin = {},
                                                    isSynced = false,
                                                    onManage = { onManageProject(project.projectId) },
                                                    onToggleSyncAction = { onToggleSync(project.projectId) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // A proper call-to-action button, not a compact tappable row like
                                // "Add member" — this is the one primary action on the whole
                                // screen (create a brand new project), so it earns the same
                                // treatment as Home's "New Sample"/"New Dataset" buttons rather
                                // than blending in as just another list row.
                                item(key = "__create_project__") {
                                    OutlinedButton(
                                        onClick = onCreateProject,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 16.dp)
                                            .height(52.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        AppIcon(AppIcons.Add, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Create Project")
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
                    }
                }
                ScrollToTopButton(
                    visible = showScrollToTop,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: Project,
    counts: Pair<Int?, Int?>?,
    onClick: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    isSynced: Boolean = true,
    onManage: () -> Unit = {},
    onToggleSyncAction: () -> Unit = {}
) {
    // Only show ID when it differs from the display name
    val showId = project.title != null && project.title != project.projectId
    // Only ever non-null/non-zero for projects the caller leads — DataSyncManager's preload
    // scopes this fetch to led projects, so a non-lead's cache entry for this id is just absent.
    val repository = koinInject<CrucibleRepository>()
    val platformCtx = getPlatformContext()
    val pendingRequestCount by repository.observePendingJoinRequestCount(project.projectId)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedPendingJoinRequestCount(project.projectId))
    LongPressMenuBox(
        menu = { dismiss ->
            ManageProjectMenuItem { dismiss(); onManage() }
            ToggleSyncMenuItem(isSynced) { dismiss(); onToggleSyncAction() }
            CopyIdMenuItem { dismiss(); copyToClipboard(platformCtx, project.projectId) }
        }
    ) { onLongClick ->
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
                { IdText(project.projectId, modifier = Modifier.padding(start = 4.dp)) }
            } else null,
            leadingContent = {
                NotificationDot(count = pendingRequestCount) {
                    AppIcon(AppIcons.Project,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSynced) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                            CountChip(icon = AppIcons.Sample, count = counts?.first, loading = counts?.first == null)
                            CountChip(icon = AppIcons.Dataset, count = counts?.second, loading = counts?.second == null)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                            AppIcon(AppIcons.Pinned, filled = isPinned,
                                modifier = Modifier.size(20.dp),
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isSynced) {
                            AppIcon(AppIcons.SyncPaused,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
    }
}

@Composable
private fun CountChip(
    icon: AppIconToken,
    count: Int?,
    loading: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
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
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Text(
                    text = count?.toString() ?: "?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}


