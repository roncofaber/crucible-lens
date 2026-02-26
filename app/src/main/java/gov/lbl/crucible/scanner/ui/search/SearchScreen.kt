package gov.lbl.crucible.scanner.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.cache.CacheManager
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.Sample

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    apiKey: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var allSamples by remember { mutableStateOf<List<Sample>>(emptyList()) }
    var allDatasets by remember { mutableStateOf<List<Dataset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        isLoading = true

        // Get or fetch projects list
        val projects = CacheManager.getProjects()
            ?: try {
                val r = ApiClient.service.getProjects()
                if (r.isSuccessful) r.body()?.also { CacheManager.cacheProjects(it) } else null
            } catch (e: Exception) { null }

        if (projects == null) { isLoading = false; return@LaunchedEffect }

        // Seed immediately from cache
        val samples = mutableListOf<Sample>()
        val datasets = mutableListOf<Dataset>()
        projects.forEach { project ->
            CacheManager.getProjectSamples(project.projectId)?.let { samples.addAll(it) }
            CacheManager.getProjectDatasets(project.projectId)?.let { datasets.addAll(it) }
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
                val sr = ApiClient.service.getSamplesByProject(project.projectId)
                val dr = ApiClient.service.getDatasetsByProject(project.projectId)
                if (sr.isSuccessful && dr.isSuccessful) {
                    val s = sr.body() ?: emptyList()
                    val d = dr.body() ?: emptyList()
                    CacheManager.cacheProjectSamples(project.projectId, s)
                    CacheManager.cacheProjectDatasets(project.projectId, d)
                    samples.addAll(s)
                    datasets.addAll(d)
                    allSamples = samples.toList()
                    allDatasets = datasets.toList()
                }
            } catch (e: Exception) { }
            loadedCount++
        }

        isLoading = false
    }

    val filteredSamples = remember(allSamples, query) {
        if (query.isBlank()) emptyList()
        else allSamples.filter { it.matchesSearch(query) }
    }
    val filteredDatasets = remember(allDatasets, query) {
        if (query.isBlank()) emptyList()
        else allDatasets.filter { it.matchesSearch(query) }
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
                                    "Search samples and datasets…",
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
                            Icon(Icons.Default.ArrowBack, "Back")
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
                            progress = loadedCount.toFloat() / totalCount,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                query.isBlank() && allSamples.isEmpty() && allDatasets.isEmpty() && !isLoading -> EmptyState(
                    icon = Icons.Default.Storage,
                    title = "No cached data",
                    subtitle = "Fetching project data…"
                )
                query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Start typing",
                    subtitle = "Search across ${allSamples.size} samples and ${allDatasets.size} datasets"
                )
                filteredSamples.isEmpty() && filteredDatasets.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No results",
                    subtitle = "Try a different search term"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (filteredSamples.isNotEmpty()) {
                        item {
                            Text(
                                "Samples (${filteredSamples.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredSamples) { sample ->
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
                        items(filteredDatasets) { dataset ->
                            SearchResultCard(
                                name = dataset.name,
                                uuid = dataset.uniqueId,
                                type = "Dataset",
                                onClick = { onResourceClick(dataset.uniqueId) }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    } // end Box
}

@Composable
private fun SearchResultCard(name: String, uuid: String, type: String, onClick: () -> Unit) {
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
            Text(
                text = uuid,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Sample.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (sampleType?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true) ||
        (internalId?.toString()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (keywords?.any { it.lowercase().contains(q) } == true)
}

private fun Dataset.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (measurement?.lowercase()?.contains(q) == true) ||
        (instrumentName?.lowercase()?.contains(q) == true) ||
        (instrumentId?.toString()?.contains(q) == true) ||
        (sessionName?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (createdAt?.lowercase()?.contains(q) == true) ||
        (internalId?.toString()?.contains(q) == true) ||
        (dataFormat?.lowercase()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (sourceFolder?.lowercase()?.contains(q) == true) ||
        (fileToUpload?.lowercase()?.contains(q) == true) ||
        (jsonLink?.lowercase()?.contains(q) == true) ||
        (sha256Hash?.lowercase()?.contains(q) == true) ||
        (keywords?.any { it.lowercase().contains(q) } == true) ||
        (scientificMetadata?.containsQuery(q) == true)
}

private fun Map<String, Any?>.containsQuery(q: String): Boolean =
    entries.any { (key, value) -> key.lowercase().contains(q) || value.matchesQuery(q) }

private fun Any?.matchesQuery(q: String): Boolean = when (this) {
    null -> false
    is String -> lowercase().contains(q)
    is Number -> toString().contains(q)
    is Map<*, *> -> entries.any { (k, v) ->
        k.toString().lowercase().contains(q) || v.matchesQuery(q)
    }
    is List<*> -> any { it.matchesQuery(q) }
    else -> toString().lowercase().contains(q)
}
