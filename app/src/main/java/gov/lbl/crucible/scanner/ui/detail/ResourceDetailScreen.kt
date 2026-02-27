package gov.lbl.crucible.scanner.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch
import gov.lbl.crucible.scanner.data.api.ApiClient
import android.util.Base64
import android.util.Log
import gov.lbl.crucible.scanner.data.cache.CacheManager
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.DatasetReference
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.model.SampleReference
import gov.lbl.crucible.scanner.ui.common.QrCodeDialog
import gov.lbl.crucible.scanner.ui.common.ShareCardGenerator
import gov.lbl.crucible.scanner.ui.common.openUrlInBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailScreen(
    resource: CrucibleResource,
    thumbnails: List<String>,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onSearch: () -> Unit = {},
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSibling: (uuid: String, direction: Int) -> Unit = { uuid, _ -> onNavigateToResource(uuid) },
    onSaveToHistory: (uuid: String, name: String) -> Unit = { _, _ -> },
    darkTheme: Boolean,
    siblingNavDirection: Int = 0,
    getCardState: (key: String) -> Boolean = { false },
    onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bannerColorInt = MaterialTheme.colorScheme.primary.toArgb()
    var showQrDialog by remember { mutableStateOf(false) }

    // Compute same-type siblings for Sample navigation
    var sameTypeSamples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    LaunchedEffect(resource) {
        if (resource !is Sample) return@LaunchedEffect
        val projectId = resource.projectId ?: return@LaunchedEffect
        fun List<Sample>.filterAndSort() = filter { it.sampleType == resource.sampleType }
            .sortedBy { it.internalId ?: Int.MAX_VALUE }
        val cached = CacheManager.getProjectSamples(projectId)
        if (cached != null) {
            sameTypeSamples = cached.filterAndSort()
        } else {
            try {
                val response = ApiClient.service.getSamplesByProject(projectId)
                if (response.isSuccessful) {
                    val all = response.body() ?: emptyList()
                    CacheManager.cacheProjectSamples(projectId, all)
                    sameTypeSamples = all.filterAndSort()
                }
            } catch (e: Exception) { }
        }
    }
    val siblingIndex = remember(sameTypeSamples, resource) {
        sameTypeSamples.indexOfFirst { it.uniqueId == resource.uniqueId }
    }

    // Prev/next siblings for swipe and button navigation
    val prevSibling = if (siblingIndex > 0) sameTypeSamples.getOrNull(siblingIndex - 1) else null
    val nextSibling = if (siblingIndex >= 0) sameTypeSamples.getOrNull(siblingIndex + 1) else null

    // Swipe gesture state
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var dragOffset by remember { mutableStateOf(0f) }
    // Slide-in animation for the content area on sibling navigation.
    // The top bar (Scaffold) appears instantly (EnterTransition.None at NavGraph level),
    // while the content slides in from the direction of travel.
    val contentOffset = remember {
        Animatable(if (siblingNavDirection != 0) siblingNavDirection * screenWidthPx else 0f)
    }
    LaunchedEffect(Unit) {
        if (siblingNavDirection != 0) {
            contentOffset.animateTo(0f, animationSpec = tween(durationMillis = 220))
        }
    }
    val scope = rememberCoroutineScope()

    val pullRefreshState = rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) { onRefresh() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (resource is Sample) "Sample" else "Dataset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        // Refresh button
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Share button
                        IconButton(
                            onClick = {
                            val projectId = when (resource) {
                                is Sample -> resource.projectId
                                is Dataset -> resource.projectId
                                else -> null
                            }

                            if (projectId != null) {
                                val resourceType = when (resource) {
                                    is Sample -> "sample-graph"
                                    is Dataset -> "dataset"
                                    else -> null
                                }

                                if (resourceType != null) {
                                    val url = "$graphExplorerUrl/$projectId/$resourceType/${resource.uniqueId}"
                                    val shareText = "Check out this ${if (resource is Sample) "sample" else "dataset"} in Crucible: $url"
                                    val imageUri = ShareCardGenerator.generate(context, resource, url, bannerColorInt, darkTheme)
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        putExtra(Intent.EXTRA_SUBJECT, resource.name)
                                        if (imageUri != null) {
                                            putExtra(Intent.EXTRA_STREAM, imageUri)
                                            type = "image/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        } else {
                                            type = "text/plain"
                                        }
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                }
                            }
                        },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Open in Graph Explorer button
                        IconButton(
                            onClick = {
                            val projectId = when (resource) {
                                is Sample -> resource.projectId
                                is Dataset -> resource.projectId
                                else -> null
                            }

                            if (projectId != null) {
                                val resourceType = when (resource) {
                                    is Sample -> "sample-graph"
                                    is Dataset -> "dataset"
                                    else -> null
                                }

                                if (resourceType != null) {
                                    val url = "$graphExplorerUrl/$projectId/$resourceType/${resource.uniqueId}"
                                    openUrlInBrowser(context, url)
                                }
                            }
                        },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = "Open in Graph Explorer",
                                modifier = Modifier.size(24.dp)
                            )
                        }

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
                .nestedScroll(pullRefreshState.nestedScrollConnection)
                .graphicsLayer {
                    translationX = dragOffset + contentOffset.value
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> dragOffset += delta },
                    onDragStopped = { _ ->
                        val current = dragOffset
                        when {
                            current > swipeThresholdPx && prevSibling != null -> scope.launch {
                                Animatable(current).animateTo(
                                    targetValue = screenWidthPx,
                                    animationSpec = tween(durationMillis = 180)
                                ) { dragOffset = value }
                                onNavigateToSibling(prevSibling.uniqueId, -1)
                            }
                            current < -swipeThresholdPx && nextSibling != null -> scope.launch {
                                Animatable(current).animateTo(
                                    targetValue = -screenWidthPx,
                                    animationSpec = tween(durationMillis = 180)
                                ) { dragOffset = value }
                                onNavigateToSibling(nextSibling.uniqueId, 1)
                            }
                            else -> scope.launch {
                                Animatable(current).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { dragOffset = value }
                            }
                        }
                    }
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "basic_info") {
                    BasicInfoCard(
                        resource = resource,
                        onPrev = prevSibling?.let { s -> { onNavigateToSibling(s.uniqueId, -1) } },
                        onNext = nextSibling?.let { s -> { onNavigateToSibling(s.uniqueId, 1) } },
                        currentIndex = siblingIndex,
                        totalCount = sameTypeSamples.size
                    )
                }

                when (resource) {
                    is Sample -> item(key = "type_details") {
                        SampleDetailsCard(
                            sample = resource,
                            onProjectClick = onNavigateToProject,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = getCardState("advanced"),
                            onAdvancedChange = { onCardStateChange("advanced", it) }
                        )
                    }
                    is Dataset -> item(key = "type_details") {
                        DatasetDetailsCard(
                            dataset = resource,
                            onProjectClick = onNavigateToProject,
                            onShowQr = { showQrDialog = true },
                            initialAdvanced = getCardState("advanced"),
                            onAdvancedChange = { onCardStateChange("advanced", it) }
                        )
                    }
                }

                when (resource) {
                    is Dataset -> {
                        if (thumbnails.isNotEmpty()) {
                            item(key = "thumbnails") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                        ThumbnailsSection(thumbnails)
                                    }
                                }
                            }
                        } else {
                            item(key = "no_thumbnails") {
                                Card {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No images available for this dataset", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (!resource.samples.isNullOrEmpty()) {
                            item(key = "linked_samples") {
                                LinkedSamplesCard(
                                    samples = resource.samples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("linked_samples"),
                                    onExpandChange = { onCardStateChange("linked_samples", it) }
                                )
                            }
                        }
                        if (!resource.parentDatasets.isNullOrEmpty()) {
                            item(key = "parent_datasets") {
                                ParentDatasetsCard(
                                    parents = resource.parentDatasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("parent_datasets"),
                                    onExpandChange = { onCardStateChange("parent_datasets", it) }
                                )
                            }
                        }
                        if (!resource.childDatasets.isNullOrEmpty()) {
                            item(key = "child_datasets") {
                                ChildDatasetsCard(
                                    children = resource.childDatasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("child_datasets"),
                                    onExpandChange = { onCardStateChange("child_datasets", it) }
                                )
                            }
                        }
                        if (!resource.scientificMetadata.isNullOrEmpty()) {
                            item(key = "scientific_metadata") {
                                ScientificMetadataCard(
                                    metadata = resource.scientificMetadata,
                                    initialExpanded = getCardState("sci_meta_expanded"),
                                    initialExpandAll = getCardState("sci_meta_expand_all"),
                                    onExpandedChange = { onCardStateChange("sci_meta_expanded", it) },
                                    onExpandAllChange = { onCardStateChange("sci_meta_expand_all", it) }
                                )
                            }
                        }
                        if (!resource.keywords.isNullOrEmpty()) {
                            item(key = "keywords") { KeywordsCard(resource.keywords ?: emptyList()) }
                        }
                    }
                    is Sample -> {
                        if (!resource.parentSamples.isNullOrEmpty()) {
                            item(key = "parent_samples") {
                                ParentSamplesCard(
                                    parents = resource.parentSamples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("parent_samples"),
                                    onExpandChange = { onCardStateChange("parent_samples", it) }
                                )
                            }
                        }
                        if (!resource.childSamples.isNullOrEmpty()) {
                            item(key = "child_samples") {
                                ChildSamplesCard(
                                    children = resource.childSamples.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("child_samples"),
                                    onExpandChange = { onCardStateChange("child_samples", it) }
                                )
                            }
                        }
                        if (!resource.datasets.isNullOrEmpty()) {
                            item(key = "linked_datasets") {
                                LinkedDatasetsCard(
                                    datasets = resource.datasets.orEmpty().distinctBy { it.uniqueId }.sortedBy { it.uniqueId },
                                    onNavigateToResource = onNavigateToResource,
                                    initialExpanded = getCardState("linked_datasets"),
                                    onExpandChange = { onCardStateChange("linked_datasets", it) }
                                )
                            }
                        }
                        if (!resource.keywords.isNullOrEmpty()) {
                            item(key = "keywords") { KeywordsCard(resource.keywords ?: emptyList()) }
                        }
                    }
                    else -> {}
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
            }
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(mfid = resource.uniqueId, name = resource.name) { showQrDialog = false }
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
    val context = LocalContext.current
    Card(border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // MFID centered at the top with copy button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resource.uniqueId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MFID", resource.uniqueId))
                        Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy MFID",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalCount > 1) {
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
                        Text(
                            text = resource.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (currentIndex >= 0) {
                            Text(
                                text = "${currentIndex + 1} / $totalCount",
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
            } else {
                Text(
                    text = resource.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
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
                            imageState = "error: ${it.result.throwable?.message}"
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
            Spacer(modifier = Modifier.height(12.dp))

            // Basic fields
            InfoRow(icon = Icons.Default.Notes, label = "Description", value = sample.description?.takeIf { it.isNotBlank() } ?: "None", verticalAlignment = Alignment.Top)
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
            Spacer(modifier = Modifier.height(12.dp))

            // Basic fields
            InfoRow(icon = Icons.Default.Notes, label = "Description", value = dataset.description?.takeIf { it.isNotBlank() } ?: "None", verticalAlignment = Alignment.Top)
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
                        null  -> Icons.Default.HelpOutline
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
                    Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(20.dp))
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
    } catch (_: Exception) { raw } } }
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
                Icons.Default.OpenInNew,
                contentDescription = "Open link",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
