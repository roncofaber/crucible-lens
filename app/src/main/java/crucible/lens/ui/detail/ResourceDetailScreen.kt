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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import crucible.lens.data.api.ApiClient
import android.util.Base64
import android.util.Log
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetReference
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleReference
import crucible.lens.ui.common.AnimatedPullToRefreshIndicator
import crucible.lens.ui.common.LoadingMessage
import crucible.lens.ui.common.QrCodeDialog
import crucible.lens.ui.common.QrCodeDialogWithNavigation
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.ShareCardGenerator
import crucible.lens.ui.common.openUrlInBrowser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ResourceDetailScreen(
    resource: CrucibleResource,
    thumbnails: List<String>,
    mfid: String = resource.uniqueId,
    isRefreshing: Boolean = false,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onSearch: () -> Unit = {},
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSibling: (uuid: String) -> Unit = onNavigateToResource,
    onSaveToHistory: (uuid: String, name: String) -> Unit = { _, _ -> },
    darkTheme: Boolean,
    getCardState: (key: String) -> Boolean = { false },
    onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val bannerColorInt = MaterialTheme.colorScheme.primary.toArgb()
    var showQrDialog by remember { mutableStateOf(false) }

    // Cache for loaded resources with full metadata and relationships
    val loadedResources = remember { mutableStateMapOf<String, CrucibleResource>() }
    val loadedThumbnails = remember { mutableStateMapOf<String, List<String>>() }
    val loadedRelationships = remember { mutableStateMapOf<String, Boolean>() } // Track which have relationships loaded

    // Sibling navigation: same type within the same project
    // Samples grouped by sampleType, Datasets grouped by measurement
    // Initialize with cached data to avoid flash of "-- / --" when navigating
    var sameTypeSamples by remember(resource) {
        mutableStateOf(
            if (resource is Sample) {
                val projectId = resource.projectId
                if (projectId != null) {
                    CacheManager.getProjectSamples(projectId)
                        ?.filter { it.sampleType == resource.sampleType }
                        ?.sortedBy { it.internalId ?: Int.MAX_VALUE }
                        ?: emptyList()
                } else emptyList()
            } else emptyList()
        )
    }
    var sameTypeDatasets by remember(resource) {
        mutableStateOf(
            if (resource is Dataset) {
                val projectId = resource.projectId
                if (projectId != null) {
                    CacheManager.getProjectDatasets(projectId)
                        ?.filter { it.measurement == resource.measurement }
                        ?.sortedBy { it.internalId ?: Int.MAX_VALUE }
                        ?: emptyList()
                } else emptyList()
            } else emptyList()
        )
    }
    // Initialize current resource immediately (fast for QR scanning)
    LaunchedEffect(resource) {
        // Store current resource first for instant display
        loadedResources[resource.uniqueId] = resource
    }

    // Batch load all project resources in background (for sibling navigation)
    LaunchedEffect(resource) {
        when (resource) {
            is Sample -> {
                val projectId = resource.projectId ?: return@LaunchedEffect
                fun List<Sample>.filterAndSort() = filter { it.sampleType == resource.sampleType }
                    .sortedBy { it.internalId ?: Int.MAX_VALUE }
                val cached = CacheManager.getProjectSamples(projectId)
                if (cached != null) {
                    // Use cache immediately - no blocking
                    sameTypeSamples = cached.filterAndSort()
                    cached.forEach { loadedResources[it.uniqueId] = it }
                } else {
                    // Batch load in background - doesn't block initial display
                    launch(Dispatchers.IO) {
                        try {
                            val response = ApiClient.service.getSamplesByProject(projectId)
                            if (response.isSuccessful) {
                                val all = response.body() ?: emptyList()
                                CacheManager.cacheProjectSamples(projectId, all)
                                sameTypeSamples = all.filterAndSort()
                                all.forEach { loadedResources[it.uniqueId] = it }
                            }
                        } catch (e: Exception) {
                            Log.e("ResourceDetail", "Failed to batch load samples", e)
                        }
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.projectId ?: return@LaunchedEffect
                fun List<Dataset>.filterAndSort() = filter { it.measurement == resource.measurement }
                    .sortedBy { it.internalId ?: Int.MAX_VALUE }
                val cached = CacheManager.getProjectDatasets(projectId)
                if (cached != null) {
                    // Use cache immediately - no blocking
                    sameTypeDatasets = cached.filterAndSort()
                    cached.forEach { loadedResources[it.uniqueId] = it }
                } else {
                    // Batch load in background with metadata - doesn't block initial display
                    launch(Dispatchers.IO) {
                        try {
                            val response = ApiClient.service.getDatasetsByProject(projectId, includeMetadata = true)
                            if (response.isSuccessful) {
                                val all = response.body() ?: emptyList()
                                CacheManager.cacheProjectDatasets(projectId, all)
                                sameTypeDatasets = all.filterAndSort()
                                all.forEach { loadedResources[it.uniqueId] = it }
                            }
                        } catch (e: Exception) {
                            Log.e("ResourceDetail", "Failed to batch load datasets", e)
                        }
                    }
                }
            }
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
        initialPage = siblingIndex.coerceAtLeast(0),
        pageCount = { siblingList.size.coerceAtLeast(1) }
    )

    // Sync pager with resource changes (when navigating via buttons or external nav)
    LaunchedEffect(siblingIndex) {
        if (siblingIndex >= 0 && pagerState.currentPage != siblingIndex) {
            pagerState.scrollToPage(siblingIndex)
        }
    }

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
                onNavigateToSibling(targetResource.uniqueId)
            }
        }
    }

    // Lazy load relationships for ~10 neighbors (current ± 5)
    // Thumbnails loaded separately with more conservative strategy
    LaunchedEffect(pagerState.currentPage, siblingList) {
        if (siblingList.isEmpty()) return@LaunchedEffect

        val currentPage = pagerState.currentPage
        val relationshipRange = 5 // Load relationships for current ± 5 pages

        // Build list of pages to load relationships (current ± 5)
        val pagesToLoad = mutableListOf<Int>()
        pagesToLoad.add(currentPage) // Current page (priority)

        // Add neighbors in expanding order: +1, -1, +2, -2, +3, -3, etc.
        for (offset in 1..relationshipRange) {
            if (currentPage + offset in siblingList.indices) {
                pagesToLoad.add(currentPage + offset)
            }
            if (currentPage - offset in siblingList.indices) {
                pagesToLoad.add(currentPage - offset)
            }
        }

        // Load relationships (but NOT thumbnails yet) for pages in the window
        launch(Dispatchers.IO) {
            pagesToLoad.forEachIndexed { index, pageIndex ->
                val pageResource = siblingList[pageIndex]
                val uuid = pageResource.uniqueId

                // Skip if relationships already loaded
                if (loadedRelationships[uuid] == true) return@forEachIndexed

                // Add small delay between loads (except first few)
                if (index > 2) {
                    kotlinx.coroutines.delay(150)
                }

                try {
                    val baseResource = loadedResources[uuid] ?: pageResource

                    when (baseResource) {
                        is Sample -> {
                            // Load parents and children relationships
                            coroutineScope {
                                val parentsDeferred = async { ApiClient.service.getParentSamples(uuid) }
                                val childrenDeferred = async { ApiClient.service.getChildSamples(uuid) }

                                val parentsResp = parentsDeferred.await()
                                val childrenResp = childrenDeferred.await()

                                val enrichedSample = baseResource.copy(
                                    parentSamples = parentsResp.body()
                                        ?.map { SampleReference(it.uniqueId, it.name) }
                                        ?.distinctBy { it.uniqueId }
                                        ?.sortedBy { it.uniqueId }
                                        ?: baseResource.parentSamples
                                )
                                enrichedSample.childSamples = childrenResp.body()
                                    ?.map { SampleReference(it.uniqueId, it.name) }
                                    ?.distinctBy { it.uniqueId }
                                    ?.sortedBy { it.uniqueId }
                                    ?: emptyList()

                                loadedResources[uuid] = enrichedSample
                                loadedRelationships[uuid] = true
                            }
                        }
                        is Dataset -> {
                            // Load relationships and linked samples (NOT thumbnails)
                            coroutineScope {
                                val parentsDeferred = async { ApiClient.service.getParentDatasets(uuid) }
                                val childrenDeferred = async { ApiClient.service.getChildDatasets(uuid) }
                                val samplesDeferred = async { ApiClient.service.getDatasetSamples(uuid) }

                                val parentsResp = parentsDeferred.await()
                                val childrenResp = childrenDeferred.await()
                                val samplesResp = samplesDeferred.await()

                                var enrichedDataset = baseResource.copy(
                                    samples = samplesResp.body()
                                        ?.map { SampleReference(it.uniqueId, it.name) }
                                        ?.distinctBy { it.uniqueId }
                                        ?.sortedBy { it.uniqueId }
                                        ?: baseResource.samples
                                )
                                enrichedDataset.parentDatasets = parentsResp.body()
                                    ?.map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                    ?.distinctBy { it.uniqueId }
                                    ?.sortedBy { it.uniqueId }
                                    ?: emptyList()
                                enrichedDataset.childDatasets = childrenResp.body()
                                    ?.map { DatasetReference(it.uniqueId, it.name, it.measurement) }
                                    ?.distinctBy { it.uniqueId }
                                    ?.sortedBy { it.uniqueId }
                                    ?: emptyList()

                                loadedResources[uuid] = enrichedDataset
                                loadedRelationships[uuid] = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ResourceDetail", "Failed to load relationships for $uuid", e)
                }
            }
        }

        // Clean up resources far from current page (keep memory usage bounded)
        launch(Dispatchers.IO) {
            val cleanupThreshold = 20 // Keep loaded data within ± 20 pages
            val keysToRemove = loadedRelationships.keys.filter { uuid ->
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > cleanupThreshold
            }
            keysToRemove.forEach { uuid ->
                loadedRelationships.remove(uuid)
                // Keep base resource but remove thumbnails to save memory
                if (uuid != resource.uniqueId) {
                    loadedThumbnails.remove(uuid)
                }
            }
        }
    }

    // Separate effect: Load thumbnails ONLY for current ± 1 pages (much more conservative)
    // Thumbnails are heavy (large base64 strings + decoded bitmaps)
    LaunchedEffect(pagerState.currentPage, siblingList) {
        if (siblingList.isEmpty()) return@LaunchedEffect

        val currentPage = pagerState.currentPage
        val thumbnailRange = 1 // Only load thumbnails for current ± 1 pages

        // Pages that should have thumbnails loaded
        val thumbnailPages = listOf(
            currentPage,
            currentPage - 1,
            currentPage + 1
        ).filter { it in siblingList.indices }

        // Load thumbnails for these pages
        launch(Dispatchers.IO) {
            thumbnailPages.forEach { pageIndex ->
                val pageResource = siblingList[pageIndex]
                val uuid = pageResource.uniqueId

                // Skip if already loaded or not a dataset
                if (loadedThumbnails.containsKey(uuid) || pageResource !is Dataset) return@forEach

                try {
                    val thumbResp = ApiClient.service.getThumbnails(uuid)
                    if (thumbResp.isSuccessful) {
                        val thumbs = thumbResp.body()?.map { "data:image/png;base64,${it.thumbnailB64}" } ?: emptyList()
                        if (thumbs.isNotEmpty()) {
                            loadedThumbnails[uuid] = thumbs
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ResourceDetail", "Failed to load thumbnails for $uuid", e)
                }
            }
        }

        // Aggressively clean up thumbnails for pages outside current ± 2 range
        // This prevents memory bloat from large base64 strings
        launch(Dispatchers.IO) {
            val thumbnailCleanupThreshold = 2
            val thumbnailKeysToRemove = loadedThumbnails.keys.filter { uuid ->
                val index = siblingList.indexOfFirst { it.uniqueId == uuid }
                index >= 0 && abs(index - currentPage) > thumbnailCleanupThreshold && uuid != resource.uniqueId
            }
            thumbnailKeysToRemove.forEach { uuid ->
                loadedThumbnails.remove(uuid)
            }
        }
    }

    // Prev/next siblings for button navigation
    val prevSibling = if (siblingIndex > 0) siblingList.getOrNull(siblingIndex - 1) else null
    val nextSibling = if (siblingIndex >= 0) siblingList.getOrNull(siblingIndex + 1) else null

    LaunchedEffect(resource.uniqueId) {
        onSaveToHistory(resource.uniqueId, resource.name)
    }
    val scope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) { onRefresh() }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshState.endRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (resource is Sample) "Sample" else "Dataset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        // Search button
                        IconButton(
                            onClick = onSearch,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Home button
                        IconButton(
                            onClick = onHome,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(24.dp)
                            )
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
                    // Each page shows content for one sibling resource
                    val pageResource = siblingList[pageIndex]
                    val isCurrentResource = pageResource.uniqueId == resource.uniqueId

                    // Wrap in key() to ensure stable composition per page
                    key(pageResource.uniqueId) {
                        // Each page needs its own independent scroll state
                        val listState = rememberLazyListState()
                        val showScrollToTop = listState.firstVisibleItemIndex > 0

                        Box(modifier = Modifier.fillMaxSize()) {
            // Show loading only when actually waiting for data
            AnimatedVisibility(
                visible = isCurrentResource && resource.uniqueId != mfid && !isRefreshing,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                val loadingMessage = LoadingMessage()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Loading Resource",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AnimatedContent(
                                    targetState = loadingMessage,
                                    transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                                    label = "loading message"
                                ) { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "basic_info_$pageIndex") {
                        // Calculate prev/next for THIS page, not the global resource
                        val hasPrev = pageIndex > 0
                        val hasNext = pageIndex < siblingList.size - 1

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
                            totalCount = siblingList.size
                        )
                    }

                // Use fully loaded resource if available, otherwise fall back to basic or current
                val displayResource = loadedResources[pageResource.uniqueId]
                    ?: if (isCurrentResource) resource else pageResource
                val displayThumbnails = loadedThumbnails[pageResource.uniqueId]
                    ?: if (isCurrentResource) thumbnails else emptyList()
                when (displayResource) {
                    is Sample -> item(key = "type_details_$pageIndex") {
                        SampleDetailsCard(
                            sample = displayResource,
                            onProjectClick = onNavigateToProject,
                            graphExplorerUrl = graphExplorerUrl,
                            darkTheme = darkTheme,
                            bannerColorInt = bannerColorInt,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = getCardState("advanced"),
                            onAdvancedChange = { onCardStateChange("advanced", it) }
                        )
                    }
                    is Dataset -> item(key = "type_details_$pageIndex") {
                        DatasetDetailsCard(
                            dataset = displayResource,
                            onProjectClick = onNavigateToProject,
                            graphExplorerUrl = graphExplorerUrl,
                            darkTheme = darkTheme,
                            bannerColorInt = bannerColorInt,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = getCardState("advanced"),
                            onAdvancedChange = { onCardStateChange("advanced", it) }
                        )
                    }
                }

                when (displayResource) {
                    is Dataset -> {
                        // Always show thumbnail section for current resource (with loading state if needed)
                        item(key = "thumbnails_$pageIndex") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                    // Determine loading state
                                    val thumbnailsLoading = isCurrentResource &&
                                        displayThumbnails.isEmpty() &&
                                        !loadedThumbnails.containsKey(pageResource.uniqueId)

                                    when {
                                        // Thumbnails loaded - show them with animation
                                        displayThumbnails.isNotEmpty() -> {
                                            AnimatedVisibility(
                                                visible = true,
                                                enter = fadeIn(animationSpec = tween(300))
                                            ) {
                                                Column {
                                                    ThumbnailsSection(displayThumbnails)
                                                }
                                            }
                                        }
                                        // Still loading thumbnails - show placeholder
                                        thumbnailsLoading -> {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(200.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(32.dp),
                                                            strokeWidth = 3.dp
                                                        )
                                                        Text(
                                                            "Loading images...",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // No thumbnails available
                                        loadedThumbnails.containsKey(pageResource.uniqueId) ||
                                        loadedResources.containsKey(pageResource.uniqueId) -> {
                                            Card {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "No images available for this dataset",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!displayResource.samples.isNullOrEmpty()) {
                            item(key = "linked_samples_$pageIndex") {
                                LinkedSamplesCard(
                                    samples = displayResource.samples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("linked_samples"),
                                    onExpandChange = { onCardStateChange("linked_samples", it) }
                                )
                            }
                        }
                        if (!displayResource.parentDatasets.isNullOrEmpty()) {
                            item(key = "parent_datasets_$pageIndex") {
                                ParentDatasetsCard(
                                    parents = displayResource.parentDatasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("parent_datasets"),
                                    onExpandChange = { onCardStateChange("parent_datasets", it) }
                                )
                            }
                        }
                        if (!displayResource.childDatasets.isNullOrEmpty()) {
                            item(key = "child_datasets_$pageIndex") {
                                ChildDatasetsCard(
                                    children = displayResource.childDatasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("child_datasets"),
                                    onExpandChange = { onCardStateChange("child_datasets", it) }
                                )
                            }
                        }
                        if (!displayResource.scientificMetadata.isNullOrEmpty()) {
                            item(key = "scientific_metadata_$pageIndex") {
                                ScientificMetadataCard(
                                    metadata = displayResource.scientificMetadata,
                                    initialExpanded = getCardState("sci_meta_expanded"),
                                    initialExpandAll = getCardState("sci_meta_expand_all"),
                                    onExpandedChange = { onCardStateChange("sci_meta_expanded", it) },
                                    onExpandAllChange = { onCardStateChange("sci_meta_expand_all", it) }
                                )
                            }
                        }
                        if (!displayResource.keywords.isNullOrEmpty()) {
                            item(key = "keywords_dataset_$pageIndex") { KeywordsCard(displayResource.keywords) }
                        }
                    }
                    is Sample -> {
                        if (!displayResource.parentSamples.isNullOrEmpty()) {
                            item(key = "parent_samples_$pageIndex") {
                                ParentSamplesCard(
                                    parents = displayResource.parentSamples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("parent_samples"),
                                    onExpandChange = { onCardStateChange("parent_samples", it) }
                                )
                            }
                        }
                        if (!displayResource.childSamples.isNullOrEmpty()) {
                            item(key = "child_samples_$pageIndex") {
                                ChildSamplesCard(
                                    children = displayResource.childSamples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("child_samples"),
                                    onExpandChange = { onCardStateChange("child_samples", it) }
                                )
                            }
                        }
                        if (!displayResource.datasets.isNullOrEmpty()) {
                            item(key = "linked_datasets") {
                                LinkedDatasetsCard(
                                    datasets = displayResource.datasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("linked_datasets"),
                                    onExpandChange = { onCardStateChange("linked_datasets", it) }
                                )
                            }
                        }
                        if (!displayResource.keywords.isNullOrEmpty()) {
                            item(key = "keywords_sample_$pageIndex") { KeywordsCard(displayResource.keywords) }
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                                    Text(
                                        text = "Loading Resource",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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

    // QR Code Dialog with horizontal navigation
    if (showQrDialog) {
        if (siblingList.isNotEmpty() && siblingIndex >= 0) {
            QrCodeDialogWithNavigation(
                resources = siblingList,
                initialIndex = siblingIndex,
                onDismiss = { showQrDialog = false },
                onPageChange = { pageIndex ->
                    // Scroll the main pager to this page (like arrow buttons)
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex)
                    }
                }
            )
        } else {
            // Fallback to simple dialog if no siblings loaded
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
    totalCount: Int = 0
) {
    Card(border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Always show navigation UI to prevent layout shift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onPrev?.invoke() },
                    enabled = onPrev != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous sample",
                        tint = if (onPrev != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Animated resource name
                    AnimatedContent(
                        targetState = resource.name,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 200))
                        },
                        label = "resource_name"
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Animated counter - placeholder while loading, actual count when ready
                    val counterText = when {
                        currentIndex < 0 || totalCount == 0 -> "-- / --"
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
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { onNext?.invoke() },
                    enabled = onNext != null,
                    modifier = Modifier.size(36.dp)
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
    graphExplorerUrl: String,
    darkTheme: Boolean,
    bannerColorInt: Int,
    onShowQr: () -> Unit = {},
    initialAdvanced: Boolean = false,
    onAdvancedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(initialAdvanced) }
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    Row(
                        modifier = Modifier
                            .clickable { val new = !advanced; advanced = new; onAdvancedChange(new) }
                            .padding(horizontal = 4.dp),
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
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = onShowQr, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Show QR Code",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // MFID with copy button on left, share and open in browser on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: MFID + Copy button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = sample.uniqueId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MFID", sample.uniqueId))
                            Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy MFID",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Right side: Share + Open in Browser
                val projectId = sample.projectId
                if (projectId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Share button
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId/sample-graph/${sample.uniqueId}"
                                val shareText = "Check out this sample in Crucible: $url"
                                val imageUri = ShareCardGenerator.generate(context, sample, bannerColorInt, darkTheme)
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    putExtra(Intent.EXTRA_SUBJECT, sample.name)
                                    if (imageUri != null) {
                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                        type = "image/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        type = "text/plain"
                                    }
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Open in Graph Explorer button
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId/sample-graph/${sample.uniqueId}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = "Open in Graph Explorer",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Basic fields
            InfoRow(icon = Icons.AutoMirrored.Filled.Notes, label = "Description", value = sample.description?.takeIf { it.isNotBlank() } ?: "None", verticalAlignment = Alignment.Top)
            InfoRow(icon = Icons.Default.Category, label = "Type", value = sample.sampleType ?: "None")
            val projectId = sample.projectId
            if (projectId != null) {
                ClickableInfoRow(icon = Icons.Default.Folder, label = "Project", value = projectId, onClick = { onProjectClick(projectId) })
            } else {
                InfoRow(icon = Icons.Default.Folder, label = "Project", value = "None")
            }
            InfoRow(icon = Icons.Default.CalendarToday, label = "Created", value = if (advanced) sample.createdAt ?: "None" else formatDateTime(sample.createdAt))

            // Advanced fields
            if (advanced) {
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
            }
        }
    }
}

@Composable
private fun DatasetDetailsCard(
    dataset: Dataset,
    onProjectClick: (String) -> Unit,
    graphExplorerUrl: String,
    darkTheme: Boolean,
    bannerColorInt: Int,
    onShowQr: () -> Unit = {},
    initialAdvanced: Boolean = false,
    onAdvancedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(initialAdvanced) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { val new = !advanced; advanced = new; onAdvancedChange(new) }
                            .padding(horizontal = 4.dp),
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
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(onClick = onShowQr, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = "Show QR Code",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // MFID with copy button on left, share and open in browser on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: MFID + Copy button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = dataset.uniqueId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MFID", dataset.uniqueId))
                            Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy MFID",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Right side: Share + Open in Browser
                val projectId = dataset.projectId
                if (projectId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Share button
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId/dataset/${dataset.uniqueId}"
                                val shareText = "Check out this dataset in Crucible: $url"
                                val imageUri = ShareCardGenerator.generate(context, dataset, bannerColorInt, darkTheme)
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    putExtra(Intent.EXTRA_SUBJECT, dataset.name)
                                    if (imageUri != null) {
                                        putExtra(Intent.EXTRA_STREAM, imageUri)
                                        type = "image/*"
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        type = "text/plain"
                                    }
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Open in Graph Explorer button
                        IconButton(
                            onClick = {
                                val url = "$graphExplorerUrl/$projectId/dataset/${dataset.uniqueId}"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = "Open in Graph Explorer",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Basic fields
            InfoRow(icon = Icons.AutoMirrored.Filled.Notes, label = "Description", value = dataset.description?.takeIf { it.isNotBlank() } ?: "None", verticalAlignment = Alignment.Top)
            InfoRow(icon = Icons.Default.Science, label = "Measurement", value = dataset.measurement ?: "None")
            InfoRow(icon = Icons.Default.Build, label = "Instrument", value = dataset.instrumentName ?: "None")
            val projectId = dataset.projectId
            if (projectId != null) {
                ClickableInfoRow(icon = Icons.Default.Folder, label = "Project", value = projectId, onClick = { onProjectClick(projectId) })
            } else {
                InfoRow(icon = Icons.Default.Folder, label = "Project", value = "None")
            }
            InfoRow(icon = Icons.Default.CalendarToday, label = "Created", value = if (advanced) dataset.createdAt ?: "None" else formatDateTime(dataset.createdAt))

            // Advanced fields
            if (advanced) {
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
                InfoRow(icon = Icons.Default.Tag, label = "Instrument ID", value = dataset.instrumentId?.toString() ?: "None")
                InfoRow(icon = Icons.Default.PlayCircle, label = "Session", value = dataset.sessionName ?: "None")
                InfoRow(icon = Icons.Default.FolderOpen, label = "Source Folder", value = dataset.sourceFolder?.takeIf { it.isNotBlank() } ?: "None")
                InfoRow(icon = Icons.Default.AttachFile, label = "File", value = dataset.fileToUpload ?: "None")
                InfoRow(icon = Icons.Default.Storage, label = "Size", value = dataset.size?.let { formatFileSize(it) } ?: "None")
                if (dataset.jsonLink != null) {
                    ClickableInfoRow(
                        icon = Icons.Default.Link,
                        label = "JSON Link",
                        value = dataset.jsonLink,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dataset.jsonLink)))
                        }
                    )
                } else {
                    InfoRow(icon = Icons.Default.Link, label = "JSON Link", value = "None")
                }
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
                InfoRow(icon = Icons.Default.AccountCircle, label = "Owner User ID", value = dataset.ownerUserId?.toString() ?: "None")
                InfoRow(icon = Icons.Default.Security, label = "SHA-256", value = dataset.sha256Hash ?: "None")
                InfoRow(icon = Icons.Default.Numbers, label = "Internal ID", value = dataset.internalId?.toString() ?: "None")
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
        Column(modifier = Modifier.padding(16.dp)) {
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

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 0.dp, vertical = 4.dp)
                                .absolutePadding(left = (indentLevel * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
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
                            MetadataTree(entryValue as Map<String, Any?>, indentLevel + 1, expandAll)
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
    parents: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (parent in parents) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = parent.datasetName ?: parent.uniqueId.take(16),
                            subtitle = parent.measurement,
                            onClick = { onNavigateToResource(parent.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildDatasetsCard(
    children: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (child in children) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = child.datasetName ?: child.uniqueId.take(16),
                            subtitle = child.measurement,
                            onClick = { onNavigateToResource(child.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedSamplesCard(
    samples: List<SampleReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (sample in samples) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = sample.sampleName ?: sample.uniqueId.take(16),
                            onClick = { onNavigateToResource(sample.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedDatasetsCard(
    datasets: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (dataset in datasets) {
                        ResourceRow(
                            icon = Icons.Default.DataObject,
                            name = dataset.datasetName ?: dataset.uniqueId.take(16),
                            subtitle = dataset.measurement,
                            onClick = { onNavigateToResource(dataset.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentSamplesCard(
    parents: List<SampleReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (parent in parents) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = parent.sampleName ?: parent.uniqueId.take(16),
                            onClick = { onNavigateToResource(parent.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildSamplesCard(
    children: List<SampleReference>,
    onNavigateToResource: (String) -> Unit,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
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
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (child in children) {
                        ResourceRow(
                            icon = Icons.Default.BubbleChart,
                            name = child.sampleName ?: child.uniqueId.take(16),
                            onClick = { onNavigateToResource(child.uniqueId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
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
            horizontalArrangement = Arrangement.SpaceBetween
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
