@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.EffectsDefaultSpring
import crucible.lens.ui.common.SpatialDefaultSizeSpring
import crucible.lens.ui.common.SpatialFastSizeSpring
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import crucible.lens.ui.metadata.MetadataHolder
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.QrCodeDialog
import crucible.lens.ui.common.QrCodeDialogWithNavigation
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.detail.components.*
import org.koin.compose.koinInject

private data class UnlinkRequest(val name: String, val otherUuid: String, val action: suspend () -> Unit)

private fun hasLinks(resource: CrucibleResource): Boolean = when (resource) {
    is Sample -> resource.links != null
    is Dataset -> resource.links != null
}

private fun siblingGroupLabel(groupBy: String?, resource: CrucibleResource): String = when (groupBy) {
    "MEASUREMENT" -> "Measurement"
    "INSTRUMENT"  -> "Instrument"
    "DATE"        -> "Date"
    "FORMAT"      -> "Format"
    "SESSION"     -> "Session"
    "OWNER"       -> "Owner"
    "TYPE"        -> "Type"
    null -> when (resource) { is Sample -> "Type"; else -> "Measurement" }
    else -> groupBy.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourceDetailScreen(
    uuid: String,
    graphExplorerUrl: String,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    siblingGroupBy: String? = null,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onNavigateToInstrument: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onHome: () -> Unit,
    onRefresh: (uuid: String) -> Unit,
    onDuplicate: (CrucibleResource) -> Unit = {},
    recentHistory: List<crucible.lens.data.preferences.HistoryItem> = emptyList(),
    onSaveToHistory: (uuid: String, name: String, resourceType: String?) -> Unit = { _, _, _ -> },
    getCardState: (key: String) -> Boolean = { false },
    onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
    onNavigateToAddFiles: (datasetUuid: String) -> Unit = {},
    onNavigateToMetadataEditor: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val apiClient = koinInject<ApiClient>()
    val repository = koinInject<CrucibleRepository>()
    var showQrDialog by remember { mutableStateOf(false) }
    var showSiblingGroupDialog by remember { mutableStateOf(false) }
    // Local groupBy that can be changed while browsing; starts from the nav argument.
    var activeSiblingGroupBy by remember { mutableStateOf(siblingGroupBy) }

    // The primary resource this screen was navigated to. Observed reactively from the
    // repository — NavGraph's LaunchedEffect(mfid) { viewModel.fetchResource(mfid) } drives
    // the actual fetch; this just renders whatever the repository currently has cached for
    // this uuid, updating automatically as fresher data arrives (cache placeholder -> full
    // network result), with NO object-identity-keyed state anywhere reacting to that change.
    val resource by repository.observeResource(uuid)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedResource(uuid))

    // Sibling navigation: same type within the same project. Resolved via the repository
    // (cache-then-network), re-resolved when the resource's project changes or the user
    // changes groupBy. Keyed on (projectId, groupBy) — stable strings, never the resource
    // object — so this never resets due to the resource being enriched in place.
    var siblingList by remember { mutableStateOf<List<CrucibleResource>>(emptyList()) }
    var siblingsResolved by remember { mutableStateOf(false) }

    val currentProjectId = resource.let { r ->
        when (r) {
            is Sample -> r.projectId
            is Dataset -> r.projectId
            null -> null
        }
    }

    LaunchedEffect(uuid, currentProjectId, activeSiblingGroupBy) {
        val r = resource
        if (r == null) return@LaunchedEffect
        siblingsResolved = false
        siblingList = listOf(r)
        try {
            siblingList = repository.fetchSiblings(r, activeSiblingGroupBy)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            siblingList = listOf(r)
        } finally {
            siblingsResolved = true
        }
    }

    val siblingIndex = remember(siblingList, uuid) {
        siblingList.indexOfFirst { it.uniqueId == uuid }
    }

    // HorizontalPager state. initialPage uses siblingIndex directly so the pager
    // opens at the right position without a post-composition scroll (cold-start
    // fallback below handles the rare case where siblingList isn't ready yet).
    val pagerState = rememberPagerState(
        initialPage = siblingIndex.coerceAtLeast(0),
        pageCount = { siblingList.size.coerceAtLeast(1) }
    )

    // Scroll to the resource's position once siblings are resolved and pageCount is updated.
    // Runs after recomposition so pageCount reflects the full sibling list size.
    LaunchedEffect(siblingIndex, siblingsResolved) {
        if (siblingsResolved && siblingIndex > 0 && pagerState.currentPage != siblingIndex) {
            pagerState.scrollToPage(siblingIndex)
        }
    }

    // Current resource shown in the pager — drives TopAppBar title and overflow menu
    val currentPageUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId ?: uuid
    val currentDisplayResource: CrucibleResource? by repository.observeResource(currentPageUuid)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedResource(currentPageUuid))

    // Screen-level sheet/dialog state — operate on currentDisplayResource
    var showEditSheet by remember { mutableStateOf(false) }
    var editSheetPendingMetadata by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var editSheetWaitingForMetadata by remember { mutableStateOf(false) }
    var showLinkSheet by remember { mutableStateOf(false) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var pendingUnlink by remember { mutableStateOf<UnlinkRequest?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    // Track history and last-viewed sibling continuously as user scrolls through pages
    LaunchedEffect(pagerState.currentPage, pagerState.targetPage) {
        val targetPage = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        val targetResource = siblingList.getOrNull(targetPage)
        if (targetResource != null) {
            val rtype = if (targetResource is Sample) "sample" else "dataset"
            onSaveToHistory(targetResource.uniqueId, targetResource.name, rtype)
        }
    }

    // Re-open edit sheet with updated metadata after returning from MetadataEditorScreen.
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty && editSheetWaitingForMetadata) {
            editSheetPendingMetadata = MetadataHolder.take()
            editSheetWaitingForMetadata = false
            showEditSheet = true
        }
    }

    val scope = rememberCoroutineScope()
    val platformContext = getPlatformContext()
    val isDarkTheme = isSystemInDarkTheme()
    val primaryColorValue = MaterialTheme.colorScheme.primary.value.toLong()

    // True only when a sibling (not the primary resource) was pull-to-refreshed.
    var localRefreshState by remember { mutableStateOf(false) }

    fun triggerRefresh() {
        val currentUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId ?: uuid
        if (currentUuid != uuid) {
            // Sibling refresh: fetch inline without ViewModel involvement. The fresh result
            // lands in the repository's cache; that page's own observeResource collection
            // picks it up automatically — no manual map write, no reload-trigger counter.
            scope.launch {
                localRefreshState = true
                try {
                    repository.invalidateResource(currentUuid)
                    repository.fetchResourceByUuid(currentUuid)
                } finally {
                    localRefreshState = false
                }
            }
        } else {
            onRefresh(currentUuid)
        }
    }

    // Primary resource refresh only — sibling PTR is handled inline above.
    LaunchedEffect(isRefreshing) {
        localRefreshState = isRefreshing
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = if (resource is Sample) "Sample" else "Dataset",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            val displayForMenu = currentDisplayResource
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { AppIcon(AppIcons.Edit) },
                                onClick = { overflowMenuExpanded = false; showEditSheet = true },
                                enabled = displayForMenu != null
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { AppIcon(AppIcons.CopyResource) },
                                onClick = { displayForMenu?.let { overflowMenuExpanded = false; onDuplicate(it) } },
                                enabled = displayForMenu != null
                            )
                            if (displayForMenu is Dataset) {
                                DropdownMenuItem(
                                    text = { Text("Add file") },
                                    leadingIcon = { AppIcon(AppIcons.AttachFile) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        onNavigateToAddFiles(displayForMenu.uniqueId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Link") },
                                leadingIcon = { AppIcon(AppIcons.LinkResource) },
                                onClick = { overflowMenuExpanded = false; showLinkSheet = true },
                                enabled = displayForMenu != null
                            )
                            val deletionRequest = when (displayForMenu) {
                                is Sample -> displayForMenu.deletionRequest
                                is Dataset -> displayForMenu.deletionRequest
                                null -> null
                            }
                            val deletionStatus = (deletionRequest?.get("status") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            DropdownMenuItem(
                                text = { Text(if (deletionStatus != null) "Deletion ${deletionStatus.replaceFirstChar { it.uppercase() }}" else "Request deletion") },
                                leadingIcon = { AppIcon(AppIcons.RequestDeletion) },
                                enabled = displayForMenu != null && deletionStatus == null,
                                onClick = { overflowMenuExpanded = false; showDeletionDialog = true }
                            )
                            val projectId = when (displayForMenu) {
                                is Sample -> displayForMenu.projectId
                                is Dataset -> displayForMenu.projectId
                                null -> null
                            }
                            if (displayForMenu != null && projectId != null && graphExplorerUrl.isNotBlank()) {
                                val webUrl = when (displayForMenu) {
                                    is Sample  -> "$graphExplorerUrl/$projectId/samples/${displayForMenu.uniqueId}"
                                    is Dataset -> "$graphExplorerUrl/$projectId/datasets/${displayForMenu.uniqueId}"
                                }
                                OpenInWebMenuItem { overflowMenuExpanded = false; openUrl(platformContext, webUrl) }
                                ShareMenuItem {
                                    overflowMenuExpanded = false
                                    shareResource(
                                        context = platformContext,
                                        resource = displayForMenu,
                                        shareText = webUrl,
                                        subject = displayForMenu.name,
                                        darkTheme = isDarkTheme,
                                        bannerColorValue = primaryColorValue
                                    )
                                }
                            }
                            // Sibling grouping — only when resource belongs to a project
                            if (displayForMenu != null && projectId != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                val groupLabel = siblingGroupLabel(activeSiblingGroupBy, displayForMenu)
                                DropdownMenuItem(
                                    text = { Text("Siblings: $groupLabel") },
                                    leadingIcon = { AppIcon(AppIcons.SwapResource) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        showSiblingGroupDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = localRefreshState,
            onRefresh = { triggerRefresh() },
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (siblingList.isNotEmpty() && siblingIndex >= 0) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { pageIndex ->
                    if (pageIndex >= siblingList.size) return@HorizontalPager
                    val pageResource = siblingList[pageIndex]
                    val pageUuid = pageResource.uniqueId

                    key(pageUuid) {
                        // Each page independently observes its own resource and (for datasets)
                        // thumbnails from the repository. Any fetch anywhere in the app that
                        // updates this uuid's cache entry is picked up automatically here.
                        val displayResource by repository.observeResource(pageUuid)
                            .collectAsStateWithLifecycle(initialValue = repository.getCachedResource(pageUuid) ?: pageResource)
                        val isEnriched = displayResource?.let { hasLinks(it) } == true
                        var enrichmentFailed by remember { mutableStateOf(false) }

                        LaunchedEffect(pageUuid, isEnriched) {
                            if (!isEnriched) {
                                when (repository.fetchResourceByUuid(pageUuid)) {
                                    is ResourceResult.Error -> enrichmentFailed = true
                                    is ResourceResult.Success -> enrichmentFailed = false
                                    is ResourceResult.Loading -> {}
                                }
                            }
                        }

                        // Always observed, regardless of resource type — harmless for samples
                        // since nothing ever populates a thumbnail cache entry for a sample uuid,
                        // so this simply stays null. Avoids an if/else branch with two different
                        // State<List<Thumbnail>?> producers (collectAsStateWithLifecycle vs a
                        // plain remembered MutableState), which is unnecessary complexity for a
                        // case that's just as correct handled uniformly.
                        val displayThumbnails by repository.observeThumbnails(pageUuid)
                            .collectAsStateWithLifecycle(initialValue = null)
                        LaunchedEffect(pageUuid, isEnriched) {
                            if (isEnriched && pageResource is Dataset && displayThumbnails == null) {
                                repository.fetchThumbnails(pageUuid)
                            }
                        }
                        val resolvedThumbnails = displayThumbnails ?: emptyList()

                        // Each page needs its own independent scroll state
                        val scrollState = rememberScrollState()
                        val showScrollToTop by remember { derivedStateOf { scrollState.value > 300 } }
                        val pageGetCardState: (String) -> Boolean = { k -> getCardState("$pageUuid/$k") }
                        val pageSetCardState: (String, Boolean) -> Unit = { k, value -> onCardStateChange("$pageUuid/$k", value) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = !isEnriched && !isRefreshing,
                                enter = fadeIn(animationSpec = EffectsDefaultSpring),
                                exit = fadeOut(animationSpec = EffectsDefaultSpring)
                            ) {
                                LoadingContent(title = "Loading Resource")
                            }

                            AnimatedVisibility(
                                visible = isEnriched,
                                enter = fadeIn(animationSpec = EffectsFastSpring),
                                exit = fadeOut(animationSpec = EffectsFastSpring)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(16.dp)
                                ) {
                                    val hasPrev = pageIndex > 0
                                    val hasNext = pageIndex < siblingList.size - 1

                                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                        BasicInfoCard(
                                            resource = displayResource ?: pageResource,
                                            onPrev = if (hasPrev) {
                                                { scope.launch { pagerState.animateScrollToPage(pageIndex - 1) } }
                                            } else null,
                                            onNext = if (hasNext) {
                                                { scope.launch { pagerState.animateScrollToPage(pageIndex + 1) } }
                                            } else null,
                                            currentIndex = pageIndex,
                                            totalCount = siblingList.size,
                                            siblingsResolved = siblingsResolved
                                        )
                                    }

                                    val resolved = displayResource ?: pageResource

                                    AnimatedVisibility(
                                        visible = enrichmentFailed,
                                        enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                        exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                    ) {
                                        ErrorCard(
                                            title = "Could not load full data",
                                            message = "Links and metadata may be incomplete.",
                                            modifier = Modifier.padding(bottom = 16.dp),
                                            onRetry = { enrichmentFailed = false }
                                        )
                                    }

                                    val displayDeletionRequest = when (resolved) {
                                        is Sample -> resolved.deletionRequest
                                        is Dataset -> resolved.deletionRequest
                                    }
                                    if (displayDeletionRequest != null) {
                                        val delStatus = (displayDeletionRequest["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "pending"
                                        val delReason = (displayDeletionRequest["reason"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.ifBlank { null }
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppIcon(AppIcons.RequestDeletion, tint = MaterialTheme.colorScheme.onErrorContainer)
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        "Deletion ${delStatus.replaceFirstChar { it.uppercase() }}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    if (delReason != null) {
                                                        Text(delReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    when (resolved) {
                                        is Sample -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                            SampleDetailsCard(
                                                sample = resolved,
                                                onProjectClick = onNavigateToProject,
                                                onUserClick = onNavigateToUser,
                                                onShowQr = { showQrDialog = true },
                                                initialAdvanced = pageGetCardState("advanced"),
                                                onAdvancedChange = { pageSetCardState("advanced", it) }
                                            )
                                        }
                                        is Dataset -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                            DatasetDetailsCard(
                                                dataset = resolved,
                                                onProjectClick = onNavigateToProject,
                                                onUserClick = onNavigateToUser,
                                                onInstrumentClick = onNavigateToInstrument,
                                                onShowQr = { showQrDialog = true },
                                                initialAdvanced = pageGetCardState("advanced"),
                                                onAdvancedChange = { pageSetCardState("advanced", it) }
                                            )
                                        }
                                    }

                                    when (resolved) {
                                        is Dataset -> {
                                            AnimatedVisibility(
                                                visible = resolvedThumbnails.isNotEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                                                    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                                        ThumbnailsSection(
                                                            uuid = pageUuid,
                                                            thumbnails = resolvedThumbnails,
                                                            onDelete = { thumbnailId ->
                                                                scope.launch {
                                                                    val resp = apiClient.service.deleteThumbnail(pageUuid, thumbnailId)
                                                                    if (resp is ApiResult.Success) {
                                                                        repository.invalidateThumbnails(pageUuid)
                                                                        repository.fetchThumbnails(pageUuid, forceRefresh = true)
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "associated" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    LinkedSamplesCard(
                                                        samples = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasetSample(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("linked_samples"),
                                                        onExpandChange = { pageSetCardState("linked_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "parent" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ParentDatasetsCard(
                                                        parents = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasets(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("parent_datasets"),
                                                        onExpandChange = { pageSetCardState("parent_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "child" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ChildDatasetsCard(
                                                        children = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasets(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("child_datasets"),
                                                        onExpandChange = { pageSetCardState("child_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = !resolved.scientificMetadata.isNullOrEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ScientificMetadataCard(
                                                        metadata = resolved.scientificMetadata ?: emptyMap(),
                                                        initialExpanded = pageGetCardState("sci_meta_expanded"),
                                                        initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                                        onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                                        onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                                    )
                                                }
                                            }
                                            AssociatedFilesCard(
                                                datasetUuid = pageUuid,
                                                initialExpanded = pageGetCardState("download_links"),
                                                onExpandedChange = { pageSetCardState("download_links", it) }
                                            )
                                        }
                                        is Sample -> {
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "parent" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ParentSamplesCard(
                                                        parents = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkSamples(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("parent_samples"),
                                                        onExpandChange = { pageSetCardState("parent_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "child" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ChildSamplesCard(
                                                        children = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkSamples(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("child_samples"),
                                                        onExpandChange = { pageSetCardState("child_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "associated" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    LinkedDatasetsCard(
                                                        datasets = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasetSample(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("linked_datasets"),
                                                        onExpandChange = { pageSetCardState("linked_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = !resolved.scientificMetadata.isNullOrEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ScientificMetadataCard(
                                                        metadata = resolved.scientificMetadata ?: emptyMap(),
                                                        initialExpanded = pageGetCardState("sci_meta_expanded"),
                                                        initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                                        onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                                        onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            ScrollToTopButton(
                                visible = showScrollToTop,
                                onClick = {
                                    scope.launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    } // end key()
                } // end HorizontalPager
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoadingContent(title = "Loading Resource")
                }
            }
        } // end PullToRefreshBox
    } // end AppScaffold

    // Screen-level sheets and dialogs — operate on currentDisplayResource
    if (showSiblingGroupDialog) {
        val forGroupOptions = currentDisplayResource
        if (forGroupOptions != null) {
            val options: List<Pair<String, String>> = when (forGroupOptions) {
                is Sample  -> listOf("TYPE" to "Type", "DATE" to "Date", "OWNER" to "Owner")
                is Dataset -> listOf(
                    "MEASUREMENT" to "Measurement",
                    "INSTRUMENT"  to "Instrument",
                    "DATE"        to "Date",
                    "FORMAT"      to "Format",
                    "SESSION"     to "Session",
                    "OWNER"       to "Owner"
                )
            }
            val effectiveActive = activeSiblingGroupBy ?: when (forGroupOptions) {
                is Sample -> "TYPE"; is Dataset -> "MEASUREMENT"
            }
            AlertDialog(
                onDismissRequest = { showSiblingGroupDialog = false },
                icon = { AppIcon(AppIcons.SwapResource) },
                title = { Text("Sibling grouping") },
                text = {
                    Column {
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeSiblingGroupBy = value
                                        showSiblingGroupDialog = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = effectiveActive == value, onClick = null)
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSiblingGroupDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
    val editSheetResource = currentDisplayResource
    if (showEditSheet && editSheetResource != null) {
        EditResourceSheet(
            resource = editSheetResource,
            onDismiss = { showEditSheet = false; editSheetPendingMetadata = null },
            onSaved = { showEditSheet = false; editSheetPendingMetadata = null; onRefresh(editSheetResource.uniqueId) },
            overrideMetadata = editSheetPendingMetadata,
            onOpenMetadataEditor = { currentJson ->
                val current = runCatching {
                    currentJson.trim().ifBlank { null }
                        ?.parseAsJsonObject()
                }.getOrNull() ?: kotlinx.serialization.json.JsonObject(emptyMap())
                MetadataHolder.put(current)
                showEditSheet = false
                editSheetWaitingForMetadata = true
                onNavigateToMetadataEditor()
            }
        )
    }
    val linkSheetResource = currentDisplayResource
    if (showLinkSheet && linkSheetResource != null) {
        LinkResourceSheet(
            resource = linkSheetResource,
            recentHistory = recentHistory,
            onDismiss = { showLinkSheet = false },
            onLinked = { showLinkSheet = false; onRefresh(linkSheetResource.uniqueId) }
        )
    }
    val deletionResource = currentDisplayResource
    if (showDeletionDialog && deletionResource != null) {
        DeletionRequestDialog(
            resource = deletionResource,
            onDismiss = { showDeletionDialog = false },
            onSubmitted = { showDeletionDialog = false; onRefresh(deletionResource.uniqueId) }
        )
    }
    pendingUnlink?.let { req ->
        var isUnlinking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isUnlinking) pendingUnlink = null },
            icon = { AppIcon(AppIcons.UnlinkResource) },
            title = { Text("Unlink resource") },
            text = { Text("Remove link to \"${req.name}\"? The resources themselves will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isUnlinking = true
                            try {
                                req.action()
                                repository.invalidateResource(req.otherUuid)
                                pendingUnlink = null
                                currentDisplayResource?.let { onRefresh(it.uniqueId) }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                pendingUnlink = null
                            }
                        }
                    },
                    enabled = !isUnlinking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isUnlinking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    else Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlink = null }, enabled = !isUnlinking) { Text("Cancel") }
            }
        )
    }

    // QR Code Dialog with horizontal navigation
    if (showQrDialog) {
        if (siblingList.isNotEmpty() && siblingIndex >= 0) {
            QrCodeDialogWithNavigation(
                resources = siblingList,
                initialIndex = pagerState.currentPage % siblingList.size,
                onDismiss = { showQrDialog = false },
                onPageChange = { pageIndex ->
                    scope.launch { pagerState.animateScrollToPage(pageIndex) }
                }
            )
        } else {
            val qrResource = currentDisplayResource
            if (qrResource != null) {
                QrCodeDialog(
                    mfid = qrResource.uniqueId,
                    name = qrResource.name,
                    onDismiss = { showQrDialog = false }
                )
            }
        }
    }
}
