package crucible.lens.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import crucible.lens.data.api.ApiClient
import android.util.Base64
import android.util.Log
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ResourceLink
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.AnimatedPullToRefreshIndicator
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.QrCodeDialog
import crucible.lens.ui.common.QrCodeDialogWithNavigation
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.ShareCardGenerator
import crucible.lens.ui.common.openUrlInBrowser

private data class UnlinkRequest(val name: String, val action: suspend () -> Unit)

private fun monthBounds(raw: String?): Pair<String, String>? {
    if (raw == null) return null
    return try {
        val ldt = try { java.time.OffsetDateTime.parse(raw.trim()).toLocalDateTime() }
                  catch (_: Exception) { java.time.LocalDateTime.parse(raw.trim()) }
        val start = ldt.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val end = ldt.withDayOfMonth(ldt.toLocalDate().lengthOfMonth())
                    .withHour(23).withMinute(59).withSecond(59).withNano(999_999_999)
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        start.format(fmt) to end.format(fmt)
    } catch (_: Exception) { null }
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

private fun dateGroupKey(raw: String?): String {
    if (raw == null) return "No date"
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy")
    return try { fmt.format(java.time.OffsetDateTime.parse(raw.trim())) }
    catch (_: Exception) { try { fmt.format(java.time.LocalDateTime.parse(raw.trim())) }
    catch (_: Exception) { "No date" } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ResourceDetailScreen(
    resource: CrucibleResource,
    thumbnails: List<String>,
    mfid: String = resource.uniqueId,
    isRefreshing: Boolean = false,
    graphExplorerUrl: String,
    siblingGroupBy: String? = null,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onNavigateToInstrument: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onHome: () -> Unit,
    onRefresh: (uuid: String) -> Unit,
    onNavigateToSibling: (uuid: String, groupBy: String?) -> Unit = { uuid, _ -> onNavigateToResource(uuid) },
    onDuplicate: (CrucibleResource) -> Unit = {},
    recentHistory: List<crucible.lens.data.preferences.HistoryItem> = emptyList(),
    onSaveToHistory: (uuid: String, name: String) -> Unit = { _, _ -> },
    getCardState: (key: String) -> Boolean = { false },
    onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var showSiblingGroupDialog by remember { mutableStateOf(false) }
    // Local groupBy that can be changed while browsing; starts from the nav argument.
    var activeSiblingGroupBy by remember { mutableStateOf(siblingGroupBy) }

    // Cache for loaded resources with full metadata and relationships
    val loadedResources = remember { mutableStateMapOf<String, CrucibleResource>() }
    // Tracks UUIDs that have been fetched with full metadata (links + scientificMetadata).
    // Checked on the main thread to avoid SnapshotStateMap reads from IO dispatchers.
    val enrichedUuids = remember { mutableSetOf<String>() }
    // Pre-seed only when thumbnails is non-empty. An empty list from the ViewModel means
    // either the fetch failed or the thumbnail cache expired — in both cases we want the
    // LaunchedEffect to retry. Only skip the fetch when we already have real images.
    val loadedThumbnails = remember {
        mutableStateMapOf<String, List<String>>().also { map ->
            if (resource is Dataset && thumbnails.isNotEmpty()) map[resource.uniqueId] = thumbnails
        }
    }

    // Sibling navigation: same type within the same project
    // Samples grouped by sampleType, Datasets grouped by measurement
    // Initialize with cached data to avoid flash of "-- / --" when navigating
    var sameTypeSamples by remember(resource) {
        mutableStateOf(if (resource is Sample) listOf<Sample>(resource) else emptyList())
    }
    var sameTypeDatasets by remember(resource) {
        mutableStateOf(if (resource is Dataset) listOf<Dataset>(resource) else emptyList())
    }
    // True once the full sibling list has been resolved (cache hit or batch load done).
    // Stays false while the list is just the 1-item seed, so the counter shows "-- / --".
    var siblingsResolved by remember(resource) { mutableStateOf(false) }

    // Batch load all project resources in background (for sibling navigation)
    LaunchedEffect(resource) {
        // Always seed with the authoritative incoming resource (handles post-refresh updates where
        // the ViewModel has a fresh version but loadedResources still holds a stale one).
        loadedResources[resource.uniqueId] = resource
        enrichedUuids.add(resource.uniqueId)
        // Clear stale thumbnails so the fresh ones from the ViewModel are displayed after refresh.
        loadedThumbnails.remove(resource.uniqueId)

        when (resource) {
            is Sample -> {
                val projectId = resource.projectId
                if (projectId == null) { siblingsResolved = true; return@LaunchedEffect }
                fun List<Sample>.filterAndSort() = filter { s -> when (siblingGroupBy) {
                    "DATE"  -> dateGroupKey(s.timestamp) == dateGroupKey(resource.timestamp)
                    "OWNER" -> s.ownerOrcid == resource.ownerOrcid
                    else    -> s.sampleType == resource.sampleType
                } }.sortedBy { it.uniqueId }
                // Fast path: use existing project cache
                val cached = CacheManager.getProjectSamples(projectId)
                if (cached != null) {
                    val filtered = cached.filterAndSort()
                    val sorted = if (filtered.any { it.uniqueId == resource.uniqueId }) filtered else (filtered + resource).sortedBy { it.uniqueId }
                    cached.forEach { s -> val rich = CacheManager.getResource(s.uniqueId) as? Sample; if (rich != null) { enrichedUuids.add(s.uniqueId); loadedResources[s.uniqueId] = rich } }
                    sameTypeSamples = sorted
                    siblingsResolved = true
                } else {
                    // Fetch only matching siblings from the server
                    launch {
                        try {
                            val bounds = if (siblingGroupBy == "DATE") monthBounds(resource.timestamp) else null
                            val resp = withContext(Dispatchers.IO) {
                                ApiClient.service.getFilteredSamples(
                                    projectId = projectId,
                                    sampleType = if (siblingGroupBy == null || siblingGroupBy == "TYPE") resource.sampleType else null,
                                    ownerOrcid = if (siblingGroupBy == "OWNER") resource.ownerOrcid else null,
                                    creationTimeGte = bounds?.first,
                                    creationTimeLte = bounds?.second
                                )
                            }
                            if (resp.isSuccessful) {
                                val all = resp.body() ?: emptyList()
                                val sorted = if (all.any { it.uniqueId == resource.uniqueId }) all.sortedBy { it.uniqueId }
                                             else (all + resource).sortedBy { it.uniqueId }
                                sorted.forEach { s -> loadedResources.getOrPut(s.uniqueId) { s } }
                                sameTypeSamples = sorted
                                siblingsResolved = true
                            } else {
                                siblingsResolved = true
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("ResourceDetail", "Failed to load sibling samples", e)
                            siblingsResolved = true
                        }
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.projectId
                if (projectId == null) { siblingsResolved = true; return@LaunchedEffect }
                fun List<Dataset>.filterAndSort() = filter { d -> when (siblingGroupBy) {
                    "INSTRUMENT" -> d.instrumentName == resource.instrumentName
                    "DATE"       -> dateGroupKey(d.timestamp) == dateGroupKey(resource.timestamp)
                    "FORMAT"     -> d.dataFormat == resource.dataFormat
                    "SESSION"    -> d.sessionName == resource.sessionName
                    "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
                    else         -> d.measurement == resource.measurement
                } }.sortedBy { it.uniqueId }
                // Fast path: use existing project cache
                val cached = CacheManager.getProjectDatasets(projectId)
                if (cached != null) {
                    val filtered = cached.filterAndSort()
                    val sorted = if (filtered.any { it.uniqueId == resource.uniqueId }) filtered else (filtered + resource).sortedBy { it.uniqueId }
                    cached.forEach { d -> val rich = CacheManager.getResource(d.uniqueId) as? Dataset; if (rich != null) { enrichedUuids.add(d.uniqueId); loadedResources[d.uniqueId] = rich } }
                    sameTypeDatasets = sorted
                    siblingsResolved = true
                } else {
                    // Fetch only matching siblings from the server
                    launch {
                        try {
                            val bounds = if (siblingGroupBy == "DATE") monthBounds(resource.timestamp) else null
                            val resp = withContext(Dispatchers.IO) {
                                ApiClient.service.getFilteredDatasets(
                                    projectId = projectId,
                                    measurement = if (siblingGroupBy == null || siblingGroupBy == "MEASUREMENT") resource.measurement else null,
                                    instrumentName = if (siblingGroupBy == "INSTRUMENT") resource.instrumentName else null,
                                    dataFormat = if (siblingGroupBy == "FORMAT") resource.dataFormat else null,
                                    sessionName = if (siblingGroupBy == "SESSION") resource.sessionName else null,
                                    ownerOrcid = if (siblingGroupBy == "OWNER") resource.ownerOrcid else null,
                                    creationTimeGte = bounds?.first,
                                    creationTimeLte = bounds?.second
                                )
                            }
                            if (resp.isSuccessful) {
                                val all = resp.body() ?: emptyList()
                                val sorted = if (all.any { it.uniqueId == resource.uniqueId }) all.sortedBy { it.uniqueId }
                                             else (all + resource).sortedBy { it.uniqueId }
                                sorted.forEach { d -> loadedResources.getOrPut(d.uniqueId) { d } }
                                sameTypeDatasets = sorted
                                siblingsResolved = true
                            } else {
                                siblingsResolved = true
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("ResourceDetail", "Failed to load sibling datasets", e)
                            siblingsResolved = true
                        }
                    }
                }
            }
        }
    }
    // Re-filter siblings when the user changes groupBy mid-browse.
    // Skips the initial value (the main LaunchedEffect(resource) already handles that).
    LaunchedEffect(activeSiblingGroupBy) {
        if (activeSiblingGroupBy == siblingGroupBy) return@LaunchedEffect
        siblingsResolved = false
        suspend fun loadFilteredSiblings(groupBy: String?) {
            when (resource) {
                is Sample -> {
                    val projectId = resource.projectId ?: run { siblingsResolved = true; return }
                    fun List<Sample>.filterAndSort() = filter { s -> when (groupBy) {
                        "DATE"  -> dateGroupKey(s.timestamp) == dateGroupKey(resource.timestamp)
                        "OWNER" -> s.ownerOrcid == resource.ownerOrcid
                        else    -> s.sampleType == resource.sampleType
                    } }.sortedBy { it.uniqueId }
                    val cached = CacheManager.getProjectSamples(projectId)
                    if (cached != null) {
                        val sorted = cached.filterAndSort().let { f ->
                            if (f.any { it.uniqueId == resource.uniqueId }) f else (f + resource).sortedBy { it.uniqueId }
                        }
                        sameTypeSamples = sorted
                        siblingsResolved = true
                    } else {
                        val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                        val resp = withContext(Dispatchers.IO) {
                            ApiClient.service.getFilteredSamples(
                                projectId = projectId,
                                sampleType = if (groupBy == null || groupBy == "TYPE") resource.sampleType else null,
                                ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                                creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                            )
                        }
                        if (resp.isSuccessful) {
                            val all = resp.body() ?: emptyList()
                            val sorted = if (all.any { it.uniqueId == resource.uniqueId }) all.sortedBy { it.uniqueId }
                                         else (all + resource).sortedBy { it.uniqueId }
                            sameTypeSamples = sorted
                            siblingsResolved = true
                        } else { siblingsResolved = true }
                    }
                }
                is Dataset -> {
                    val projectId = resource.projectId ?: run { siblingsResolved = true; return }
                    fun List<Dataset>.filterAndSort() = filter { d -> when (groupBy) {
                        "INSTRUMENT" -> d.instrumentName == resource.instrumentName
                        "DATE"       -> dateGroupKey(d.timestamp) == dateGroupKey(resource.timestamp)
                        "FORMAT"     -> d.dataFormat == resource.dataFormat
                        "SESSION"    -> d.sessionName == resource.sessionName
                        "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
                        else         -> d.measurement == resource.measurement
                    } }.sortedBy { it.uniqueId }
                    val cached = CacheManager.getProjectDatasets(projectId)
                    if (cached != null) {
                        val sorted = cached.filterAndSort().let { f ->
                            if (f.any { it.uniqueId == resource.uniqueId }) f else (f + resource).sortedBy { it.uniqueId }
                        }
                        sameTypeDatasets = sorted
                        siblingsResolved = true
                    } else {
                        val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                        val resp = withContext(Dispatchers.IO) {
                            ApiClient.service.getFilteredDatasets(
                                projectId = projectId,
                                measurement = if (groupBy == null || groupBy == "MEASUREMENT") resource.measurement else null,
                                instrumentName = if (groupBy == "INSTRUMENT") resource.instrumentName else null,
                                dataFormat = if (groupBy == "FORMAT") resource.dataFormat else null,
                                sessionName = if (groupBy == "SESSION") resource.sessionName else null,
                                ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                                creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                            )
                        }
                        if (resp.isSuccessful) {
                            val all = resp.body() ?: emptyList()
                            val sorted = if (all.any { it.uniqueId == resource.uniqueId }) all.sortedBy { it.uniqueId }
                                         else (all + resource).sortedBy { it.uniqueId }
                            sameTypeDatasets = sorted
                            siblingsResolved = true
                        } else { siblingsResolved = true }
                    }
                }
            }
        }
        try {
            loadFilteredSiblings(activeSiblingGroupBy)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ResourceDetail", "Failed to reload siblings", e)
            siblingsResolved = true
        }
    }

    val siblingList: List<CrucibleResource> = when (resource) {
        is Sample -> sameTypeSamples
        is Dataset -> sameTypeDatasets
    }
    val siblingIndex = remember(siblingList, resource) {
        siblingList.indexOfFirst { it.uniqueId == resource.uniqueId }
    }

    // HorizontalPager state for seamless sibling navigation
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { siblingList.size.coerceAtLeast(1) }
    )

    // Scroll to the resource's position once siblings are resolved and pageCount is updated.
    // Runs after recomposition so pageCount reflects the full sibling list size.
    LaunchedEffect(siblingIndex, siblingsResolved) {
        if (siblingsResolved && siblingIndex > 0) {
            pagerState.scrollToPage(siblingIndex)
        }
    }

    // Current resource shown in the pager — drives TopAppBar title and overflow menu
    val currentPageResource = siblingList.getOrNull(pagerState.currentPage) ?: resource
    val currentDisplayResource: CrucibleResource =
        loadedResources[currentPageResource.uniqueId] ?: currentPageResource

    // Screen-level sheet/dialog state — operate on currentDisplayResource
    var showEditSheet by remember { mutableStateOf(false) }
    var showLinkSheet by remember { mutableStateOf(false) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var pendingUnlink by remember { mutableStateOf<UnlinkRequest?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    // Track history continuously as user scrolls through pages
    LaunchedEffect(pagerState.currentPage, pagerState.targetPage) {
        // Save current page to history immediately (even while scrolling)
        val targetPage = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        val targetResource = siblingList.getOrNull(targetPage)
        if (targetResource != null) {
            onSaveToHistory(targetResource.uniqueId, targetResource.name)
        }
    }

    // Handle pager swipes to trigger navigation when scroll completes
    LaunchedEffect(pagerState.currentPage) {
        if (siblingList.isNotEmpty() && !pagerState.isScrollInProgress) {
            val targetResource = siblingList.getOrNull(pagerState.currentPage)
            if (targetResource != null && targetResource.uniqueId != resource.uniqueId) {
                onNavigateToSibling(targetResource.uniqueId, activeSiblingGroupBy)
            }
        }
    }

    // Incremented when a sibling PTR completes, forces lazy-load effects to re-run
    var siblingReloadTrigger by remember { mutableStateOf(0) }

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
            if (uuid in enrichedUuids) return@filter false
            val existing = loadedResources[uuid]
            when (existing) {
                is Sample  -> existing.links == null
                is Dataset -> existing.links == null
                else       -> true
            }
        }

        // Load relationships (but NOT thumbnails yet) for pages in the window
        launch(Dispatchers.IO) {
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
                        else       -> false
                    }
                ) {
                    withContext(Dispatchers.Main) {
                        enrichedUuids.add(uuid)
                        loadedResources[uuid] = cached
                    }
                    return@forEachIndexed
                }

                try {
                    val enriched: CrucibleResource? = when (pageResource) {
                        is Sample -> ApiClient.service.getSample(uuid).body()
                        is Dataset -> kotlinx.coroutines.coroutineScope {
                            val dsDeferred   = async { ApiClient.service.getDataset(uuid, includeMetadata = true) }
                            val metaDeferred = async { try { ApiClient.service.getDatasetScientificMetadata(uuid) } catch (_: Exception) { null } }
                            val ds   = dsDeferred.await().body()
                            val meta = metaDeferred.await()?.takeIf { it.isSuccessful }?.body()
                            if (ds != null && !meta.isNullOrEmpty()) ds.copy(scientificMetadata = meta) else ds
                        }
                    }
                    withContext(Dispatchers.Main) {
                        enrichedUuids.add(uuid)
                        if (enriched != null) {
                            loadedResources[uuid] = enriched
                            CacheManager.cacheResource(uuid, enriched)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ResourceDetail", "Failed to load relationships for $uuid", e)
                }
            }
        }

        // Clean up resources far from current page (keep memory usage bounded)
        launch {
            val cleanupThreshold = 20
            val keysToRemove = loadedResources.keys.filter { uuid ->
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > cleanupThreshold
            }
            keysToRemove.forEach { uuid ->
                loadedResources.remove(uuid)
                loadedThumbnails.remove(uuid)
                enrichedUuids.remove(uuid)
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
        launch(Dispatchers.IO) {
            thumbnailPages.forEach { pageIndex ->
                val pageResource = siblingList[pageIndex]
                val uuid = pageResource.uniqueId

                // Skip if already loaded or not a dataset
                if (loadedThumbnails.containsKey(uuid) || pageResource !is Dataset) return@forEach

                // Prefer the ViewModel-cached thumbnails over a fresh API call
                val cached = CacheManager.getThumbnails(uuid)
                if (cached != null) {
                    withContext(Dispatchers.Main) { loadedThumbnails[uuid] = cached }
                    return@forEach
                }

                try {
                    val thumbResp = ApiClient.service.getThumbnails(uuid)
                    val thumbs = if (thumbResp.isSuccessful) {
                        thumbResp.body()?.map { "data:image/png;base64,${it.thumbnailB64}" } ?: emptyList()
                    } else {
                        emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        loadedThumbnails[uuid] = thumbs
                        if (thumbs.isNotEmpty()) CacheManager.cacheThumbnails(uuid, thumbs)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ResourceDetail", "Failed to load thumbnails for $uuid", e)
                    withContext(Dispatchers.Main) { loadedThumbnails[uuid] = emptyList() }
                }
            }
        }

        // Clean up thumbnails outside current ± 3 range to prevent memory bloat
        launch {
            val thumbnailCleanupThreshold = 3
            val thumbnailKeysToRemove = loadedThumbnails.keys.filter { uuid ->
                if (uuid == resource.uniqueId) return@filter false
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > thumbnailCleanupThreshold
            }
            thumbnailKeysToRemove.forEach { uuid ->
                loadedThumbnails.remove(uuid)
            }
        }
    }

    LaunchedEffect(resource.uniqueId) {
        onSaveToHistory(resource.uniqueId, resource.name)
    }
    val scope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()
    // True only when a sibling (not the primary resource) was pull-to-refreshed.
    // Guards siblingReloadTrigger so it only fires on actual sibling PTRs.
    var isSiblingRefreshPending by remember { mutableStateOf(false) }

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            val currentUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId
                ?: resource.uniqueId
            if (currentUuid != mfid) {
                // Refreshing a sibling — clear stale local state so the pager
                // picks up the fresh data once the ViewModel signals isRefreshing=false.
                loadedResources.remove(currentUuid)
                loadedThumbnails.remove(currentUuid)
                enrichedUuids.remove(currentUuid)
                isSiblingRefreshPending = true
            }
            onRefresh(currentUuid)
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullRefreshState.endRefresh()
            // Only re-trigger lazy loading when a sibling was actually refreshed
            if (isSiblingRefreshPending) {
                isSiblingRefreshPending = false
                siblingReloadTrigger++
            }
        }
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (resource is Sample) "Sample" else "Dataset",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((-4).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                        }
                        Box {
                            IconButton(
                                onClick = { overflowMenuExpanded = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { overflowMenuExpanded = false; showEditSheet = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Default.CopyAll, contentDescription = null) },
                                    onClick = { overflowMenuExpanded = false; onDuplicate(currentDisplayResource) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Link") },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                    onClick = { overflowMenuExpanded = false; showLinkSheet = true }
                                )
                                val deletionRequest = when (val r = currentDisplayResource) {
                                    is Sample -> r.deletionRequest
                                    is Dataset -> r.deletionRequest
                                }
                                val deletionStatus = deletionRequest?.get("status") as? String
                                DropdownMenuItem(
                                    text = { Text(if (deletionStatus != null) "Deletion ${deletionStatus.replaceFirstChar { it.uppercase() }}" else "Request deletion") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                    enabled = deletionStatus == null,
                                    onClick = { overflowMenuExpanded = false; showDeletionDialog = true }
                                )
                                val projectId = when (val r = currentDisplayResource) {
                                    is Sample -> r.projectId
                                    is Dataset -> r.projectId
                                }
                                if (projectId != null && graphExplorerUrl.isNotBlank()) {
                                    val webUrl = when (currentDisplayResource) {
                                        is Sample  -> "$graphExplorerUrl/$projectId/sample-graph/${currentDisplayResource.uniqueId}"
                                        is Dataset -> "$graphExplorerUrl/$projectId/dataset/${currentDisplayResource.uniqueId}"
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Open in web") },
                                        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, webUrl)
                                                putExtra(Intent.EXTRA_SUBJECT, currentDisplayResource.name)
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                        }
                                    )
                                }
                                // Sibling grouping — only when resource belongs to a project
                                val resourceProjectId = when (val r = currentDisplayResource) {
                                    is Sample -> r.projectId
                                    is Dataset -> r.projectId
                                }
                                if (resourceProjectId != null) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    val groupLabel = siblingGroupLabel(activeSiblingGroupBy, currentDisplayResource)
                                    DropdownMenuItem(
                                        text = { Text("Siblings: $groupLabel") },
                                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            showSiblingGroupDialog = true
                                        }
                                    )
                                }
                            }
                        }
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
            // HorizontalPager for seamless sibling navigation with snap
            if (siblingList.isNotEmpty() && siblingIndex >= 0) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshState.nestedScrollConnection),
                    beyondBoundsPageCount = 1, // Pre-render adjacent pages
                    userScrollEnabled = true
                ) { pageIndex ->
                    if (pageIndex >= siblingList.size) return@HorizontalPager
                    val pageResource = siblingList[pageIndex]
                    val isCurrentResource = pageResource.uniqueId == resource.uniqueId

                    key(pageResource.uniqueId) {
                        // Each page needs its own independent scroll state
                        val listState = rememberLazyListState()
                        val showScrollToTop = listState.firstVisibleItemIndex > 0
                        // Scope card state to this page's resource so expanded/collapsed state
                        // doesn't leak across pages sharing the same callback origin.
                        val pageId = pageResource.uniqueId
                        val pageGetCardState: (String) -> Boolean = { key -> getCardState("$pageId/$key") }
                        val pageSetCardState: (String, Boolean) -> Unit = { key, value -> onCardStateChange("$pageId/$key", value) }

                        Box(modifier = Modifier.fillMaxSize()) {
            // Show loading only when actually waiting for data
            AnimatedVisibility(
                visible = isCurrentResource && resource.uniqueId != mfid && !isRefreshing,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                LoadingContent(title = "Loading Resource")
            }

            // Show content: immediately when we have the resource data
            AnimatedVisibility(
                visible = !isCurrentResource || resource.uniqueId == mfid,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer { translationY = pullRefreshState.verticalOffset },
                    verticalArrangement = Arrangement.Top
                ) {
                    item(key = "basic_info_$pageIndex") {
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
                    }

                // Use fully loaded resource if available, otherwise fall back to basic or current
                val displayResource = loadedResources[pageResource.uniqueId]
                    ?: if (isCurrentResource) resource else pageResource
                val displayThumbnails = loadedThumbnails[pageResource.uniqueId]
                    ?: if (isCurrentResource) thumbnails else emptyList()
                when (displayResource) {
                    is Sample -> item(key = "type_details_$pageIndex") {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            SampleDetailsCard(
                                sample = displayResource,
                                onProjectClick = onNavigateToProject,
                                onShowQr = { showQrDialog = true },
                                initialAdvanced = pageGetCardState("advanced"),
                                onAdvancedChange = { pageSetCardState("advanced", it) }
                            )
                        }
                    }
                    is Dataset -> item(key = "type_details_$pageIndex") {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            DatasetDetailsCard(
                                dataset = displayResource,
                                onProjectClick = onNavigateToProject,
                                onInstrumentClick = onNavigateToInstrument,
                                onShowQr = { showQrDialog = true },
                                initialAdvanced = pageGetCardState("advanced"),
                                onAdvancedChange = { pageSetCardState("advanced", it) }
                            )
                        }
                    }
                }

                when (displayResource) {
                    is Dataset -> {
                        item(key = "thumbnails_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayThumbnails.isNotEmpty(),
                                enter = expandVertically() + fadeIn(animationSpec = tween(300)),
                                exit = ExitTransition.None
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                        ThumbnailsSection(displayThumbnails)
                                    }
                                }
                            }
                        }
                        item(key = "linked_samples_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "associated" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    LinkedSamplesCard(
                                        samples = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkDatasetSample(displayResource.uniqueId, uuid)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("linked_samples"),
                                        onExpandChange = { pageSetCardState("linked_samples", it) }
                                    )
                                }
                            }
                        }
                        item(key = "parent_datasets_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "parent" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    ParentDatasetsCard(
                                        parents = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkDatasets(uuid, displayResource.uniqueId)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("parent_datasets"),
                                        onExpandChange = { pageSetCardState("parent_datasets", it) }
                                    )
                                }
                            }
                        }
                        item(key = "child_datasets_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "child" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    ChildDatasetsCard(
                                        children = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkDatasets(displayResource.uniqueId, uuid)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("child_datasets"),
                                        onExpandChange = { pageSetCardState("child_datasets", it) }
                                    )
                                }
                            }
                        }
                        item(key = "scientific_metadata_$pageIndex") {
                            AnimatedVisibility(
                                visible = !displayResource.scientificMetadata.isNullOrEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
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
                        item(key = "keywords_dataset_$pageIndex") {
                            AnimatedVisibility(
                                visible = !displayResource.keywords.isNullOrEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    KeywordsCard(displayResource.keywords.orEmpty())
                                }
                            }
                        }
                    }
                    is Sample -> {
                        item(key = "parent_samples_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "parent" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    ParentSamplesCard(
                                        parents = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkSamples(uuid, displayResource.uniqueId)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("parent_samples"),
                                        onExpandChange = { pageSetCardState("parent_samples", it) }
                                    )
                                }
                            }
                        }
                        item(key = "child_samples_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "sample" && it.relationship == "child" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    ChildSamplesCard(
                                        children = displayResource.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkSamples(displayResource.uniqueId, uuid)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("child_samples"),
                                        onExpandChange = { pageSetCardState("child_samples", it) }
                                    )
                                }
                            }
                        }
                        item(key = "linked_datasets_$pageIndex") {
                            AnimatedVisibility(
                                visible = displayResource.links?.any { it.resourceType == "dataset" && it.relationship == "associated" } == true,
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    LinkedDatasetsCard(
                                        datasets = displayResource.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                        onNavigateToResource = onNavigateToResource,
                                        onUnlink = { uuid, name ->
                                            pendingUnlink = UnlinkRequest(name) {
                                                ApiClient.service.unlinkDatasetSample(uuid, displayResource.uniqueId)
                                            }
                                        },
                                        initialExpanded = pageGetCardState("linked_datasets"),
                                        onExpandChange = { pageSetCardState("linked_datasets", it) }
                                    )
                                }
                            }
                        }
                        item(key = "keywords_sample_$pageIndex") {
                            AnimatedVisibility(
                                visible = !displayResource.keywords.isNullOrEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = ExitTransition.None
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    KeywordsCard(displayResource.keywords.orEmpty())
                                }
                            }
                        }
                    }
                }

                val ageMin = CacheManager.getResourceAgeMinutes(resource.uniqueId)
                if (ageMin != null) {
                    item(key = "cache_age") {
                        Text(
                            text = "Cached ${ageMin}m ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                } // end LazyColumn items
            } // end LazyColumn

                        // Pull-to-refresh indicator with spring animation
                        AnimatedPullToRefreshIndicator(
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        // Scroll-to-top button
                        ScrollToTopButton(
                            visible = showScrollToTop,
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
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
                // Fallback when siblings haven't loaded yet
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    // Show loading or error state
                    AnimatedVisibility(
                        visible = resource.uniqueId != mfid || isRefreshing,
                        enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 200))
                    ) {
                        LoadingContent(title = "Loading Resource")
                    }

                    // Pull-to-refresh indicator
                    AnimatedPullToRefreshIndicator(
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            } // end else
        } // end outer Box
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
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
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
                                onClick = {
                                    activeSiblingGroupBy = value
                                    showSiblingGroupDialog = false
                                }
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
        crucible.lens.ui.common.EditResourceSheet(
            resource = currentDisplayResource,
            onDismiss = { showEditSheet = false },
            onSaved = { showEditSheet = false; onRefresh(currentDisplayResource.uniqueId) }
        )
    }
    if (showLinkSheet) {
        crucible.lens.ui.common.LinkResourceSheet(
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
            icon = { Icon(Icons.Default.LinkOff, contentDescription = null) },
            title = { Text("Unlink resource") },
            text = { Text("Remove link to \"${req.name}\"? The resources themselves will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isUnlinking = true
                            try {
                                req.action()
                                pendingUnlink = null
                                onRefresh(currentDisplayResource.uniqueId)
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

@Composable
private fun ResourceTypeBadge(resource: CrucibleResource) {
    val (icon, label, color) = when (resource) {
        is Sample -> Triple(Icons.Default.Science, "Sample", MaterialTheme.colorScheme.primary)
        is Dataset -> Triple(Icons.Default.DataObject, "Dataset", MaterialTheme.colorScheme.secondary)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BasicInfoCard(
    resource: CrucibleResource,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    currentIndex: Int = -1,
    totalCount: Int = 0,
    siblingsResolved: Boolean = true
) {
    Card(border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Name — full card width, not squeezed by chevrons
            AnimatedContent(
                targetState = resource.name,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                },
                label = "resource_name"
            ) { name ->
                val nameScrollState = rememberScrollState()
                var nameOverflows by remember(name) { mutableStateOf(false) }
                val showRightFade = nameOverflows && nameScrollState.canScrollForward
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            if (showRightFade) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = size.width * 0.75f,
                                        endX = size.width
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = if (nameOverflows) TextAlign.Start else TextAlign.Center,
                        onTextLayout = { if (it.hasVisualOverflow) nameOverflows = true },
                        modifier = if (nameOverflows) Modifier.horizontalScroll(nameScrollState)
                                   else Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Navigation row — chevrons + counter only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onPrev?.invoke() },
                    enabled = onPrev != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous sample",
                        tint = if (onPrev != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
                // Animated counter — placeholder while siblings are loading
                val counterText = when {
                    !siblingsResolved || currentIndex < 0 || totalCount == 0 -> "-- / --"
                    else -> "${currentIndex + 1} / $totalCount"
                }
                AnimatedContent(
                    targetState = counterText,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 200))
                    },
                    label = "sibling_counter"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onNext?.invoke() },
                    enabled = onNext != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next sample",
                        tint = if (onNext != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailsSection(thumbnails: List<String>) {
    thumbnails.forEachIndexed { index, thumbnail ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            var imageState by remember { mutableStateOf<String?>(null) }

            // Extract base64 data from data URI (format: "data:image/png;base64,<base64_string>")
            val base64Data = remember(thumbnail) {
                try {
                    if (thumbnail.startsWith("data:image/")) {
                        val base64String = thumbnail.substringAfter("base64,")
                        Base64.decode(base64String, Base64.DEFAULT)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ResourceDetailScreen", "Failed to decode thumbnail ${index + 1}", e)
                    null
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                contentAlignment = Alignment.Center
            ) {
                if (base64Data != null) {
                    AsyncImage(
                        model = base64Data,
                        contentDescription = "Dataset image ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        onLoading = {
                            imageState = "loading"
                        },
                        onSuccess = {
                            imageState = "success"
                        },
                        onError = {
                            imageState = "error: ${it.result.throwable.message}"
                        }
                    )
                } else {
                    imageState = "error: Failed to decode base64"
                }

                // Show loading/error overlay
                when {
                    imageState == "loading" -> {
                        CircularProgressIndicator()
                    }
                    imageState?.startsWith("error") == true -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Failed to load image",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleDetailsCard(
    sample: Sample,
    onProjectClick: (String) -> Unit,
    onShowQr: () -> Unit = {},
    initialAdvanced: Boolean = false,
    onAdvancedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(initialAdvanced) }
    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            val projectId = sample.projectId

            // Header: title + action icons (copy, open, share, QR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sample Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MFID", sample.uniqueId))
                            Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy MFID",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onShowQr, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Show QR Code",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // MFID left-aligned below title
            Text(
                text = sample.uniqueId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Deletion warning
            val sampleDeletionStatus = sample.deletionRequest?.get("status") as? String
            if (sampleDeletionStatus != null) {
                Surface(
                    color = when (sampleDeletionStatus) {
                        "approved" -> MaterialTheme.colorScheme.errorContainer
                        "pending"  -> MaterialTheme.colorScheme.tertiaryContainer
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "Deletion ${sampleDeletionStatus.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Basic fields
            InfoRow(icon = Icons.Default.Category, label = "Type", value = sample.sampleType ?: "None")
            if (projectId != null) {
                ClickableInfoRow(icon = Icons.Default.Folder, label = "Project", value = projectId, onClick = { onProjectClick(projectId) })
            } else {
                InfoRow(icon = Icons.Default.Folder, label = "Project", value = "None")
            }
            InfoRow(icon = Icons.Default.Schedule, label = "Timestamp", value = formatDateTime(sample.timestamp))
            InfoRow(icon = Icons.AutoMirrored.Filled.Notes, label = "Description", value = sample.description?.takeIf { it.isNotBlank() } ?: "None", verticalAlignment = Alignment.Top)

            // Advanced fields
            if (advanced) {
                Column {
                    if (sample.ownerOrcid != null) {
                        ClickableInfoRow(
                            icon = Icons.Default.Person,
                            label = "Owner ORCID",
                            value = sample.ownerOrcid,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orcid.org/${sample.ownerOrcid}")))
                            }
                        )
                    } else {
                        InfoRow(icon = Icons.Default.Person, label = "Owner ORCID", value = "None")
                    }
                    InfoRow(icon = Icons.Default.Schedule, label = "Created at", value = formatDateTime(sample.creationTime))
                    InfoRow(icon = Icons.Default.Update, label = "Modified at", value = formatDateTime(sample.modificationTime))
                    InfoRow(icon = Icons.Default.Numbers, label = "Internal ID", value = sample.internalId?.toString() ?: "None")
                }
            }

            // Advanced/Basic toggle at bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clickable { val new = !advanced; advanced = new; onAdvancedChange(new) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (advanced) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (advanced) "Basic" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DatasetDetailsCard(
    dataset: Dataset,
    onProjectClick: (String) -> Unit,
    onInstrumentClick: (String) -> Unit = {},
    onShowQr: () -> Unit = {},
    initialAdvanced: Boolean = false,
    onAdvancedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(initialAdvanced) }

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            val projectId = dataset.projectId

            // Header: title + action icons (copy, open, share, QR)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dataset Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MFID", dataset.uniqueId))
                            Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy MFID",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onShowQr, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Show QR Code",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // MFID left-aligned below title
            Text(
                text = dataset.uniqueId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Deletion warning
            val datasetDeletionStatus = dataset.deletionRequest?.get("status") as? String
            if (datasetDeletionStatus != null) {
                Surface(
                    color = when (datasetDeletionStatus) {
                        "approved" -> MaterialTheme.colorScheme.errorContainer
                        "pending"  -> MaterialTheme.colorScheme.tertiaryContainer
                        else       -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "Deletion ${datasetDeletionStatus.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Basic fields
            InfoRow(icon = Icons.Default.Science, label = "Measurement", value = dataset.measurement ?: "None")
            InfoRow(icon = Icons.Default.PlayCircle, label = "Session", value = dataset.sessionName ?: "None")
            if (dataset.instrumentName != null) {
                val instrumentScope = rememberCoroutineScope()
                ClickableInfoRow(
                    icon = Icons.Default.Build,
                    label = "Instrument",
                    value = dataset.instrumentName,
                    onClick = {
                        instrumentScope.launch {
                            val instruments = crucible.lens.data.cache.CacheManager.getInstruments()
                                ?: withContext(Dispatchers.IO) {
                                    ApiClient.service.getInstruments().body()
                                        ?.also { crucible.lens.data.cache.CacheManager.cacheInstruments(it) }
                                }
                            val instrument = instruments?.find { it.instrumentName == dataset.instrumentName }
                            if (instrument != null) onInstrumentClick(instrument.uniqueId)
                        }
                    }
                )
            } else {
                InfoRow(icon = Icons.Default.Build, label = "Instrument", value = "None")
            }
            if (projectId != null) {
                ClickableInfoRow(icon = Icons.Default.Folder, label = "Project", value = projectId, onClick = { onProjectClick(projectId) })
            } else {
                InfoRow(icon = Icons.Default.Folder, label = "Project", value = "None")
            }
            InfoRow(icon = Icons.Default.Schedule, label = "Timestamp", value = formatDateTime(dataset.timestamp))

            // Advanced fields
            if (advanced) {
                Column {
                    if (dataset.ownerOrcid != null) {
                        ClickableInfoRow(
                            icon = Icons.Default.Person,
                            label = "Owner ORCID",
                            value = dataset.ownerOrcid,
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orcid.org/${dataset.ownerOrcid}")))
                            }
                        )
                    } else {
                        InfoRow(icon = Icons.Default.Person, label = "Owner ORCID", value = "None")
                    }
                    InfoRow(
                        icon = when (dataset.isPublic) {
                            true  -> Icons.Default.Public
                            false -> Icons.Default.Lock
                            null  -> Icons.AutoMirrored.Filled.HelpOutline
                        },
                        label = "Visibility",
                        value = when (dataset.isPublic) {
                            true  -> "Public"
                            false -> "Private"
                            null  -> "None"
                        }
                    )
                    InfoRow(icon = Icons.Default.Description, label = "Format", value = dataset.dataFormat ?: "None")
                    InfoRow(icon = Icons.Default.Label, label = "Data Type", value = dataset.dataType ?: "None")
                    InfoRow(icon = Icons.Default.Storage, label = "Size", value = dataset.size?.let { formatFileSize(it) } ?: "None")
                    InfoRow(icon = Icons.Default.FolderOpen, label = "Source Folder", value = dataset.sourceFolder?.takeIf { it.isNotBlank() } ?: "None")
                    InfoRow(icon = Icons.Default.Security, label = "SHA-256", value = dataset.sha256Hash ?: "None")
                    InfoRow(icon = Icons.Default.Schedule, label = "Created at", value = formatDateTime(dataset.creationTime))
                    InfoRow(icon = Icons.Default.Update, label = "Modified at", value = formatDateTime(dataset.modificationTime))
                }
            }

            // Advanced/Basic toggle at bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clickable { val new = !advanced; advanced = new; onAdvancedChange(new) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (advanced) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (advanced) "Basic" else "Advanced",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ScientificMetadataCard(
    metadata: Map<String, Any?>,
    initialExpanded: Boolean = false,
    initialExpandAll: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onExpandAllChange: (Boolean) -> Unit = {}
) {
    // The API wraps actual data inside a "scientific_metadata" key — unwrap it if present.
    @Suppress("UNCHECKED_CAST")
    val displayMetadata = (metadata["scientific_metadata"] as? Map<String, Any?>)
        ?.takeIf { it.isNotEmpty() }
        ?: metadata

    var expanded by remember { mutableStateOf(initialExpanded) }
    var expandAll by remember { mutableStateOf(initialExpandAll) }

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scientific Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (expanded) {
                    TextButton(
                        onClick = { val new = !expandAll; expandAll = new; onExpandAllChange(new) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (expandAll) "Collapse All" else "Expand All",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTree(displayMetadata, indentLevel = 0, expandAll = expandAll)
            }
        }
    }
}

@Composable
private fun MetadataTree(data: Map<String, Any?>, indentLevel: Int, expandAll: Boolean = false) {
    val entries = data.entries.toList()
    for ((index, entry) in entries.withIndex()) {
        val (entryKey, entryValue) = entry
        key(entryKey) {
            when (entryValue) {
                is Map<*, *> -> {
                    var expanded by remember(expandAll) { mutableStateOf(expandAll) }
                    val nodeRotation by animateFloatAsState(
                        targetValue = if (expanded) 0f else -90f,
                        animationSpec = tween(150),
                        label = "node_expand_icon"
                    )

                    Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(150))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 0.dp, vertical = 4.dp)
                                .absolutePadding(left = (indentLevel * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).rotate(nodeRotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatKey(entryKey),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (expanded) {
                            @Suppress("UNCHECKED_CAST")
                            val nestedData = entryValue as Map<String, Any?>
                            MetadataTree(nestedData, indentLevel + 1, expandAll)
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 2.dp)
                            .absolutePadding(left = (indentLevel * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = formatKey(entryKey),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.35f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatValue(entryValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
            }

            if (index < entries.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .absolutePadding(left = (indentLevel * 16).dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatKey(key: String): String {
    return key.replace("_", " ")
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}

private fun formatValue(value: Any?): String {
    return when (value) {
        null -> "—"
        is Number -> {
            val num = value.toDouble()
            if (num == num.toLong().toDouble()) {
                num.toLong().toString()
            } else {
                "%.4f".format(num)
            }
        }
        is Boolean -> if (value) "Yes" else "No"
        is List<*> -> {
            if (value.isEmpty()) {
                "[]"
            } else if (value.all { it is Number || it is String || it is Boolean }) {
                value.joinToString(", ")
            } else {
                value.joinToString("\n") { formatValue(it) }
            }
        }
        is Map<*, *> -> {
            // This shouldn't be called for nested maps since we handle them separately
            // But just in case, format as JSON-like
            "{...}"
        }
        else -> value.toString()
    }
}

@Composable
private fun KeywordsCard(keywords: List<String>) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keywords",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    AssistChip(
                        onClick = { },
                        label = { Text(keyword) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentDatasetsCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent Datasets (${parents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in parents) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildDatasetsCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Child Datasets (${children.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in children) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedSamplesCard(
    samples: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Linked Samples (${samples.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in samples) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedDatasetsCard(
    datasets: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Dataset, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Linked Datasets (${datasets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in datasets) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentSamplesCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent Samples (${parents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in parents) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildSamplesCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Child Samples (${children.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in children) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = link.name,
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ResourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (subtitle != null) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DeletionRequestDialog(
    resource: CrucibleResource,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
        title = { Text("Request deletion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Submit a deletion request for \"${resource.name}\". An admin will review it.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
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
                            val resp = crucible.lens.data.api.ApiClient.service.requestDeletion(
                                resourceId = resource.uniqueId,
                                reason = reason.trim().ifBlank { null }
                            )
                            if (resp.isSuccessful) onSubmitted()
                            else errorMsg = "Failed (${resp.code()}) — a request may already exist"
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

private fun formatRelativeTime(raw: String?): String {
    if (raw == null) return "None"
    val s = raw.trim()
    val instant = try {
        java.time.OffsetDateTime.parse(s).toInstant()
    } catch (_: Exception) { try {
        java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC)
    } catch (_: Exception) { null } } ?: return formatDateTime(raw)

    val diff = java.time.Duration.between(instant, java.time.Instant.now())
    val abs  = kotlin.math.abs(diff.seconds)
    val past = diff.seconds >= 0
    val suffix = if (past) "ago" else "from now"

    return when {
        abs < 60              -> "just now"
        abs < 3_600           -> "${abs / 60} min $suffix"
        abs < 86_400          -> "${abs / 3_600} h $suffix"
        abs < 7 * 86_400      -> "${abs / 86_400} day${if (abs / 86_400 != 1L) "s" else ""} $suffix"
        abs < 30 * 86_400     -> "${abs / (7 * 86_400)} week${if (abs / (7 * 86_400) != 1L) "s" else ""} $suffix"
        abs < 365 * 86_400    -> "${abs / (30 * 86_400)} month${if (abs / (30 * 86_400) != 1L) "s" else ""} $suffix"
        else                  -> "${abs / (365 * 86_400)} year${if (abs / (365 * 86_400) != 1L) "s" else ""} $suffix"
    }
}

private fun formatDateTime(raw: String?): String {
    if (raw == null) return "None"
    val s = raw.trim()
    val fmtDateTime = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", java.util.Locale.getDefault())
    val fmtDate     = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy",           java.util.Locale.getDefault())
    return try {
        // Full datetime with timezone offset (Z, +HH:MM, etc.) — any fractional-second length
        fmtDateTime.format(java.time.OffsetDateTime.parse(s).toLocalDateTime())
    } catch (_: Exception) { try {
        // Full datetime without timezone
        fmtDateTime.format(java.time.LocalDateTime.parse(s))
    } catch (_: Exception) { try {
        // Date only
        fmtDate.format(java.time.LocalDate.parse(s))
    } catch (_: Exception) {
        // Compact date with AM/PM suffix: yyyyMMdd_am or yyyyMMdd_pm
        val match = Regex("""(\d{8})_(am|pm)""", RegexOption.IGNORE_CASE).matchEntire(s)
        if (match != null) try {
            val date = java.time.LocalDate.parse(
                match.groupValues[1],
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
            )
            fmtDate.format(date) + " · ${match.groupValues[2].uppercase()}"
        } catch (_: Exception) { raw }
        else raw
    } } }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}

@Composable
private fun CopyableInfoRow(
    context: Context,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = verticalAlignment
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )
    }
}

@Composable
private fun ClickableInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.3f)
        )
        Row(
            modifier = Modifier.weight(0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open link",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
