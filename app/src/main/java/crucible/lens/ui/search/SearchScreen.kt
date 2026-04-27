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
import android.util.Log
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.MetadataSearchResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import crucible.lens.data.util.fetchProjectData
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.common.OfflineBanner
import kotlinx.coroutines.launch

private const val TAG = "SearchScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    apiKey: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var allProjects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var allSamples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var allDatasets by remember { mutableStateOf<List<Dataset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var metadataResults by remember { mutableStateOf<List<MetadataSearchResult>?>(null) }
    var isMetadataSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Clear server results when query changes
    LaunchedEffect(query) { metadataResults = null }

    LaunchedEffect(Unit) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        isLoading = true

        // Get or fetch projects list
        val projects = CacheManager.getProjects()
            ?: try {
                val r = ApiClient.service.getProjects()
                if (r.isSuccessful) r.body()?.also { CacheManager.cacheProjects(it) } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load projects", e)
                null
            }

        if (projects == null) { isLoading = false; return@LaunchedEffect }

        allProjects = projects

        // Seed immediately from cache, preferring individually-cached rich versions.
        val samples = mutableListOf<Sample>()
        val datasets = mutableListOf<Dataset>()
        projects.forEach { project ->
            CacheManager.getProjectSamples(project.projectId)?.forEach { s ->
                samples.add((CacheManager.getResource(s.uniqueId) as? Sample) ?: s)
            }
            CacheManager.getProjectDatasets(project.projectId)?.forEach { d ->
                datasets.add((CacheManager.getResource(d.uniqueId) as? Dataset) ?: d)
            }
        }
        allSamples = samples.toList()
        allDatasets = datasets.toList()

        // Fetch uncached projects in the background, updating results as each arrives
        val uncached = projects.filter {
            CacheManager.getProjectSamples(it.projectId) == null ||
            CacheManager.getProjectDatasets(it.projectId) == null
        }
        totalCount = uncached.size
        loadedCount = 0

        uncached.forEach { project ->
            try {
                val (s, d) = fetchProjectData(project.projectId)
                samples.addAll(s.map { (CacheManager.getResource(it.uniqueId) as? Sample) ?: it })
                datasets.addAll(d.map { (CacheManager.getResource(it.uniqueId) as? Dataset) ?: it })
                allSamples = samples.toList()
                allDatasets = datasets.toList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch project ${project.projectId}", e)
            }
            loadedCount++
        }

        isLoading = false
    }

    val filteredProjects = remember(allProjects, query) {
        if (query.isBlank()) emptyList()
        else allProjects.filter { it.matchesSearch(query) }
    }
    val filteredSamples = remember(allSamples, query) {
        if (query.isBlank()) emptyList()
        else allSamples.filter { it.matchesSearch(query) }
    }
    val filteredDatasets = remember(allDatasets, query) {
        if (query.isBlank()) emptyList()
        else allDatasets.filter { it.matchesSearch(query) }
    }
    val mfidCandidate = remember(query) {
        val q = query.trim()
        if (q.length >= 10 && q.all { c -> c.isLowerCase() || c.isDigit() }) q else null
    }

    Scaffold(
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
                        IconButton(onClick = onHome) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                    }
                )
                if (isLoading) {
                    if (totalCount > 0) {
                        LinearProgressIndicator(
                            progress = { loadedCount.toFloat() / totalCount },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                OfflineBanner()
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
                query.isBlank() && allSamples.isEmpty() && allDatasets.isEmpty() && !isLoading -> EmptyState(
                    icon = Icons.Default.Storage,
                    title = "No cached data",
                    subtitle = "Fetching project data…"
                )
                query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Start typing",
                    subtitle = "Search across ${allProjects.size} projects, ${allSamples.size} samples, and ${allDatasets.size} datasets"
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
                                            val resp = ApiClient.service.searchScientificMetadata(query.trim())
                                            if (resp.isSuccessful) resp.body() else emptyList()
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
                                    "Server Metadata (${metadataResults!!.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                metadataResults!!.forEach { result ->
                                    val cachedDataset = CacheManager.getResource(result.datasetMfid) as? Dataset
                                    val displayName = cachedDataset?.name ?: result.datasetMfid
                                    val snippet = result.scientificMetadata
                                        ?.entries?.take(2)
                                        ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                                    SearchResultCard(
                                        name = displayName,
                                        uuid = result.datasetMfid,
                                        type = "Dataset",
                                        subtitle = snippet,
                                        onClick = { onResourceClick(result.datasetMfid) }
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
                                        val resp = ApiClient.service.searchScientificMetadata(query.trim())
                                        if (resp.isSuccessful) resp.body() else emptyList()
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
                                "Server Metadata (${metadataResults!!.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(metadataResults!!, key = { it.datasetMfid }) { result ->
                            val cachedDataset = CacheManager.getResource(result.datasetMfid) as? Dataset
                            val displayName = cachedDataset?.name ?: result.datasetMfid
                            val snippet = result.scientificMetadata
                                ?.entries?.take(2)
                                ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                            SearchResultCard(
                                name = displayName,
                                uuid = result.datasetMfid,
                                type = "Dataset",
                                subtitle = snippet,
                                onClick = { onResourceClick(result.datasetMfid) }
                            )
                        }
                    }
                }
            }
        }
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

