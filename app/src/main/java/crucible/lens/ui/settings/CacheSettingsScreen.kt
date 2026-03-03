package crucible.lens.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.cache.CacheManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cacheAge by remember { mutableStateOf<Long?>(null) }
    var cacheStats by remember { mutableStateOf<CacheManager.CacheStats?>(null) }

    LaunchedEffect(Unit) {
        cacheAge = CacheManager.getProjectsAgeMinutes()
        cacheStats = CacheManager.getDetailedStats()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cache") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Cache", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pre-loaded data for faster browsing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cache stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Cache",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val stats = cacheStats
                        if (stats != null && stats.estimatedSizeKB > 0) {
                            val sizeLabel = if (stats.estimatedSizeKB >= 1024)
                                "%.1f MB".format(stats.estimatedSizeKB / 1024.0)
                            else
                                "${stats.estimatedSizeKB} KB"
                            Text(
                                "~$sizeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val stats = cacheStats
                    if (stats == null || (stats.resourceCount == 0 && stats.projectCount == 0 && stats.cachedSampleCount == 0)) {
                        Text(
                            "No cached data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val ageLabel = cacheAge?.let { "${it}m ago" } ?: "unknown"
                        if (stats.projectCount > 0)
                            CacheStatRow("Projects", "${stats.projectCount} cached ($ageLabel)")
                        if (stats.cachedSampleCount > 0)
                            CacheStatRow("Samples", "${stats.cachedSampleCount} cached")
                        if (stats.cachedDatasetCount > 0)
                            CacheStatRow("Datasets", "${stats.cachedDatasetCount} cached")
                        if (stats.resourceCount > 0)
                            CacheStatRow("Full resources", "${stats.resourceCount} cached")
                        if (stats.thumbnailCount > 0)
                            CacheStatRow("Thumbnails", "${stats.thumbnailCount} cached")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    CacheManager.clearAll()
                    cacheAge = null
                    cacheStats = CacheManager.getDetailedStats()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Cache cleared",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear All Cache")
            }
        }
    }
}

@Composable
private fun CacheStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
