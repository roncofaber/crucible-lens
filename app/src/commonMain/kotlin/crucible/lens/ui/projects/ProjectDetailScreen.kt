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
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.sync.ProjectSyncTarget
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.SortState
import crucible.lens.data.util.matchesSearch
import crucible.lens.data.util.userDisplayName
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.buildCrucibleWebUrl
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openInBrowser
import crucible.lens.platform.shareText
import crucible.lens.platform.showToast
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.CollapsingAppTopBar
import crucible.lens.ui.common.ContentFastCrossfadeSpec
import crucible.lens.ui.common.CopyIdMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.IdText
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

// ProjectContent is defined in ProjectDetailViewModel.kt; the resource lists, their group-by enums
// and grouping logic live in ProjectResourceLists.kt.

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectDetailScreen(
    projectReference: String,
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
    currentUserOrcid: String? = null,
    accountId: String? = null
) {
    val repository = koinInject<CrucibleRepository>()
    val viewModel: ProjectDetailViewModel = koinViewModel()
    val joinRequestLookup by viewModel.joinRequestState.collectAsStateWithLifecycle()
    val joinRequestSubmission by viewModel.joinRequestSubmissionState.collectAsStateWithLifecycle()
    val project by repository.observeProject(projectReference)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProject(projectReference))
    val projectSlug = project?.projectId
    val syncTarget = project?.let { ProjectSyncTarget(it.uniqueId, it.projectId) }
    LaunchedEffect(projectReference) {
        repository.fetchProject(projectReference)
    }

    // Member list — fetched alongside the project so the collapsing header's member count is
    // ready without a dedicated round trip; also the shared cache rememberOwnerNames reads from
    // when grouping by owner, so opening this screen once warms that path too.
    val members by repository.observeProjectMembers(projectReference)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProjectMembers(projectReference))
    LaunchedEffect(projectReference) {
        if (repository.getCachedProjectMembers(projectReference) == null) {
            repository.fetchProjectMembers(projectReference)
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
    val memberProjects by repository.observeProjects()
        .collectAsStateWithLifecycle(initialValue = repository.getCachedProjects())
    val isConfidentlyNonMember = memberProjects != null && memberProjects!!.none {
        it.uniqueId == projectReference || it.projectId == projectReference
    }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(joinRequestSubmission) {
        if (joinRequestSubmission is JoinRequestSubmissionState.Submitted) {
            showJoinDialog = false
            viewModel.clearJoinRequestSubmission()
        }
    }
    LaunchedEffect(projectSlug, accountId, isSynced, isConfidentlyNonMember) {
        if (isConfidentlyNonMember && projectSlug != null) viewModel.loadJoinRequestStatus(projectSlug)
        else viewModel.clearJoinRequestStatus()
    }

    // Pending-request dot on the overflow menu, for the project's lead only — same
    // ORCID-based lead check ManageProjectViewModel uses (project.lead is member-only-populated,
    // projectLeadOrcid always is). The count itself comes from CrucibleRepository's cache,
    // populated by DataSyncManager's background sync (see dev/architecture.md); no fetch is
    // triggered from here, this only renders whatever's already known.
    val isCurrentUserLead = currentUserOrcid != null &&
        (project?.projectLeadOrcid == currentUserOrcid || project?.lead?.uniqueId == currentUserOrcid)
    val pendingCountKey = projectSlug ?: projectReference
    val pendingRequestCount by repository.observePendingJoinRequestCount(pendingCountKey)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedPendingJoinRequestCount(pendingCountKey))
    val leadPendingRequestCount = if (isCurrentUserLead) pendingRequestCount else null

    val ctx = getPlatformContext()
    val projectWebUrl = buildCrucibleWebUrl(graphExplorerUrl, projectSlug ?: projectReference)
    val prefs = koinInject<AppPreferences>()

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
        syncTarget?.let { target ->
            viewModel.load(target, ctx, accountId, isSynced, forceRefresh = true)
            val slug = target.projectSlug
            if (isConfidentlyNonMember) viewModel.loadJoinRequestStatus(slug, forceRefresh = true)
        }
        // load() only force-refreshes samples/datasets. The collapsing header reads the project
        // and its member list from two other caches, so without these a pull-to-refresh left the
        // title, organization, lead and member count stale until their TTL lapsed.
        scope.launch {
            try { repository.fetchProject(projectReference, forceRefresh = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        scope.launch {
            try { repository.fetchProjectMembers(projectReference, forceRefresh = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
        if (isCurrentUserLead && projectSlug != null) {
            scope.launch {
                try { repository.fetchPendingJoinRequestCounts(listOf(projectSlug)) }
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
    LaunchedEffect(syncTarget, accountId, isSynced, isConfidentlyNonMember) {
        if (!isConfidentlyNonMember && syncTarget != null) viewModel.load(syncTarget, ctx, accountId, isSynced)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    AppScaffold(
        topBar = {
            CollapsingAppTopBar(
                name = project?.title ?: projectSlug ?: projectReference,
                icon = AppIcons.Project,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onTitleClick = onManageProject,
                expandedContent = {
                    val lead = project?.lead
                    val org = project?.organization?.takeIf { it.isNotBlank() }
                    val memberCount = members?.size
                    // Byline: lead + org on one line, matching GitHub's "by owner · org" pattern
                    // rather than each getting its own centered row. The lead is the only tappable
                    // part of this line, so it alone stays primary - org shares the block's plain
                    // onSecondaryContainer, hierarchy between this and the line below is carried by
                    // type scale (bodyMedium here, bodySmall below), not by introducing more colours.
                    if (lead != null || org != null) {
                        val leadIdentifier = lead?.let { it.username ?: it.uniqueId }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (lead != null) {
                                Text(
                                    userDisplayName(lead),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = if (leadIdentifier != null) Modifier.clickable { onUserClick(leadIdentifier) } else Modifier
                                )
                            }
                            if (lead != null && org != null) {
                                Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            if (org != null) {
                                Text(org, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    if (memberCount != null || !isSynced) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (memberCount != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.Team, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(if (memberCount == 1) "1 member" else "$memberCount members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            if (!isSynced) {
                                if (memberCount != null) {
                                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(AppIcons.SyncPaused, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("Not syncing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                    // Least essential identity field, so it's last - the shared IdText style
                    // (monospace) used by every other machine ID in the app, not the sans-serif
                    // prose used for the byline/member-count above. Coloured onSecondaryContainer
                    // to match this header rather than IdText's default onSurfaceVariant, which
                    // only pairs safely with the `surface` family. Tap to copy, matching
                    // InstrumentDetailScreen's overflow-menu Copy ID action for the same purpose.
                    IdText(
                        text = projectSlug ?: projectReference,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.clickable { copyToClipboard(ctx, projectSlug ?: projectReference) }
                    )
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
                                enabled = projectSlug != null,
                                onClick = { topBarMenuExpanded = false; onCreateSample() }
                            )
                            DropdownMenuItem(
                                text = { Text("New Dataset") },
                                leadingIcon = { AppIcon(AppIcons.Dataset) },
                                enabled = projectSlug != null,
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
                                enabled = projectSlug != null,
                                onClick = { topBarMenuExpanded = false; onToggleSync() }
                            )
                            CopyIdMenuItem { topBarMenuExpanded = false; copyToClipboard(ctx, projectSlug ?: projectReference) }
                            OpenInWebMenuItem { topBarMenuExpanded = false; openInBrowser(ctx, projectWebUrl) }
                            ShareMenuItem { topBarMenuExpanded = false; shareText(ctx, projectWebUrl, project?.title ?: projectSlug ?: projectReference) }
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
                    isPending = (joinRequestLookup as? ProjectJoinRequestState.Ready)?.request != null,
                    isChecking = joinRequestLookup is ProjectJoinRequestState.Idle || joinRequestLookup is ProjectJoinRequestState.Loading,
                    errorMessage = (joinRequestLookup as? ProjectJoinRequestState.Error)?.message,
                    refreshError = (joinRequestLookup as? ProjectJoinRequestState.Ready)?.refreshError,
                    onRetry = { projectSlug?.let { viewModel.loadJoinRequestStatus(it, forceRefresh = true) } },
                    onRequestJoin = {
                        viewModel.clearJoinRequestSubmission()
                        showJoinDialog = true
                    },
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
                                projectId = projectSlug ?: projectReference,
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
                                projectId = projectSlug ?: projectReference,
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
            submissionState = joinRequestSubmission,
            onDismiss = {
                viewModel.clearJoinRequestSubmission()
                showJoinDialog = false
            },
            onSubmit = { reason -> projectSlug?.let { viewModel.submitJoinRequest(it, reason) } }
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
    errorMessage: String?,
    refreshError: String?,
    onRetry: () -> Unit,
    onRequestJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isChecking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            return@Box
        }
        if (errorMessage != null) {
            ErrorCard(
                title = "Could not check join request status",
                message = errorMessage,
                onRetry = onRetry,
                modifier = Modifier.padding(32.dp)
            )
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "You're not a member of this project",
                style = MaterialTheme.typography.titleMedium,
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
            if (refreshError != null) {
                Text(
                    refreshError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun JoinRequestDialog(
    submissionState: JoinRequestSubmissionState,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val isSubmitting = submissionState is JoinRequestSubmissionState.Submitting
    val errorMessage = (submissionState as? JoinRequestSubmissionState.Error)?.message

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.PersonAdd)
                Text("Request to join")
            }
        },
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
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(reason) },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}
