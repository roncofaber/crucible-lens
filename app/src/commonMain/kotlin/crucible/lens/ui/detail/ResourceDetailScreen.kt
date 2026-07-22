@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*











import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.EffectsDefaultSpring
import crucible.lens.ui.common.SpatialDefaultSizeSpring
import crucible.lens.ui.common.SpatialFastSizeSpring
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.time.Clock
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult


import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ResourceLink
import crucible.lens.data.model.Sample
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.MONTH_NAMES
import crucible.lens.data.util.dateGroupKey
import crucible.lens.data.util.monthBounds
import crucible.lens.data.util.formatFileSize
import crucible.lens.data.util.formatDecimal
import crucible.lens.ui.metadata.MetadataHolder
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.fadeEndEdge
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.QrCodeDialog
import crucible.lens.ui.common.QrCodeDialogWithNavigation
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.detail.components.*

private data class UnlinkRequest(val name: String, val otherUuid: String, val action: suspend () -> Unit)

/** Ensures [resource] is in the list and returns the list sorted by uniqueId. */
private fun <T : CrucibleResource> List<T>.ensureContains(resource: T): List<T> =
    if (any { it.uniqueId == resource.uniqueId }) sortedBy { it.uniqueId }
    else (this + resource).sortedBy { it.uniqueId }

private fun List<Sample>.filterSiblings(groupBy: String?, resource: Sample) =
    filter { s -> when (groupBy) {
        "DATE"  -> dateGroupKey(s.timestamp) == dateGroupKey(resource.timestamp)
        "OWNER" -> s.ownerOrcid == resource.ownerOrcid
        else    -> s.sampleType == resource.sampleType
    } }.sortedBy { it.uniqueId }

private fun List<Dataset>.filterSiblings(groupBy: String?, resource: Dataset) =
    filter { d -> when (groupBy) {
        "INSTRUMENT" -> d.instrumentName == resource.instrumentName
        "DATE"       -> dateGroupKey(d.timestamp) == dateGroupKey(resource.timestamp)
        "FORMAT"     -> d.dataFormat == resource.dataFormat
        "SESSION"    -> d.sessionName == resource.sessionName
        "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
        else         -> d.measurement == resource.measurement
    } }.sortedBy { it.uniqueId }

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
    resource: CrucibleResource,
    thumbnails: List<crucible.lens.data.model.Thumbnail>,
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
    cache: ResourceDetailCache = remember { ResourceDetailCache(
        loadedResources = androidx.compose.runtime.mutableStateMapOf(),
        enrichedUuids = mutableSetOf(),
        failedEnrichmentUuids = androidx.compose.runtime.mutableStateSetOf(),
        loadedThumbnails = androidx.compose.runtime.mutableStateMapOf(),
        seedThumbnails = { _, _ -> }
    ) },
    onNavigateToAddFiles: (datasetUuid: String) -> Unit = {},
    onNavigateToMetadataEditor: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var showSiblingGroupDialog by remember { mutableStateOf(false) }
    // Local groupBy that can be changed while browsing; starts from the nav argument.
    var activeSiblingGroupBy by remember { mutableStateOf(siblingGroupBy) }

    // Seed thumbnails from ViewModel on first composition so they display immediately.
    // The maps themselves are owned by the ViewModel (passed as parameters) so they survive
    // configuration changes and don't lose enriched-resource state on rotation.
    LaunchedEffect(resource.uniqueId) {
        if (resource is Dataset && thumbnails.isNotEmpty()) {
            cache.seedThumbnails(resource.uniqueId, thumbnails)
        }
    }

    // Sibling navigation: same type within the same project
    // Samples grouped by sampleType, Datasets grouped by measurement
    // Initialize with cached data to avoid flash of "-- / --" when navigating
    var sameTypeSamples by remember(resource) {
        mutableStateOf(if (resource is Sample) listOf(resource) else emptyList())
    }
    var sameTypeDatasets by remember(resource) {
        mutableStateOf(if (resource is Dataset) listOf(resource) else emptyList())
    }
    // True once the full sibling list has been resolved (cache hit or batch load done).
    // Stays false while the list is just the 1-item seed, so the counter shows "-- / --".
    var siblingsResolved by remember(resource) { mutableStateOf(false) }

    // Shared sibling-loading logic — used by both LaunchedEffects below.
    // Fetches the sibling list from cache or API, applies groupBy filtering,
    // ensures the current resource is included, and updates state.
    suspend fun loadSiblings(groupBy: String?) {
        when (resource) {
            is Sample -> {
                val projectId = resource.projectId ?: run { siblingsResolved = true; return }
                val cached = CacheManager.getProjectSamples(projectId)
                if (cached != null) {
                    sameTypeSamples = cached.filterSiblings(groupBy, resource).ensureContains(resource)
                    siblingsResolved = true
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        ApiClient.service.getFilteredSamples(
                            projectId = projectId,
                            sampleType = if (groupBy == null || groupBy == "TYPE") resource.sampleType else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> {
                            val sorted = resp.data.ensureContains(resource)
                            sorted.forEach { cache.loadedResources.getOrPut(it.uniqueId) { it } }
                            sameTypeSamples = sorted
                            siblingsResolved = true
                        }
                        is ApiResult.Error -> siblingsResolved = true
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.projectId ?: run { siblingsResolved = true; return }
                val cached = CacheManager.getProjectDatasets(projectId)
                if (cached != null) {
                    sameTypeDatasets = cached.filterSiblings(groupBy, resource).ensureContains(resource)
                    siblingsResolved = true
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        ApiClient.service.getFilteredDatasets(
                            projectId = projectId,
                            measurement = if (groupBy == null || groupBy == "MEASUREMENT") resource.measurement else null,
                            instrumentName = if (groupBy == "INSTRUMENT") resource.instrumentName else null,
                            dataFormat = if (groupBy == "FORMAT") resource.dataFormat else null,
                            sessionName = if (groupBy == "SESSION") resource.sessionName else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> {
                            val sorted = resp.data.ensureContains(resource)
                            sorted.forEach { cache.loadedResources.getOrPut(it.uniqueId) { it } }
                            sameTypeDatasets = sorted
                            siblingsResolved = true
                        }
                        is ApiResult.Error -> siblingsResolved = true
                    }
                }
            }
        }
    }

    // Initial load: seed the cache with the authoritative resource, warm loadedResources
    // from any cached siblings, then load the sibling list.
    LaunchedEffect(resource) {
        cache.loadedResources[resource.uniqueId] = resource
        cache.enrichedUuids.add(resource.uniqueId)
        cache.loadedThumbnails.remove(resource.uniqueId)
        // Warm loadedResources from the project cache so sibling pages render instantly
        when (resource) {
            is Sample -> resource.projectId?.let { pid ->
                CacheManager.getProjectSamples(pid)?.forEach { s ->
                    val rich = CacheManager.getResource(s.uniqueId) as? Sample
                    if (rich != null) { cache.enrichedUuids.add(s.uniqueId); cache.loadedResources[s.uniqueId] = rich }
                }
            }
            is Dataset -> resource.projectId?.let { pid ->
                CacheManager.getProjectDatasets(pid)?.forEach { d ->
                    val rich = CacheManager.getResource(d.uniqueId) as? Dataset
                    if (rich != null) { cache.enrichedUuids.add(d.uniqueId); cache.loadedResources[d.uniqueId] = rich }
                }
            }
        }
        try { loadSiblings(siblingGroupBy) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { println("Error loading siblings: $e"); siblingsResolved = true }
    }

    // Re-filter siblings when the user changes groupBy mid-browse.
    LaunchedEffect(activeSiblingGroupBy) {
        if (activeSiblingGroupBy == siblingGroupBy) return@LaunchedEffect
        siblingsResolved = false
        try { loadSiblings(activeSiblingGroupBy) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { println("Error loading siblings: $e"); siblingsResolved = true }
    }

    val siblingList: List<CrucibleResource> = when (resource) {
        is Sample -> sameTypeSamples
        is Dataset -> sameTypeDatasets
    }
    val siblingIndex = remember(siblingList, resource) {
        siblingList.indexOfFirst { it.uniqueId == resource.uniqueId }
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
    val currentPageResource = siblingList.getOrNull(pagerState.currentPage) ?: resource
    val currentDisplayResource: CrucibleResource =
        cache.loadedResources[currentPageResource.uniqueId] ?: currentPageResource

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

    // Seed the primary resource into local maps immediately so its page never shows
    // a loading state. The enrichment LaunchedEffect would do this eventually, but
    // doing it eagerly avoids a 1-frame flash on first composition.
    LaunchedEffect(resource.uniqueId) {
        cache.loadedResources[resource.uniqueId] = resource
        val hasLinks = (resource is Sample && resource.links != null) ||
                       (resource is Dataset && resource.links != null)
        if (hasLinks) cache.enrichedUuids.add(resource.uniqueId)
    }

    // Re-open edit sheet with updated metadata after returning from MetadataEditorScreen.
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty && editSheetWaitingForMetadata) {
            editSheetPendingMetadata = MetadataHolder.take()
            editSheetWaitingForMetadata = false
            showEditSheet = true
        }
    }

    // Incremented when a sibling PTR completes, forces lazy-load effects to re-run
    var siblingReloadTrigger by remember { mutableIntStateOf(0) }

    // Lazy load relationships for ~20 neighbors (current ± 10)
    // Thumbnails loaded separately with more conservative strategy
    LaunchedEffect(pagerState.currentPage, siblingList, siblingReloadTrigger) {
        if (siblingList.isEmpty()) return@LaunchedEffect

        val currentPage = pagerState.currentPage
        val relationshipRange = 10

        // Build list of pages to load in expanding order: 0, +1, -1, +2, -2, ...
        val pagesToLoad = mutableListOf<Int>()
        pagesToLoad.add(currentPage)
        for (offset in 1..relationshipRange) {
            if (currentPage + offset in siblingList.indices) pagesToLoad.add(currentPage + offset)
            if (currentPage - offset in siblingList.indices) pagesToLoad.add(currentPage - offset)
        }

        // Filter pages that still need enrichment — check on main thread (safe for SnapshotStateMap)
        val toEnrich = pagesToLoad.filter { pageIndex ->
            val uuid = siblingList[pageIndex].uniqueId
            if (uuid in cache.enrichedUuids) return@filter false
            when (val existing = cache.loadedResources[uuid]) {
                is Sample  -> existing.links == null
                is Dataset -> existing.links == null
                else       -> true
            }
        }

        // Load relationships (but NOT thumbnails yet) for pages in the window
        launch(Dispatchers.Default) {
            toEnrich.forEachIndexed { index, pageIndex ->
                val pageResource = siblingList[pageIndex]
                val uuid = pageResource.uniqueId

                // Add small delay between loads (except first few)
                if (index > 2) {
                    kotlinx.coroutines.delay(150)
                }

                // Use individual resource cache if already enriched from a prior navigation
                val cached = CacheManager.getResource(uuid)
                if (cached != null && when (cached) {
                        is Sample  -> cached.links != null
                        is Dataset -> cached.links != null
                    }
                ) {
                    withContext(Dispatchers.Main) {
                        cache.enrichedUuids.add(uuid)
                        cache.loadedResources[uuid] = cached
                    }
                    return@forEachIndexed
                }

                try {
                    val enriched: CrucibleResource? = when (pageResource) {
                        is Sample -> (ApiClient.service.getSample(uuid) as? ApiResult.Success)?.data
                        is Dataset ->
                            (ApiClient.service.getDataset(uuid, includeMetadata = true) as? ApiResult.Success)?.data
                    }
                    withContext(Dispatchers.Main) {
                        cache.enrichedUuids.add(uuid)
                        if (enriched != null) {
                            cache.loadedResources[uuid] = enriched
                            CacheManager.cacheResource(uuid, enriched)
                            cache.failedEnrichmentUuids.remove(uuid)
                        } else {
                            cache.failedEnrichmentUuids.add(uuid)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { cache.failedEnrichmentUuids.add(uuid) }
                }
            }
        }

        // Clean up resources far from current page (keep memory usage bounded)
        launch {
            val cleanupThreshold = 20
            val keysToRemove = cache.loadedResources.keys.filter { uuid ->
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > cleanupThreshold
            }
            keysToRemove.forEach { uuid ->
                cache.loadedResources.remove(uuid)
                cache.loadedThumbnails.remove(uuid)
                cache.enrichedUuids.remove(uuid)
                cache.failedEnrichmentUuids.remove(uuid)
            }
        }
    }

    // Separate effect: Load thumbnails for current ± 2 pages
    // Thumbnails are heavy (large base64 strings + decoded bitmaps)
    LaunchedEffect(pagerState.currentPage, siblingList, siblingReloadTrigger) {
        if (siblingList.isEmpty()) return@LaunchedEffect

        val currentPage = pagerState.currentPage

        // Pages that should have thumbnails loaded (current ± 2)
        val thumbnailPages = (-2..2).map { currentPage + it }.filter { it in siblingList.indices }

        // Load thumbnails for these pages
        launch(Dispatchers.Default) {
            thumbnailPages.forEach { pageIndex ->
                val pageResource = siblingList[pageIndex]
                val uuid = pageResource.uniqueId

                // Skip if already loaded or not a dataset
                if (cache.loadedThumbnails.containsKey(uuid) || pageResource !is Dataset) return@forEach

                // Prefer the ViewModel-cached thumbnails over a fresh API call
                val cached = CacheManager.getThumbnails(uuid)
                if (cached != null) {
                    withContext(Dispatchers.Main) { cache.loadedThumbnails[uuid] = cached }
                    return@forEach
                }

                try {
                    val thumbResp = ApiClient.service.getThumbnails(uuid)
                    val thumbs = when (thumbResp) {
                        is ApiResult.Success -> thumbResp.data
                        is ApiResult.Error -> emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        cache.loadedThumbnails[uuid] = thumbs
                        if (thumbs.isNotEmpty()) CacheManager.cacheThumbnails(uuid, thumbs)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Error: $e")
                    withContext(Dispatchers.Main) { cache.loadedThumbnails[uuid] = emptyList() }
                }
            }
        }

        // Clean up thumbnails outside current ± 3 range to prevent memory bloat
        launch {
            val thumbnailCleanupThreshold = 3
            val thumbnailKeysToRemove = cache.loadedThumbnails.keys.filter { uuid ->
                if (uuid == resource.uniqueId) return@filter false
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > thumbnailCleanupThreshold
            }
            thumbnailKeysToRemove.forEach { uuid ->
                cache.loadedThumbnails.remove(uuid)
            }
        }
    }

    LaunchedEffect(resource.uniqueId) {
        onSaveToHistory(resource.uniqueId, resource.name, if (resource is Sample) "sample" else "dataset")
    }
    val scope = rememberCoroutineScope()
    val platformContext = getPlatformContext()
    val isDarkTheme = isSystemInDarkTheme()
    val primaryColorValue = MaterialTheme.colorScheme.primary.value.toLong()


    // True only when a sibling (not the primary resource) was pull-to-refreshed.
    // Guards siblingReloadTrigger so it only fires on actual sibling PTRs.
    var localRefreshState by remember { mutableStateOf(false) }

    fun triggerRefresh() {
        val currentUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId
            ?: resource.uniqueId
        if (currentUuid != resource.uniqueId) {
            // Sibling refresh: fetch inline without ViewModel involvement.
            // Old cache.loadedResources/cache.enrichedUuids are kept so links stay visible.
            scope.launch {
                localRefreshState = true
                try {
                    val pageResource = siblingList.firstOrNull { it.uniqueId == currentUuid }
                    if (pageResource != null) {
                        val fresh: CrucibleResource? = withContext(Dispatchers.Default) {
                            when (pageResource) {
                                is Sample  -> (ApiClient.service.getSample(currentUuid) as? ApiResult.Success)?.data
                                is Dataset -> (ApiClient.service.getDataset(currentUuid, includeMetadata = true) as? ApiResult.Success)?.data
                            }
                        }
                        if (fresh != null) {
                            cache.loadedResources[currentUuid] = fresh
                            CacheManager.cacheResource(currentUuid, fresh)
                        }
                        cache.loadedThumbnails.remove(currentUuid)
                        siblingReloadTrigger++ // re-fetch thumbnails
                    }
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
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { AppIcon(AppIcons.Edit) },
                                    onClick = { overflowMenuExpanded = false; showEditSheet = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { AppIcon(AppIcons.CopyResource) },
                                    onClick = { overflowMenuExpanded = false; onDuplicate(currentDisplayResource) }
                                )
                                if (currentDisplayResource is Dataset) {
                                    DropdownMenuItem(
                                        text = { Text("Add file") },
                                        leadingIcon = { AppIcon(AppIcons.AttachFile) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            onNavigateToAddFiles(currentDisplayResource.uniqueId)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Link") },
                                    leadingIcon = { AppIcon(AppIcons.LinkResource) },
                                    onClick = { overflowMenuExpanded = false; showLinkSheet = true }
                                )
                                val deletionRequest = when (currentDisplayResource) {
                                    is Sample -> currentDisplayResource.deletionRequest
                                    is Dataset -> currentDisplayResource.deletionRequest
                                }
                                val deletionStatus = (deletionRequest?.get("status") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                DropdownMenuItem(
                                    text = { Text(if (deletionStatus != null) "Deletion ${deletionStatus.replaceFirstChar { it.uppercase() }}" else "Request deletion") },
                                    leadingIcon = { AppIcon(AppIcons.RequestDeletion) },
                                    enabled = deletionStatus == null,
                                    onClick = { overflowMenuExpanded = false; showDeletionDialog = true }
                                )
                                val projectId = when (currentDisplayResource) {
                                    is Sample -> currentDisplayResource.projectId
                                    is Dataset -> currentDisplayResource.projectId
                                }
                                if (projectId != null && graphExplorerUrl.isNotBlank()) {
                                    val webUrl = when (currentDisplayResource) {
                                        is Sample  -> "$graphExplorerUrl/$projectId/samples/${currentDisplayResource.uniqueId}"
                                        is Dataset -> "$graphExplorerUrl/$projectId/datasets/${currentDisplayResource.uniqueId}"
                                    }
                                    OpenInWebMenuItem { overflowMenuExpanded = false; openUrl(platformContext, webUrl) }
                                    ShareMenuItem {
                                        overflowMenuExpanded = false
                                        shareResource(
                                            context = platformContext,
                                            resource = currentDisplayResource,
                                            shareText = webUrl,
                                            subject = currentDisplayResource.name,
                                            darkTheme = isDarkTheme,
                                            bannerColorValue = primaryColorValue
                                        )
                                    }
                                }
                                // Sibling grouping — only when resource belongs to a project
                                if (projectId != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    val groupLabel = siblingGroupLabel(activeSiblingGroupBy, currentDisplayResource)
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
            // HorizontalPager for seamless sibling navigation with snap
            if (siblingList.isNotEmpty() && siblingIndex >= 0) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { pageIndex ->
                    if (pageIndex >= siblingList.size) return@HorizontalPager
                    val pageResource = siblingList[pageIndex]
                    val isCurrentResource = pageResource.uniqueId == resource.uniqueId

                    key(pageResource.uniqueId) {
                        // Each page needs its own independent scroll state
                        val scrollState = rememberScrollState()
                        val showScrollToTop by remember { derivedStateOf { scrollState.value > 300 } }
                        // Scope card state to this page's resource so expanded/collapsed state
                        // doesn't leak across pages sharing the same callback origin.
                        val pageId = pageResource.uniqueId
                        val pageGetCardState: (String) -> Boolean = { key -> getCardState("$pageId/$key") }
                        val pageSetCardState: (String, Boolean) -> Unit = { key, value -> onCardStateChange("$pageId/$key", value) }

                        // Loading state is driven purely by per-page enrichment:
                        // show spinner while this page's data is in flight, content once done.
                        val isPageEnriched = cache.enrichedUuids.contains(pageResource.uniqueId) ||
                                             cache.failedEnrichmentUuids.contains(pageResource.uniqueId)

                        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isPageEnriched && !isRefreshing,
                enter = fadeIn(animationSpec = EffectsDefaultSpring),
                exit = fadeOut(animationSpec = EffectsDefaultSpring)
            ) {
                LoadingContent(title = "Loading Resource")
            }

            AnimatedVisibility(
                visible = isPageEnriched,
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
                                resource = if (isCurrentResource) resource else pageResource,
                                onPrev = if (hasPrev) {
                                    {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pageIndex - 1)
                                        }
                                    }
                                } else null,
                                onNext = if (hasNext) {
                                    {
                                        scope.launch {
                                            pagerState.animateScrollToPage(pageIndex + 1)
                                        }
                                    }
                                } else null,
                                currentIndex = pageIndex,
                                totalCount = siblingList.size,
                                siblingsResolved = siblingsResolved
                            )
                        }

                // Use fully loaded resource if available, otherwise fall back to basic or current
                val displayResource = cache.loadedResources[pageResource.uniqueId]
                    ?: if (isCurrentResource) resource else pageResource
                val displayThumbnails = cache.loadedThumbnails[pageResource.uniqueId]
                    ?: if (isCurrentResource) thumbnails else emptyList()

                AnimatedVisibility(
                    visible = pageResource.uniqueId in cache.failedEnrichmentUuids,
                    enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                    exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                ) {
                    ErrorCard(
                        title = "Could not load full data",
                        message = "Links and metadata may be incomplete.",
                        modifier = Modifier.padding(bottom = 16.dp),
                        onRetry = {
                            cache.enrichedUuids.remove(pageResource.uniqueId)
                            cache.failedEnrichmentUuids.remove(pageResource.uniqueId)
                            siblingReloadTrigger++
                        }
                    )
                }

                val displayDeletionRequest = when (displayResource) {
                    is Sample -> displayResource.deletionRequest
                    is Dataset -> displayResource.deletionRequest
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

                when (displayResource) {
                    is Sample -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        SampleDetailsCard(
                            sample = displayResource,
                            onProjectClick = onNavigateToProject,
                            onUserClick = onNavigateToUser,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = pageGetCardState("advanced"),
                            onAdvancedChange = { pageSetCardState("advanced", it) }
                        )
                    }
                    is Dataset -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        DatasetDetailsCard(
                            dataset = displayResource,
                            onProjectClick = onNavigateToProject,
                            onUserClick = onNavigateToUser,
                            onInstrumentClick = onNavigateToInstrument,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = pageGetCardState("advanced"),
                            onAdvancedChange = { pageSetCardState("advanced", it) }
                        )
                    }
                }

                when (displayResource) {
                    is Dataset -> {
                        AnimatedVisibility(
                            visible = displayThumbnails.isNotEmpty(),
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                                Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                    ThumbnailsSection(
                                        uuid = pageResource.uniqueId,
                                        thumbnails = displayThumbnails,
                                        onDelete = { thumbnailId ->
                                            scope.launch {
                                                val resp = ApiClient.service.deleteThumbnail(pageResource.uniqueId, thumbnailId)
                                                if (resp is ApiResult.Success) {
                                                    cache.loadedThumbnails[pageResource.uniqueId] =
                                                        cache.loadedThumbnails[pageResource.uniqueId].orEmpty()
                                                            .filter { it.id != thumbnailId }
                                                    CacheManager.clearThumbnail(pageResource.uniqueId)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "associated" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                LinkedSamplesCard(
                                    samples = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkDatasetSample(displayResource.uniqueId, uuid) } },
                                    initialExpanded = pageGetCardState("linked_samples"),
                                    onExpandChange = { pageSetCardState("linked_samples", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "parent" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ParentDatasetsCard(
                                    parents = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkDatasets(uuid, displayResource.uniqueId) } },
                                    initialExpanded = pageGetCardState("parent_datasets"),
                                    onExpandChange = { pageSetCardState("parent_datasets", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "child" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ChildDatasetsCard(
                                    children = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkDatasets(displayResource.uniqueId, uuid) } },
                                    initialExpanded = pageGetCardState("child_datasets"),
                                    onExpandChange = { pageSetCardState("child_datasets", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = !displayResource.scientificMetadata.isNullOrEmpty(),
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ScientificMetadataCard(
                                    metadata = displayResource.scientificMetadata ?: emptyMap(),
                                    initialExpanded = pageGetCardState("sci_meta_expanded"),
                                    initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                    onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                    onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                )
                            }
                        }
                        AssociatedFilesCard(
                            datasetUuid = pageResource.uniqueId,
                            initialExpanded = pageGetCardState("download_links"),
                            onExpandedChange = { pageSetCardState("download_links", it) }
                        )
                    }
                    is Sample -> {
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "parent" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ParentSamplesCard(
                                    parents = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkSamples(uuid, displayResource.uniqueId) } },
                                    initialExpanded = pageGetCardState("parent_samples"),
                                    onExpandChange = { pageSetCardState("parent_samples", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "child" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ChildSamplesCard(
                                    children = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkSamples(displayResource.uniqueId, uuid) } },
                                    initialExpanded = pageGetCardState("child_samples"),
                                    onExpandChange = { pageSetCardState("child_samples", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "associated" } == true,
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                LinkedDatasetsCard(
                                    datasets = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    onUnlink = { uuid, name -> pendingUnlink = UnlinkRequest(name, uuid) { ApiClient.service.unlinkDatasetSample(uuid, displayResource.uniqueId) } },
                                    initialExpanded = pageGetCardState("linked_datasets"),
                                    onExpandChange = { pageSetCardState("linked_datasets", it) }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = !displayResource.scientificMetadata.isNullOrEmpty(),
                            enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                            exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                ScientificMetadataCard(
                                    metadata = displayResource.scientificMetadata ?: emptyMap(),
                                    initialExpanded = pageGetCardState("sci_meta_expanded"),
                                    initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                    onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                    onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                )
                            }
                        }
                    }
                }

                val ageMin = CacheManager.getResourceAgeMinutes(resource.uniqueId)
                if (ageMin != null) {
                    Text(
                        text = "Cached ${ageMin}m ago",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                } // end Column
            } // end AnimatedVisibility (content)

                        // Scroll-to-top button
                        ScrollToTopButton(
                            visible = showScrollToTop,
                            onClick = {
                                scope.launch {
                                    scrollState.animateScrollTo(0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        )
                    } // end page Box
                    } // end key() block
                } // end HorizontalPager
            } else {
                // Fallback: siblings haven't loaded yet — show loading unconditionally.
                Box(modifier = Modifier.fillMaxSize()) {
                    LoadingContent(title = "Loading Resource")
                }
            } // end else
        } // end PullToRefreshBox
    } // end Scaffold

    // Screen-level sheets and dialogs — operate on currentDisplayResource
    if (showSiblingGroupDialog) {
        val options: List<Pair<String, String>> = when (resource) {
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
        val effectiveActive = activeSiblingGroupBy ?: when (resource) {
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
                            RadioButton(
                                selected = effectiveActive == value,
                                onClick = null
                            )
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
    if (showEditSheet) {
        EditResourceSheet(
            resource = currentDisplayResource,
            onDismiss = { showEditSheet = false; editSheetPendingMetadata = null },
            onSaved = { showEditSheet = false; editSheetPendingMetadata = null; onRefresh(currentDisplayResource.uniqueId) },
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
    if (showLinkSheet) {
        LinkResourceSheet(
            resource = currentDisplayResource,
            recentHistory = recentHistory,
            onDismiss = { showLinkSheet = false },
            onLinked = { showLinkSheet = false; onRefresh(currentDisplayResource.uniqueId) }
        )
    }
    if (showDeletionDialog) {
        DeletionRequestDialog(
            resource = currentDisplayResource,
            onDismiss = { showDeletionDialog = false },
            onSubmitted = { showDeletionDialog = false; onRefresh(currentDisplayResource.uniqueId) }
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
                                // Evict both sides so neither shows stale links
                                CacheManager.clearResource(req.otherUuid)
                                pendingUnlink = null
                                onRefresh(currentDisplayResource.uniqueId)
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
            QrCodeDialog(
                mfid = resource.uniqueId,
                name = resource.name,
                onDismiss = { showQrDialog = false }
            )
        }
    }
}


