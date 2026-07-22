@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.search

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.EffectsDefaultSpring
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ResourceSearchResult
import crucible.lens.ui.common.FilterSheet
import crucible.lens.ui.common.SearchFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Maps resource type string to the correct icon.
private fun iconForType(resourceType: String?) = when (resourceType) {
    "sample" -> AppIcons.Sample
    "project" -> AppIcons.Project
    else -> AppIcons.Dataset
}

@Composable
fun SearchScreen(
    apiKey: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    userOrcid: String? = null
) {
    val apiClient = koinInject<ApiClient>()
    var query by rememberSaveable { mutableStateOf("") }
    var showMineOnly by rememberSaveable { mutableStateOf(false) }
    // Search mode: false = name search, true = metadata search
    var metadataMode by rememberSaveable { mutableStateOf(false) }

    var activeFilters by remember { mutableStateOf(SearchFilters()) }
    var isFilterLoading by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Both modes produce the same type — a unified list of ResourceSearchResult.
    // Name search maps Sample/Dataset/Project → ResourceSearchResult at the call site.
    // Metadata search returns ResourceSearchResult directly from the API.
    var nameResults by remember { mutableStateOf<List<ResourceSearchResult>>(emptyList()) }
    var isNameSearching by remember { mutableStateOf(false) }

    var metadataResults by remember { mutableStateOf<List<ResourceSearchResult>?>(null) }
    var isMetadataSearching by remember { mutableStateOf(false) }

    var isFirstComposition by remember { mutableStateOf(true) }

    // Clear metadata results when query changes
    LaunchedEffect(query) { metadataResults = null }

    // Name search — fires in name mode
    LaunchedEffect(query, metadataMode, activeFilters.isActive) {
        if (metadataMode) {
            nameResults = emptyList(); isNameSearching = false; return@LaunchedEffect
        }
        if (activeFilters.isActive || query.length < 3) {
            nameResults = emptyList(); isNameSearching = false; return@LaunchedEffect
        }
        if (!isFirstComposition) delay(350)
        isFirstComposition = false
        isNameSearching = true
        val q = query.trim()
        val samples = (apiClient.service.searchSamples(q) as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid) }
            ?: emptyList()
        val datasets = (apiClient.service.searchDatasets(q) as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid) }
            ?: emptyList()
        val projects = (apiClient.service.searchProjects(q) as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.projectId, "project", it.title ?: it.projectId) }
            ?: emptyList()
        val combined = projects + samples + datasets
        nameResults = if (showMineOnly && userOrcid != null)
            combined.filter { it.ownerOrcid == userOrcid || it.resourceType == "project" }
        else combined
        isNameSearching = false
    }

    // Filter-based search — fires in name mode when filters are active
    LaunchedEffect(activeFilters) {
        if (!activeFilters.isActive || metadataMode) { return@LaunchedEffect }
        isFilterLoading = true
        try {
            val after = activeFilters.createdAfter.ifBlank { null }
            val before = activeFilters.createdBefore.ifBlank { null }
            val projectId = activeFilters.projectId.ifBlank { null }
            val ownerOrcid = activeFilters.ownerOrcid.ifBlank { null }
            val samples = (apiClient.service.getFilteredSamples(
                projectId = projectId,
                sampleType = activeFilters.sampleType.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data
                ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid) }
                ?: emptyList()
            val datasets = (apiClient.service.getFilteredDatasets(
                projectId = projectId,
                measurement = activeFilters.measurement.ifBlank { null },
                instrumentName = activeFilters.instrumentName.ifBlank { null },
                dataFormat = activeFilters.dataFormat.ifBlank { null },
                sessionName = activeFilters.sessionName.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data
                ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid) }
                ?: emptyList()
            nameResults = samples + datasets
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
        isFilterLoading = false
    }

    // Metadata search — fires in metadata mode
    LaunchedEffect(query, metadataMode) {
        if (!metadataMode) {
            metadataResults = null; isMetadataSearching = false; return@LaunchedEffect
        }
        if (query.length < 3) { metadataResults = null; return@LaunchedEffect }
        if (!isFirstComposition) delay(350)
        isFirstComposition = false
        isMetadataSearching = true
        metadataResults = try {
            (apiClient.service.searchScientificMetadata(query.trim()) as? ApiResult.Success)?.data
                ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { emptyList() }
        isMetadataSearching = false
    }

    val filtersActive = activeFilters.isActive
    val textQuery = query.trim().lowercase()
    val searchActive = query.length >= 3 || filtersActive

    // Active results for current mode, with client-side text filter applied in filter mode
    val activeResults: List<ResourceSearchResult> = when {
        metadataMode -> metadataResults ?: emptyList()
        filtersActive && textQuery.length >= 3 ->
            nameResults.filter { it.name?.contains(textQuery, ignoreCase = true) == true }
        else -> nameResults
    }

    // Group by resource type for section headers
    val projectResults = activeResults.filter { it.resourceType == "project" }
    val sampleResults = activeResults.filter { it.resourceType == "sample" }
    val datasetResults = activeResults.filter { it.resourceType == "dataset" }

    val mfidCandidate = remember(query) {
        val q = query.trim()
        if (q.length >= 10 && q.all { c -> c.isLowerCase() || c.isDigit() }) q else null
    }

    val isLoading = if (metadataMode) isMetadataSearching else (isNameSearching || isFilterLoading)
    val hasResults = activeResults.isNotEmpty()
    val searchPending = when {
        metadataMode -> query.length >= 3 && !isMetadataSearching && metadataResults == null
        else -> !filtersActive && query.length >= 3 && !isNameSearching
    }
    val isSearchLoading = (isLoading || searchPending) && !hasResults && mfidCandidate == null

    val lazyListState = remember { LazyListState() }
    LaunchedEffect(isNameSearching, isMetadataSearching) {
        if (!isNameSearching && !isMetadataSearching) lazyListState.scrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        SearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .semantics { traversalIndex = 0f },
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = { if (!it) onBack() },
                    placeholder = { Text("Search the Crucible...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        IconButton(onClick = onBack) { AppIcon(AppIcons.Back) }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!metadataMode) {
                                BadgedBox(badge = {
                                    if (activeFilters.isActive) Badge { Text(activeFilters.activeCount.toString()) }
                                }) {
                                    IconButton(onClick = { showFilterSheet = true }) {
                                        AppIcon(AppIcons.Filter)
                                    }
                                }
                            }
                            IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                        }
                    }
                )
            },
            expanded = true,
            onExpandedChange = { if (!it) onBack() }
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {

                    // ── Chips row ─────────────────────────────────────────────
                    val showChipsRow = searchActive || userOrcid != null || activeFilters.isActive
                    if (showChipsRow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!metadataMode && userOrcid != null) {
                                    FilterChip(
                                        selected = showMineOnly,
                                        onClick = { showMineOnly = !showMineOnly },
                                        label = { Text("Mine") },
                                        leadingIcon = if (showMineOnly) {
                                            { AppIcon(AppIcons.Check, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                                if (!metadataMode && activeFilters.isActive) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { activeFilters = SearchFilters() },
                                        label = { Text("${activeFilters.activeCount} filter${if (activeFilters.activeCount > 1) "s" else ""} · Clear") },
                                        leadingIcon = { AppIcon(AppIcons.Filter, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                            if (searchActive) {
                                FilterChip(
                                    selected = metadataMode,
                                    onClick = { metadataMode = !metadataMode },
                                    label = { Text("Metadata") },
                                    leadingIcon = { AppIcon(AppIcons.SearchFilters, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    // ── Content area ──────────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when {
                            apiKey.isNullOrBlank() -> SearchEmptyState(
                                icon = AppIcons.Key,
                                title = "No API key",
                                subtitle = "Configure your API key in Settings to search"
                            )
                            query.isBlank() && !filtersActive -> SearchEmptyState(
                                icon = AppIcons.Search,
                                title = "Start typing",
                                subtitle = "Type at least 3 characters to search samples, datasets and projects"
                            )
                            isSearchLoading -> SearchLoadingState()
                            !hasResults && mfidCandidate == null -> SearchEmptyState(
                                icon = AppIcons.SearchOff,
                                title = "No results",
                                subtitle = if (metadataMode)
                                    "No metadata matches found — try a different term"
                                else
                                    "No name matches found — try metadata search"
                            )
                            else -> LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                if (mfidCandidate != null) {
                                    item(key = "direct") {
                                        DirectLookupCard(mfidCandidate) { onResourceClick(it) }
                                        HorizontalDivider()
                                    }
                                }

                                // Projects (name mode only — metadata search doesn't return projects)
                                if (projectResults.isNotEmpty()) {
                                    item(key = "header_projects") { SearchSectionHeader("Projects (${projectResults.size})") }
                                    items(projectResults, key = { it.uniqueId }) { result ->
                                        SearchResultItem(result) { onProjectClick(result.uniqueId) }
                                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                                    }
                                }

                                // Samples
                                if (sampleResults.isNotEmpty()) {
                                    item(key = "header_samples") {
                                        SearchSectionHeader("Samples (${sampleResults.size})")
                                    }
                                    items(sampleResults, key = { it.uniqueId }) { result ->
                                        SearchResultItem(result) { onResourceClick(result.uniqueId) }
                                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                                    }
                                }

                                // Datasets (includes untyped results from metadata search)
                                if (datasetResults.isNotEmpty()) {
                                    item(key = "header_datasets") {
                                        SearchSectionHeader("Datasets (${datasetResults.size})")
                                    }
                                    items(datasetResults, key = { it.uniqueId }) { result ->
                                        SearchResultItem(result) { onResourceClick(result.uniqueId) }
                                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                FilterLoadingBar(isFilterLoading)
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = activeFilters,
            onApply = { activeFilters = it },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun SearchResultItem(result: ResourceSearchResult, onClick: () -> Unit) {
    val snippet = result.scientificMetadata
        ?.entries?.take(2)
        ?.joinToString(" · ") { (k, v) -> "$k: $v" }
    ListItem(
        headlineContent = {
            Text(result.name ?: result.uniqueId, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (snippet != null) {
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    result.uniqueId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = { AppIcon(iconForType(result.resourceType), tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SearchSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SearchLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text(
                "Searching...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchEmptyState(icon: AppIconToken, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(icon, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FilterLoadingBar(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = EffectsFastSpring),
        exit = fadeOut(animationSpec = EffectsDefaultSpring)
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DirectLookupCard(mfid: String, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onClick(mfid) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(AppIcons.OpenInNew, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Open resource directly",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    mfid,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}
