@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.projects
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*


import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.util.dateGroupKey
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.matchesSearch
import androidx.lifecycle.viewmodel.compose.viewModel
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.platform.showToast
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.fadeEndEdge
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.platform.openUrl
import androidx.compose.ui.graphics.SolidColor
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.createAppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ProjectContent is defined in ProjectDetailViewModel.kt

private enum class SampleGroupBy(val label: String) {
    NONE("None"), TYPE("Type"), DATE("Date"), OWNER("Owner")
}

private enum class DatasetGroupBy(val label: String) {
    NONE("None"), MEASUREMENT("Measurement"), INSTRUMENT("Instrument"),
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
            when (val resp = ApiClient.service.getProjectUsers(projectId)) {
                is ApiResult.Success -> {
                    resp.data.forEach { u -> u.uniqueId?.let { id -> ownerNames[id] = ownerDisplayName(u.firstName, u.lastName) } }
                }
                is ApiResult.Error -> {
                    // Fail silently
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
        ownerNamesReady = true
    }
    return ownerNames to ownerNamesReady
}

@Composable
private fun EmptyListCard(
    resourceName: String,
    defaultIcon: AppIconToken,
    isFiltered: Boolean
) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(
                        if (isFiltered) AppIcons.SearchOff else defaultIcon,
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

private fun androidx.compose.foundation.lazy.LazyListScope.loadMoreItem(
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


@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    graphExplorerUrl: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit = {},
    onResourceClick: (uuid: String, groupBy: String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    isHidden: Boolean = false,
    onCreateSample: () -> Unit = {},
    onCreateDataset: () -> Unit = {},
    onManageProject: () -> Unit = {},
    onUserClick: (String) -> Unit = {}) {
    val project = remember(projectId) {
        CacheManager.getProjects()?.find { it.projectId == projectId }
    }

    val ctx = getPlatformContext()
    val prefs = remember(ctx) { createAppPreferences(ctx) }

    val viewModel: ProjectDetailViewModel = viewModel()
    val loadState by viewModel.loadState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sampleGroupBy by remember { mutableStateOf(SampleGroupBy.TYPE) }
    var datasetGroupBy by remember { mutableStateOf(DatasetGroupBy.MEASUREMENT) }
    var sortState by remember { mutableStateOf(SortState()) }
    val scope = rememberCoroutineScope()

    // Load persisted group-by choices and default tab on first composition
    LaunchedEffect(Unit) {
        sampleGroupBy = SampleGroupBy.valueOf(prefs.sampleGroupBy.first())
        datasetGroupBy = DatasetGroupBy.valueOf(prefs.datasetGroupBy.first())
        val tab = prefs.defaultProjectTab.first()
        if (tab == AppPreferences.PROJECT_TAB_DATASETS) {
            pagerState.scrollToPage(1)
        }
    }

    val projectContent = (loadState as? LoadState.Success)?.data
    val samples = projectContent?.samples ?: emptyList()
    val datasets = projectContent?.datasets ?: emptyList()
    val filteredSamples = remember(loadState, searchQuery) {
        if (searchQuery.isBlank()) samples
        else samples.filter { it.matchesSearch(searchQuery) }
    }
    val filteredDatasets = remember(loadState, searchQuery) {
        if (searchQuery.isBlank()) datasets
        else datasets.filter { it.matchesSearch(searchQuery) }
    }

    LaunchedEffect(projectId) { viewModel.load(projectId, isHidden = isHidden) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Project",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    var topBarMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { topBarMenuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(expanded = topBarMenuExpanded, onDismissRequest = { topBarMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("New Sample") },
                                leadingIcon = { AppIcon(AppIcons.Add) },
                                onClick = { topBarMenuExpanded = false; onCreateSample() }
                            )
                            DropdownMenuItem(
                                text = { Text("New Dataset") },
                                leadingIcon = { AppIcon(AppIcons.Dataset) },
                                onClick = { topBarMenuExpanded = false; onCreateDataset() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Manage project") },
                                leadingIcon = { AppIcon(AppIcons.ManageMembers) },
                                onClick = { topBarMenuExpanded = false; onManageProject() }
                            )
                            OpenInWebMenuItem { topBarMenuExpanded = false; openUrl(ctx, "$graphExplorerUrl/$projectId") }
                            ShareMenuItem { topBarMenuExpanded = false; shareText(ctx, "$graphExplorerUrl/$projectId", project?.title ?: projectId) }
                            HorizontalDivider()
                            RefreshMenuItem { topBarMenuExpanded = false; viewModel.load(projectId, isHidden = isHidden, forceRefresh = true) }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loadState.isRefreshingNow,
            onRefresh = { viewModel.load(projectId, isHidden = isHidden, forceRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ProjectHeader — fixed, not part of the pager.
                // Modifier.scrollable with a no-op state forwards vertical drags
                // upward through the nested scroll chain so PTR activates here too.
                Box(
                    modifier = Modifier.scrollable(
                        state = rememberScrollableState { 0f },
                        orientation = Orientation.Vertical
                    )
                ) {
                    ProjectHeader(
                        project = project,
                        projectId = projectId,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        isPinned = isPinned,
                        onTogglePin = onTogglePin,
                        currentPage = pagerState.currentPage,
                        sampleGroupBy = sampleGroupBy,
                        datasetGroupBy = datasetGroupBy,
                        onSampleGroupByChange = { sampleGroupBy = it; scope.launch { prefs.saveSampleGroupBy(it.name) }; showToast(ctx, "Grouped by ${it.label}") },
                        onDatasetGroupByChange = { datasetGroupBy = it; scope.launch { prefs.saveDatasetGroupBy(it.name) }; showToast(ctx, "Grouped by ${it.label}") },
                        sortState = sortState,
                        onSortStateChange = { sortState = it; showToast(ctx, "Sorted by ${it.field.label} ${if (it.ascending) "↑" else "↓"}") },
                        onUserClick = onUserClick)
                }

                // TabRow — fixed below the header, above the pager
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = 0,
                                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        text = {
                            val count = filteredSamples.size
                            val total = samples.size
                            val label = if (searchQuery.isBlank()) "Samples ($total)"
                                else "Samples ($count/$total)"
                            AnimatedContent(
                                targetState = label,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "samples_tab_label"
                            ) { Text(it) }
                        },
                        icon = { AppIcon(AppIcons.Sample) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = 1,
                                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        text = {
                            val count = filteredDatasets.size
                            val total = datasets.size
                            val label = when {
                                searchQuery.isBlank() -> "Datasets ($total)"
                                else -> "Datasets ($count/$total)"
                            }
                            AnimatedContent(
                                targetState = label,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                label = "datasets_tab_label"
                            ) { Text(it) }
                        },
                        icon = { AppIcon(AppIcons.Dataset) }
                    )
                }

                // Pager — only list content swipes, header+tabs stay fixed above
                when {
                    loadState is LoadState.Loading -> LoadingContent(
                        title = "Loading Project Data",
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    loadState is LoadState.Error -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        ErrorCard(
                            title = "Error Loading Data",
                            message = (loadState as LoadState.Error).message,
                            modifier = Modifier.padding(16.dp),
                            onRetry = { viewModel.load(projectId, isHidden = isHidden, forceRefresh = true) }
                        )
                    }
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> SamplesList(
                                samples = filteredSamples,
                                isFiltered = searchQuery.isNotBlank(),
                                fromCache = (loadState as? LoadState.Success)?.fromCache ?: false,
                                projectId = projectId,
                                graphExplorerUrl = graphExplorerUrl,
                                groupBy = sampleGroupBy,
                                sortState = sortState,
                                onSampleClick = { uuid -> onResourceClick(uuid, sampleGroupBy.name) }
                            )
                            1 -> DatasetsList(
                                datasets = filteredDatasets,
                                isFiltered = searchQuery.isNotBlank(),
                                fromCache = (loadState as? LoadState.Success)?.fromCache ?: false,
                                projectId = projectId,
                                graphExplorerUrl = graphExplorerUrl,
                                groupBy = datasetGroupBy,
                                sortState = sortState,
                                onDatasetClick = { uuid -> onResourceClick(uuid, datasetGroupBy.name) }
                            )
                        }
                    }
                }
            } // end Column
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
    currentPage: Int = 0,
    sampleGroupBy: SampleGroupBy = SampleGroupBy.TYPE,
    datasetGroupBy: DatasetGroupBy = DatasetGroupBy.MEASUREMENT,
    onSampleGroupByChange: (SampleGroupBy) -> Unit = {},
    onDatasetGroupByChange: (DatasetGroupBy) -> Unit = {},
    sortState: SortState = SortState(),
    onSortStateChange: (SortState) -> Unit = {},
    onUserClick: (String) -> Unit = {}) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

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
                        AppIcon(AppIcons.Project,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val nameScrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fadeEndEdge(nameScrollState.canScrollForward)
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
                            }
                            val lead = project?.lead
                            val org = project?.organization?.takeIf { it.isNotBlank() }
                            if (lead != null || org != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (lead != null) {
                                        val leadIdentifier = lead.username ?: lead.uniqueId
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = if (leadIdentifier != null) Modifier.clickable { onUserClick(leadIdentifier) } else Modifier
                                        ) {
                                            AppIcon(AppIcons.Person, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(ownerDisplayName(lead.firstName, lead.lastName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    if (org != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIcon(AppIcons.Business, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(org, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Creation / modification timestamps
                    val created = project?.createdAt?.let { dateGroupKey(it) }
                    val modified = project?.modifiedAt?.let { dateGroupKey(it) }
                    if (created != null || modified != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (created != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(AppIcons.CreationDate, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Created $created", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (modified != null && modified != created) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(AppIcons.ModificationDate, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Updated $modified", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        AppIcon(AppIcons.Pinned, filled = isPinned,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

            // Search field + group-by + sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(AppIcons.Search, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            AppIcon(AppIcons.ClearInput, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).clickable { onSearchChange("") })
                        }
                    }
                }
                Box {
                    IconButton(onClick = { groupMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        AppIcon(AppIcons.GroupBy, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Group by", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {}, enabled = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        )
                        if (currentPage == 0) {
                            SampleGroupBy.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (opt == sampleGroupBy) AppIcon(AppIcons.SelectionDot, modifier = Modifier.size(6.dp))
                                            else Spacer(modifier = Modifier.size(6.dp))
                                            Text(opt.label)
                                        }
                                    },
                                    onClick = { onSampleGroupByChange(opt); groupMenuExpanded = false },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            DatasetGroupBy.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (opt == datasetGroupBy) AppIcon(AppIcons.SelectionDot, modifier = Modifier.size(6.dp))
                                            else Spacer(modifier = Modifier.size(6.dp))
                                            Text(opt.label)
                                        }
                                    },
                                    onClick = { onDatasetGroupByChange(opt); groupMenuExpanded = false },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                        AppIcon(AppIcons.Sort, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Sort by", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {}, enabled = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        )
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (sortState.field == field) AppIcon(if (sortState.ascending) AppIcons.ParentResource else AppIcons.ChildResource, modifier = Modifier.size(14.dp))
                                        else Spacer(modifier = Modifier.size(14.dp))
                                        Text(field.label)
                                    }
                                },
                                onClick = {
                                    onSortStateChange(
                                        if (sortState.field == field) sortState.copy(ascending = !sortState.ascending)
                                        else SortState(field, true)
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
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
    sortState: SortState = SortState(),
    onSampleClick: (String) -> Unit,
    leadingContent: (LazyListScope.() -> Unit)? = null) {
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == SampleGroupBy.OWNER, projectId)
    if (samples.isEmpty()) {
        EmptyListCard(resourceName = "Samples", defaultIcon = AppIcons.Sample, isFiltered = isFiltered)
    } else if (!ownerNamesReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    } else {
        val groupedSamples = remember(samples, groupBy, ownerNames.toMap()) {
            if (groupBy == SampleGroupBy.NONE) emptyList()
            else samples.groupBy { s -> when (groupBy) {
                SampleGroupBy.NONE  -> ""
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
                leadingContent?.invoke(this)
                if (groupBy == SampleGroupBy.NONE) {
                    val sortedSamples = samples.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTime ?: "" })
                    items(sortedSamples, key = { it.uniqueId }) { sample ->
                            ResourceCard(title = sample.name, uniqueId = sample.uniqueId, icon = AppIcons.Sample, graphExplorerUrl = graphExplorerUrl, projectId = projectId, resourceType = "sample", onClick = { onSampleClick(sample.uniqueId) })
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                } else groupedSamples.forEach { (groupKey, samplesInGroup) ->
                    val expanded = expandedGroups[groupKey] == true
                    val sortedSamples = samplesInGroup.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTime ?: "" })
                    val displayedCount = displayedCounts[groupKey] ?: 50

                    stickyHeader(key = "header_sample_$groupKey") {
                        GroupStickyHeader(
                            title = groupKey,
                            count = samplesInGroup.size,
                            icon = AppIcons.Sample,
                            expanded = expanded,
                            onToggle = { expandedGroups[groupKey] = !expanded }
                        )
                    }

                    if (expanded) {
                        items(sortedSamples.take(displayedCount), key = { it.uniqueId }) { sample ->
                            ResourceCard(title = sample.name, uniqueId = sample.uniqueId, icon = AppIcons.Sample, graphExplorerUrl = graphExplorerUrl, projectId = projectId, resourceType = "sample", onClick = { onSampleClick(sample.uniqueId) })
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        }
                        loadMoreItem("sample", groupKey, displayedCount, sortedSamples.size) {
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
    sortState: SortState = SortState(),
    onDatasetClick: (String) -> Unit,
    leadingContent: (LazyListScope.() -> Unit)? = null) {
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == DatasetGroupBy.OWNER, projectId)
    if (datasets.isEmpty()) {
        EmptyListCard(resourceName = "Datasets", defaultIcon = AppIcons.Dataset, isFiltered = isFiltered)
    } else if (!ownerNamesReady) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
        }
    } else {
        val groupedDatasets = remember(datasets, groupBy, ownerNames.toMap()) {
            if (groupBy == DatasetGroupBy.NONE) emptyList()
            else datasets.groupBy { d -> when (groupBy) {
                DatasetGroupBy.NONE        -> ""
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
                leadingContent?.invoke(this)
                if (groupBy == DatasetGroupBy.NONE) {
                    val sortedDatasets = datasets.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTime ?: "" })
                    items(sortedDatasets, key = { it.uniqueId }) { dataset ->
                            ResourceCard(title = dataset.name, uniqueId = dataset.uniqueId, icon = AppIcons.Dataset, graphExplorerUrl = graphExplorerUrl, projectId = projectId, resourceType = "dataset", onClick = { onDatasetClick(dataset.uniqueId) })
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                } else groupedDatasets.forEach { (groupKey, datasetsInGroup) ->
                    val expanded = expandedGroups[groupKey] == true
                    val sortedDatasets = datasetsInGroup.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTime ?: "" })
                    val displayedCount = displayedCounts[groupKey] ?: 50

                    stickyHeader(key = "header_dataset_$groupKey") {
                        GroupStickyHeader(
                            title = groupKey,
                            count = datasetsInGroup.size,
                            icon = AppIcons.Dataset,
                            expanded = expanded,
                            onToggle = { expandedGroups[groupKey] = !expanded }
                        )
                    }

                    if (expanded) {
                        items(sortedDatasets.take(displayedCount), key = { it.uniqueId }) { dataset ->
                            ResourceCard(title = dataset.name, uniqueId = dataset.uniqueId, icon = AppIcons.Dataset, graphExplorerUrl = graphExplorerUrl, projectId = projectId, resourceType = "dataset", onClick = { onDatasetClick(dataset.uniqueId) })
                            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                        }
                        loadMoreItem("dataset", groupKey, displayedCount, sortedDatasets.size) {
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
    icon: AppIconToken,
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
                AppIcon(icon, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
            AppIcon(if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResourceCard(
    title: String,
    uniqueId: String,
    icon: AppIconToken,
    graphExplorerUrl: String = "",
    projectId: String? = null,
    resourceType: String = "sample",
    onClick: () -> Unit
) {
    val platformCtx = getPlatformContext()
    var menuExpanded by remember { mutableStateOf(false) }

    val webUrl = if (projectId != null && graphExplorerUrl.isNotBlank()) {
        if (resourceType == "dataset") "$graphExplorerUrl/$projectId/datasets/$uniqueId"
        else "$graphExplorerUrl/$projectId/samples/$uniqueId"
    } else null

    Box {
        ListItem(
            headlineContent = {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(uniqueId, style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingContent = { AppIcon(icon, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
        )

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            CopyIdMenuItem { menuExpanded = false; copyToClipboard(platformCtx, uniqueId) }
            if (webUrl != null) {
                OpenInWebMenuItem { menuExpanded = false; openUrl(platformCtx, webUrl) }
                ShareMenuItem { menuExpanded = false; shareText(platformCtx, webUrl, "") }
            }
        }
    } // end Box
}


