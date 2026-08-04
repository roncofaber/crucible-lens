@file:OptIn(ExperimentalMaterial3Api::class)

package crucible.lens.ui.projects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.SortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.data.util.userDisplayName
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import crucible.lens.platform.showToast
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.CollapsingAppTopBar
import crucible.lens.ui.common.ContentFastCrossfadeSpec
import crucible.lens.ui.common.GroupByOption
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.NotificationDot
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.RefreshMenuItem
import crucible.lens.ui.common.ResourceControlsBar
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.TabSwitchSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import crucible.lens.ui.theme.emphasizedTitleMedium

// ProjectContent is defined in ProjectDetailViewModel.kt; the resource lists, their group-by enums
// and grouping logic live in ProjectResourceLists.kt.

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (uuid: String, groupBy: String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    isSynced: Boolean = false,
    onToggleSync: () -> Unit = {},
    onCreateSample: () -> Unit = {},
    onCreateDataset: () -> Unit = {},
    onManageProject: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
    currentUserOrcid: String? = null
) {
    val repository = koinInject<CrucibleRepository>()
    // The projects-list cache (warm by the time a project can be opened at all, from Home/Projects
    // list) and the per-project cache (populated by DataSyncManager's background sync or this
    // screen's own fetchProject() call below) are the same CrucibleRepository cache, but a project
    // reached here right after the list fetch — before fetchProject() below has run — may only be
    // in the list cache yet. Falling back to it restores an instant header render for that case
    // instead of a blank header + an avoidable network round-trip.
    val cachedFallback = remember(projectId) { repository.getCachedProjects()?.find { it.projectId == projectId } }
    // Observed reactively so the header updates in place once fetched — covers both member
    // projects (usually warm already from the Projects list fetch) and non-member projects
    // reached via discover-search (never in that list, so this is a cold single fetch).
    val project by repository.observeProject(projectId)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProject(projectId) ?: cachedFallback)
    LaunchedEffect(projectId) {
        if (repository.getCachedProject(projectId) == null) {
            repository.fetchProject(projectId)
        }
    }

    // Member list — fetched alongside the project so the collapsing header's member count is
    // ready without a dedicated round trip; also the shared cache rememberOwnerNames reads from
    // when grouping by owner, so opening this screen once warms that path too.
    val members by repository.observeProjectMembers(projectId)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProjectMembers(projectId))
    LaunchedEffect(projectId) {
        if (repository.getCachedProjectMembers(projectId) == null) {
            repository.fetchProjectMembers(projectId)
        }
    }

    // Membership check for the join-request banner. null = not loaded yet (treated as
    // "assume member" to avoid flashing the banner before the list arrives) — mirrors the
    // "confidently non-member" condition already used in SearchScreen's discover toggle.
    // Seeded from the cache, like observeProject/observeProjectMembers above, so membership is
    // known on the first frame whenever the member list is already warm. That both removes a
    // flash of empty Samples/Datasets tabs before the non-member notice appears, and lets the
    // content load below be skipped outright for non-members. A stale cache can only mislabel
    // someone added to the project since the last list fetch, and the observe corrects that as
    // soon as fresh data lands.
    val apiClient = koinInject<ApiClient>()
    val memberProjects by repository.observeProjects()
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProjects())
    val isConfidentlyNonMember = memberProjects != null && memberProjects!!.none { it.projectId == projectId }
    var joinRequestState by remember(projectId) { mutableStateOf<JoinRequest?>(null) }
    var joinRequestChecked by remember(projectId) { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    LaunchedEffect(projectId, isConfidentlyNonMember) {
        if (!isConfidentlyNonMember) { joinRequestChecked = false; return@LaunchedEffect }
        joinRequestState = (apiClient.service.getMyJoinRequests(status = "pending") as? ApiResult.Success)?.data
            ?.find { it.groupName == projectId }
        joinRequestChecked = true
    }

    // Pending-request dot on the overflow menu, for the project's lead only — same
    // ORCID-based lead check ManageProjectViewModel uses (project.lead is member-only-populated,
    // projectLeadOrcid always is). The count itself comes from CrucibleRepository's cache,
    // populated by DataSyncManager's background sync (see dev/architecture.md); no fetch is
    // triggered from here, this only renders whatever's already known.
    val isCurrentUserLead = currentUserOrcid != null &&
        (project?.projectLeadOrcid == currentUserOrcid || project?.lead?.uniqueId == currentUserOrcid)
    val pendingRequestCount by repository.observePendingJoinRequestCount(projectId)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedPendingJoinRequestCount(projectId))
    val leadPendingRequestCount = if (isCurrentUserLead) pendingRequestCount else null

    val ctx = getPlatformContext()
    val prefs = koinInject<AppPreferences>()

    val viewModel: ProjectDetailViewModel = koinViewModel()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sampleGroupBy by remember { mutableStateOf(SampleGroupBy.TYPE) }
    var datasetGroupBy by remember { mutableStateOf(DatasetGroupBy.MEASUREMENT) }
    var sortState by remember { mutableStateOf(SortState()) }
    val scope = rememberCoroutineScope()

    // The underlying API call has no group_name filter — it always returns every project this
    // user leads, not just this one — so this is as cheap as ProjectsListScreen's refresh, just
    // scoped here to write/zero this project's cache entry.
    fun refreshProjectDetail() {
        viewModel.load(projectId, forceRefresh = true)
        // load() only force-refreshes samples/datasets. The collapsing header reads the project
        // and its member list from two other caches, so without these a pull-to-refresh left the
        // title, organization, lead and member count stale until their TTL lapsed.
        scope.launch {
            try { repository.fetchProject(projectId, forceRefresh = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        scope.launch {
            try { repository.fetchProjectMembers(projectId, forceRefresh = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        if (isCurrentUserLead) {
            scope.launch {
                try { repository.fetchPendingJoinRequestCounts(listOf(projectId)) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
            }
        }
    }

    // Load persisted group-by choices and default tab on first composition. valueOf is guarded
    // because a stored name can outlive its enum entry across an app update, and an unguarded
    // valueOf would throw out of this effect rather than fall back.
    LaunchedEffect(Unit) {
        sampleGroupBy = runCatching { SampleGroupBy.valueOf(prefs.sampleGroupBy.first()) }
            .getOrDefault(SampleGroupBy.TYPE)
        datasetGroupBy = runCatching { DatasetGroupBy.valueOf(prefs.datasetGroupBy.first()) }
            .getOrDefault(DatasetGroupBy.MEASUREMENT)
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

    // Skipped for non-members: the list endpoints filter everything out for them anyway, and the
    // content area is replaced by NonMemberContent. Guarded rather than unconditional so a warm
    // member-list cache (the usual case, per the seed above) avoids the round trip entirely; on a
    // cold cache isConfidentlyNonMember is false on the first frame, so members never wait.
    LaunchedEffect(projectId, isConfidentlyNonMember) {
        if (!isConfidentlyNonMember) viewModel.load(projectId)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        topBar = {
            CollapsingAppTopBar(
                name = project?.title ?: projectId,
                icon = AppIcons.Project,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onTitleClick = onManageProject,
                expandedContent = {
                    val lead = project?.lead
                    val org = project?.organization?.takeIf { it.isNotBlank() }
                    val memberCount = members?.size
                    if (lead != null) {
                        val leadIdentifier = lead.username ?: lead.uniqueId
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (leadIdentifier != null) Modifier.clickable { onUserClick(leadIdentifier) } else Modifier
                        ) {
                            // The lead outranks organization/member-count, so it takes bodyMedium
                            // on onSurface while they stay bodySmall/onSurfaceVariant. The icon
                            // keeps the primary tint: it is now the only thing marking this row as
                            // tappable, since the name itself is no longer accent-coloured.
                            AppIcon(AppIcons.Person, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(userDisplayName(lead), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (org != null || memberCount != null || !isSynced) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (org != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.Business, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(org, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (org != null && memberCount != null) {
                                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (memberCount != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.Team, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (memberCount == 1) "1 member" else "$memberCount members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (!isSynced) {
                                if (org != null || memberCount != null) {
                                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.SyncPaused, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Not syncing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onTogglePin) {
                        AppIcon(AppIcons.Pinned, filled = isPinned, tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    var topBarMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { topBarMenuExpanded = true }) {
                            NotificationDot(count = leadPendingRequestCount) {
                                AppIcon(AppIcons.MoreVert)
                            }
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
                                leadingIcon = {
                                    NotificationDot(count = leadPendingRequestCount) {
                                        AppIcon(AppIcons.ManageMembers)
                                    }
                                },
                                onClick = { topBarMenuExpanded = false; onManageProject() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSynced) "Stop syncing" else "Sync this project") },
                                leadingIcon = { AppIcon(if (isSynced) AppIcons.SyncPaused else AppIcons.Syncing) },
                                onClick = { topBarMenuExpanded = false; onToggleSync() }
                            )
                            OpenInWebMenuItem { topBarMenuExpanded = false; openUrl(ctx, "$graphExplorerUrl/$projectId") }
                            ShareMenuItem { topBarMenuExpanded = false; shareText(ctx, "$graphExplorerUrl/$projectId", project?.title ?: projectId) }
                            HorizontalDivider()
                            RefreshMenuItem { topBarMenuExpanded = false; refreshProjectDetail() }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loadState.isRefreshingNow,
            onRefresh = { refreshProjectDetail() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isConfidentlyNonMember) {
                // Non-members never see actual samples/datasets (the list endpoints silently
                // filter to public/ACL-visible resources), so the normal tabs+pager would just
                // show a misleading "No Samples"/"No Datasets" empty state. Replace the whole
                // content area with an explicit not-a-member message and the join action instead.
                NonMemberContent(
                    isPending = joinRequestState != null,
                    isChecking = !joinRequestChecked,
                    onRequestJoin = { showJoinDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // The nestedScroll connection is attached here — on a child *inside*
                // PullToRefreshBox's own content, not on AppScaffold/PullToRefreshBox's own
                // modifier — so it's unambiguously closer to the scrolling list than
                // PullToRefreshBox's own connection. Nested scroll dispatches to the nearest
                // ancestor first, so this gets first refusal on a downward drag at the top of the
                // list (re-expanding the collapsed bar) before PullToRefreshBox's own connection
                // further out ever sees it and starts the refresh gesture instead.
                Column(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                        ResourceControlsBar(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            searchPlaceholder = "Search samples and datasets…",
                            groupOptions = if (pagerState.currentPage == 0) {
                                SampleGroupBy.entries.map { opt ->
                                    GroupByOption(opt.label, opt == sampleGroupBy) {
                                        sampleGroupBy = opt
                                        scope.launch { prefs.saveSampleGroupBy(opt.name) }
                                        showToast(ctx, "Grouped by ${opt.label}")
                                    }
                                }
                            } else {
                                DatasetGroupBy.entries.map { opt ->
                                    GroupByOption(opt.label, opt == datasetGroupBy) {
                                        datasetGroupBy = opt
                                        scope.launch { prefs.saveDatasetGroupBy(opt.name) }
                                        showToast(ctx, "Grouped by ${opt.label}")
                                    }
                                }
                            },
                            sortState = sortState,
                            onSortStateChange = { sortState = it; showToast(ctx, "Sorted by ${it.field.label} ${if (it.ascending) "↑" else "↓"}") },
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                            ResourceTab(
                                selected = pagerState.currentPage == 0,
                                label = tabLabel("Samples", filteredSamples.size, samples.size, searchQuery),
                                icon = AppIcons.Sample,
                                contentDescription = "samples_tab_label",
                                onClick = { scope.launch { pagerState.animateScrollToPage(0, animationSpec = TabSwitchSpec) } }
                            )
                            ResourceTab(
                                selected = pagerState.currentPage == 1,
                                label = tabLabel("Datasets", filteredDatasets.size, datasets.size, searchQuery),
                                icon = AppIcons.Dataset,
                                contentDescription = "datasets_tab_label",
                                onClick = { scope.launch { pagerState.animateScrollToPage(1, animationSpec = TabSwitchSpec) } }
                            )
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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
                                onSampleClick = { uuid -> onResourceClick(uuid, sampleGroupBy.name) },
                                loadState = loadState,
                                onRetry = { refreshProjectDetail() }
                            )
                            1 -> DatasetsList(
                                datasets = filteredDatasets,
                                isFiltered = searchQuery.isNotBlank(),
                                fromCache = (loadState as? LoadState.Success)?.fromCache ?: false,
                                projectId = projectId,
                                graphExplorerUrl = graphExplorerUrl,
                                groupBy = datasetGroupBy,
                                sortState = sortState,
                                onDatasetClick = { uuid -> onResourceClick(uuid, datasetGroupBy.name) },
                                loadState = loadState,
                                onRetry = { refreshProjectDetail() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        JoinRequestDialog(
            projectId = projectId,
            onDismiss = { showJoinDialog = false },
            onSubmitted = { request -> showJoinDialog = false; joinRequestState = request }
        )
    }
}

private fun tabLabel(name: String, filtered: Int, total: Int, searchQuery: String): String =
    if (searchQuery.isBlank()) "$name ($total)" else "$name ($filtered/$total)"

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ResourceTab(
    selected: Boolean,
    label: String,
    icon: AppIconToken,
    contentDescription: String,
    onClick: () -> Unit
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(ContentFastCrossfadeSpec) togetherWith fadeOut(ContentFastCrossfadeSpec) },
                label = contentDescription
            ) { Text(it) }
        },
        icon = { AppIcon(icon) }
    )
}

@Composable
private fun NonMemberContent(
    isPending: Boolean,
    isChecking: Boolean,
    onRequestJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isChecking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            return@Box
        }
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                if (isPending) AppIcons.Pending else AppIcons.PersonAdd,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "You're not a member of this project",
                style = MaterialTheme.typography.emphasizedTitleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Samples and datasets are only visible to members.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isPending) {
                Text(
                    text = "Join request pending",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequestJoin) {
                    Text("Request to join")
                }
            }
        }
    }
}

@Composable
private fun JoinRequestDialog(
    projectId: String,
    onDismiss: () -> Unit,
    onSubmitted: (JoinRequest) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val apiClient = koinInject<ApiClient>()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIcon(AppIcons.PersonAdd) },
        title = { Text("Request to join") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Ask the project lead to add you as a member.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                if (errorMsg != null) {
                    Text(
                        errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isSubmitting = true
                        errorMsg = null
                        try {
                            val resp = apiClient.service.requestToJoinProject(
                                projectId = projectId,
                                reason = reason.trim().ifBlank { null }
                            )
                            when (resp) {
                                is ApiResult.Success -> onSubmitted(resp.data)
                                is ApiResult.Error -> errorMsg = when (resp.code) {
                                    409 -> "You already have a pending request, or are already a member"
                                    else -> "Failed (${resp.code})"
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMsg = "Network error: ${e.message}"
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
