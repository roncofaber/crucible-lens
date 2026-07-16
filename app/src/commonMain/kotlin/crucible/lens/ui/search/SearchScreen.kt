package crucible.lens.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.MetadataSearchResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.FilterSheet
import crucible.lens.ui.common.SearchFilters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    var query by remember { mutableStateOf("") }
    var showMineOnly by remember { mutableStateOf(false) }
    var metadataResults by remember { mutableStateOf<List<MetadataSearchResult>?>(null) }
    var isMetadataSearching by remember { mutableStateOf(false) }
    var activeFilters by remember { mutableStateOf(SearchFilters()) }
    var filterResults by remember { mutableStateOf<Pair<List<Sample>, List<Dataset>>?>(null) }
    var isFilterLoading by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    // Server-side fuzzy name search results
    var nameSearchSamples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var nameSearchDatasets by remember { mutableStateOf<List<Dataset>>(emptyList()) }
    var nameSearchProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var isNameSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Clear metadata results and name search when query changes
    LaunchedEffect(query) { metadataResults = null }

    // Debounced server-side name search (only when no active filters)
    LaunchedEffect(query, activeFilters.isActive) {
        if (activeFilters.isActive || query.length < 3) {
            nameSearchSamples = emptyList(); nameSearchDatasets = emptyList(); nameSearchProjects = emptyList()
            isNameSearching = false; return@LaunchedEffect
        }
        delay(350)
        isNameSearching = true
        val q = query.trim()
        val samples = (ApiClient.service.searchSamples(q) as? ApiResult.Success)?.data ?: emptyList()
        val datasets = (ApiClient.service.searchDatasets(q) as? ApiResult.Success)?.data ?: emptyList()
        val projects = (ApiClient.service.searchProjects(q) as? ApiResult.Success)?.data ?: emptyList()
        nameSearchSamples = if (showMineOnly && userOrcid != null) samples.filter { it.ownerOrcid == userOrcid } else samples
        nameSearchDatasets = if (showMineOnly && userOrcid != null) datasets.filter { it.ownerOrcid == userOrcid } else datasets
        nameSearchProjects = projects
        isNameSearching = false
    }

    // Apply server-side filters whenever activeFilters changes
    LaunchedEffect(activeFilters) {
        if (!activeFilters.isActive) { filterResults = null; return@LaunchedEffect }
        isFilterLoading = true
        try {
            val after = activeFilters.createdAfter.ifBlank { null }
            val before = activeFilters.createdBefore.ifBlank { null }
            val projectId = activeFilters.projectId.ifBlank { null }
            val ownerOrcid = activeFilters.ownerOrcid.ifBlank { null }
            val samples = (ApiClient.service.getFilteredSamples(
                projectId = projectId,
                sampleType = activeFilters.sampleType.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data ?: emptyList()
            val datasets = (ApiClient.service.getFilteredDatasets(
                projectId = projectId,
                measurement = activeFilters.measurement.ifBlank { null },
                instrumentName = activeFilters.instrumentName.ifBlank { null },
                dataFormat = activeFilters.dataFormat.ifBlank { null },
                sessionName = activeFilters.sessionName.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data ?: emptyList()
            filterResults = samples to datasets
        } catch (e: Exception) {
            filterResults = null
        }
        isFilterLoading = false
    }

    val mineActive = showMineOnly && userOrcid != null
    val filtersActive = activeFilters.isActive
    val filteredProjects = if (!filtersActive && query.length >= 3) nameSearchProjects else emptyList()
    val filteredSamples = when {
        filtersActive -> {
            val base = filterResults?.first ?: emptyList()
            if (mineActive) base.filter { it.ownerOrcid == userOrcid } else base
        }
        query.length >= 3 -> nameSearchSamples
        else -> emptyList()
    }
    val filteredDatasets = when {
        filtersActive -> {
            val base = filterResults?.second ?: emptyList()
            if (mineActive) base.filter { it.ownerOrcid == userOrcid } else base
        }
        query.length >= 3 -> nameSearchDatasets
        else -> emptyList()
    }
    val isLoading = isNameSearching || isFilterLoading
    val mfidCandidate = remember(query) {
        val q = query.trim()
        if (q.length >= 10 && q.all { c -> c.isLowerCase() || c.isDigit() }) q else null
    }

    AppScaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    "Search the Crucible...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (activeFilters.isActive)
                                    Badge { Text(activeFilters.activeCount.toString()) }
                            }
                        ) {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Default.FilterAlt, contentDescription = "Filters")
                            }
                        }
                        IconButton(onClick = onHome) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                    }
                )
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (userOrcid != null || activeFilters.isActive || isFilterLoading) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (userOrcid != null) {
                            FilterChip(
                                selected = showMineOnly,
                                onClick = { showMineOnly = !showMineOnly },
                                label = { Text("Mine") },
                                leadingIcon = if (showMineOnly) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                        if (isFilterLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
                        } else if (activeFilters.isActive) {
                            FilterChip(
                                selected = true,
                                onClick = { activeFilters = SearchFilters() },
                                label = { Text("${activeFilters.activeCount} filter${if (activeFilters.activeCount > 1) "s" else ""} · Clear") },
                                leadingIcon = { Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                apiKey.isNullOrBlank() -> EmptyState(
                    icon = Icons.Default.Key,
                    title = "No API key",
                    subtitle = "Configure your API key in Settings to search"
                )
                query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Start typing",
                    subtitle = "Type at least 3 characters to search samples, datasets and projects"
                )
                filteredProjects.isEmpty() && filteredSamples.isEmpty() && filteredDatasets.isEmpty() -> {
                    if (mfidCandidate != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DirectLookupCard(mfidCandidate) { onResourceClick(it) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isMetadataSearching = true
                                        metadataResults = try {
                                            when (val resp = ApiClient.service.searchScientificMetadata(query.trim())) {
                                                is ApiResult.Success -> resp.data
                                                is ApiResult.Error -> emptyList()
                                            }
                                        } catch (_: Exception) { emptyList() }
                                        isMetadataSearching = false
                                    }
                                },
                                enabled = !isMetadataSearching,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isMetadataSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Search metadata on server")
                            }
                            if (!metadataResults.isNullOrEmpty()) {
                                Text(
                                    "Server Metadata (${metadataResults?.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                metadataResults?.forEach { result ->
                                    val cachedDataset = CacheManager.getResource(result.uniqueId) as? Dataset
                                    val displayName = cachedDataset?.name ?: result.uniqueId
                                    val snippet = result.scientificMetadata
                                        ?.entries?.take(2)
                                        ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                                    SearchResultCard(
                                        name = displayName,
                                        uuid = result.uniqueId,
                                        type = "Dataset",
                                        subtitle = snippet,
                                        onClick = { onResourceClick(result.uniqueId) }
                                    )
                                }
                            }
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No results",
                            subtitle = "Try a different search term"
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (mfidCandidate != null) {
                        item { DirectLookupCard(mfidCandidate) { onResourceClick(it) } }
                    }
                    if (filteredProjects.isNotEmpty()) {
                        item {
                            Text(
                                "Projects (${filteredProjects.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredProjects, key = { it.projectId }) { project ->
                            SearchResultCard(
                                name = project.title ?: project.projectId,
                                uuid = project.projectId,
                                type = "Project",
                                onClick = { onProjectClick(project.projectId) }
                            )
                        }
                    }
                    if (filteredSamples.isNotEmpty()) {
                        item {
                            Text(
                                "Samples (${filteredSamples.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredSamples, key = { it.uniqueId }) { sample ->
                            SearchResultCard(
                                name = sample.name,
                                uuid = sample.uniqueId,
                                type = "Sample",
                                onClick = { onResourceClick(sample.uniqueId) }
                            )
                        }
                    }
                    if (filteredDatasets.isNotEmpty()) {
                        item {
                            Text(
                                "Datasets (${filteredDatasets.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredDatasets, key = { it.uniqueId }) { dataset ->
                            SearchResultCard(
                                name = dataset.name,
                                uuid = dataset.uniqueId,
                                type = "Dataset",
                                onClick = { onResourceClick(dataset.uniqueId) }
                            )
                        }
                    }

                    // ── Server metadata search ────────────────────────────────
                    item {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isMetadataSearching = true
                                    metadataResults = try {
                                        when (val resp = ApiClient.service.searchScientificMetadata(query.trim())) {
                                            is ApiResult.Success -> resp.data
                                            is ApiResult.Error -> emptyList()
                                        }
                                    } catch (_: Exception) { emptyList() }
                                    isMetadataSearching = false
                                }
                            },
                            enabled = !isMetadataSearching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMetadataSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Search metadata on server")
                        }
                    }

                    if (!metadataResults.isNullOrEmpty()) {
                        item {
                            Text(
                                "Server Metadata (${metadataResults?.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(metadataResults ?: emptyList(), key = { it.uniqueId }) { result ->
                            val cachedDataset = CacheManager.getResource(result.uniqueId) as? Dataset
                            val displayName = cachedDataset?.name ?: result.uniqueId
                            val snippet = result.scientificMetadata
                                ?.entries?.take(2)
                                ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                            SearchResultCard(
                                name = displayName,
                                uuid = result.uniqueId,
                                type = "Dataset",
                                subtitle = snippet,
                                onClick = { onResourceClick(result.uniqueId) }
                            )
                        }
                    }
                }
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
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
    } // end Box
}

@Composable
private fun SearchResultCard(
    name: String,
    uuid: String,
    type: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(type) },
                    modifier = Modifier.height(28.dp)
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Text(
                text = uuid,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectLookupCard(mfid: String, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(mfid) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
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
