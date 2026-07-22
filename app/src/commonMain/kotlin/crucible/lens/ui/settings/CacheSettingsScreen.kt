@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.util.formatDecimal
import crucible.lens.ui.common.AppScaffold
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CacheSettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cacheManager = koinInject<CacheManager>()
    var cacheAge by remember { mutableStateOf<Long?>(null) }
    var cacheStats by remember { mutableStateOf<CacheManager.CacheStats?>(null) }

    LaunchedEffect(Unit) {
        cacheAge = cacheManager.getProjectsAgeMinutes()
        cacheStats = cacheManager.getDetailedStats()
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Cache",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(
                            AppIcons.FileStorage,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Cached data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val stats = cacheStats
                        if (stats != null && stats.estimatedSizeKB > 0) {
                            val sizeLabel = if (stats.estimatedSizeKB >= 1024)
                                formatDecimal(stats.estimatedSizeKB / 1024.0, 1) + " MB"
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
                    cacheManager.clearAll()
                    cacheAge = null
                    cacheStats = cacheManager.getDetailedStats()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Cache cleared",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(AppIcons.Delete, modifier = Modifier.size(18.dp))
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
