package gov.lbl.crucible.scanner.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.with
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.cache.CacheManager
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.Project
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.ui.common.LoadingMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val project = remember(projectId) {
        CacheManager.getProjects()?.find { it.projectId == projectId }
    }

    var samples by remember { mutableStateOf<List<Sample>?>(null) }
    var datasets by remember { mutableStateOf<List<Dataset>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var fromCache by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredSamples = remember(samples, searchQuery) {
        if (searchQuery.isBlank()) samples ?: emptyList()
        else (samples ?: emptyList()).filter { it.matchesSearch(searchQuery) }
    }
    val filteredDatasets = remember(datasets, searchQuery) {
        if (searchQuery.isBlank()) datasets ?: emptyList()
        else (datasets ?: emptyList()).filter { it.matchesSearch(searchQuery) }
    }

    fun loadProjectData(forceRefresh: Boolean = false) {
        scope.launch {
            try {
                isLoading = true
                error = null

                if (!forceRefresh) {
                    val cachedSamples = CacheManager.getProjectSamples(projectId)
                    val cachedDatasets = CacheManager.getProjectDatasets(projectId)

                    if (cachedSamples != null && cachedDatasets != null) {
                        samples = cachedSamples
                        datasets = cachedDatasets
                        fromCache = true
                        isLoading = false
                        return@launch
                    }
                }
                fromCache = false

                val samplesResponse = ApiClient.service.getSamplesByProject(projectId)
                val datasetsResponse = ApiClient.service.getDatasetsByProject(projectId)

                if (samplesResponse.isSuccessful && datasetsResponse.isSuccessful) {
                    val loadedSamples = samplesResponse.body()
                    val loadedDatasets = datasetsResponse.body()

                    if (loadedSamples != null && loadedDatasets != null) {
                        CacheManager.cacheProjectSamples(projectId, loadedSamples)
                        CacheManager.cacheProjectDatasets(projectId, loadedDatasets)
                        samples = loadedSamples
                        datasets = loadedDatasets
                    } else {
                        error = "Failed to load project data"
                    }
                } else {
                    error = "Failed to load project data"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(projectId) {
        loadProjectData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        CacheManager.clearProjectDetail(projectId)
                        loadProjectData(forceRefresh = true)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            if (isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = {
                        val url = "$graphExplorerUrl/$projectId"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.Default.Public, contentDescription = "Open in browser")
                    }
                    IconButton(onClick = {
                        val url = "$graphExplorerUrl/$projectId"
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this project in Crucible: $url")
                            putExtra(Intent.EXTRA_SUBJECT, "Crucible Project: ${project?.projectName ?: projectId}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Project header with integrated search
            ProjectHeader(
                project = project,
                projectId = projectId,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                fromCache = fromCache
            )

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        val count = filteredSamples.size
                        val total = samples?.size ?: 0
                        Text(if (searchQuery.isBlank()) "Samples ($total)" else "Samples ($count/$total)")
                    },
                    icon = { Icon(Icons.Default.Science, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val count = filteredDatasets.size
                        val total = datasets?.size ?: 0
                        Text(if (searchQuery.isBlank()) "Datasets ($total)" else "Datasets ($count/$total)")
                    },
                    icon = { Icon(Icons.Default.Dataset, contentDescription = null) }
                )
            }

            when {
                isLoading -> {
                    val loadingMessage = LoadingMessage()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Loading Project Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AnimatedContent(
                                        targetState = loadingMessage,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(500)) with
                                                fadeOut(animationSpec = tween(500))
                                        },
                                        label = "loading message"
                                    ) { message ->
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                error != null -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Error Loading Data",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                else -> {
                    when (selectedTab) {
                        0 -> SamplesList(
                            samples = filteredSamples,
                            isFiltered = searchQuery.isNotBlank(),
                            onSampleClick = onResourceClick
                        )
                        1 -> DatasetsList(
                            datasets = filteredDatasets,
                            isFiltered = searchQuery.isNotBlank(),
                            onDatasetClick = onResourceClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    project: Project?,
    projectId: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    fromCache: Boolean = false
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name + icon row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = project?.projectName ?: projectId,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    project?.projectLeadEmail?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "Lead: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (fromCache) {
                        val ageMin = CacheManager.getProjectDataAgeMinutes(projectId) ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Cached ${ageMin}m ago",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Description
            val description = project?.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Creation date chip
            project?.createdAt?.let { date ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(
                        icon = Icons.Default.CalendarToday,
                        label = date.take(10)
                    )
                }
            }

            // Integrated search field
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search samples and datasets…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onSearchChange("") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SamplesList(
    samples: List<Sample>,
    isFiltered: Boolean,
    onSampleClick: (String) -> Unit
) {
    if (samples.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isFiltered) Icons.Default.SearchOff else Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isFiltered) "No Matching Samples" else "No Samples",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isFiltered) "No samples match your search." else "This project has no samples.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val groupedSamples = samples.groupBy { it.sampleType ?: "Unspecified Type" }
            .entries.sortedBy { it.key.lowercase() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groupedSamples.forEach { (sampleType, samplesInGroup) ->
                item {
                    CollapsibleGroup(
                        title = sampleType,
                        count = samplesInGroup.size,
                        icon = Icons.Default.Science
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            samplesInGroup.sortedBy { it.internalId ?: Int.MAX_VALUE }.forEach { sample ->
                                ResourceCard(
                                    title = sample.name,
                                    subtitle = null,
                                    uniqueId = sample.uniqueId,
                                    icon = Icons.Default.Science,
                                    onClick = { onSampleClick(sample.uniqueId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatasetsList(
    datasets: List<Dataset>,
    isFiltered: Boolean,
    onDatasetClick: (String) -> Unit
) {
    if (datasets.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isFiltered) Icons.Default.SearchOff else Icons.Default.Dataset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isFiltered) "No Matching Datasets" else "No Datasets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isFiltered) "No datasets match your search." else "This project has no datasets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val groupedDatasets = datasets.groupBy { it.measurement ?: "Unspecified Measurement" }
            .entries.sortedBy { it.key.lowercase() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groupedDatasets.forEach { (measurement, datasetsInGroup) ->
                item {
                    CollapsibleGroup(
                        title = measurement,
                        count = datasetsInGroup.size,
                        icon = Icons.Default.Dataset
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            datasetsInGroup.sortedBy { it.internalId ?: Int.MAX_VALUE }.forEach { dataset ->
                                ResourceCard(
                                    title = dataset.name,
                                    subtitle = null,
                                    uniqueId = dataset.uniqueId,
                                    icon = Icons.Default.Dataset,
                                    onClick = { onDatasetClick(dataset.uniqueId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleGroup(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$count item${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150)),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(
    title: String,
    subtitle: String?,
    uniqueId: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = uniqueId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
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
        (internalId?.toString()?.contains(q) == true)
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
        (scientificMetadata?.matchesSearch(q) == true)
}

private fun Map<String, Any?>.matchesSearch(query: String): Boolean =
    entries.any { (key, value) ->
        key.lowercase().contains(query) || value.matchesSearchValue(query)
    }

private fun Any?.matchesSearchValue(query: String): Boolean = when (this) {
    null -> false
    is String -> lowercase().contains(query)
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (this as Map<String, Any?>).matchesSearch(query)
    is List<*> -> any { it.matchesSearchValue(query) }
    else -> toString().lowercase().contains(query)
}
