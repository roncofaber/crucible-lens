@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.showToast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.HealthStatus
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.ui.common.AppScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed interface HealthState {
    object Idle : HealthState
    object Checking : HealthState
    data class Ok(val status: HealthStatus) : HealthState
    data class Failed(val message: String) : HealthState
}

@Composable
fun ApiSettingsScreen(
    currentApiBaseUrl: String,
    currentGraphExplorerUrl: String,
    onApiBaseUrlSave: (String) -> Unit,
    onGraphExplorerUrlSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var apiBaseUrlInput by remember { mutableStateOf(currentApiBaseUrl) }
    var graphExplorerUrlInput by remember { mutableStateOf(currentGraphExplorerUrl) }
    val platformContext = getPlatformContext()
    val apiClient = koinInject<ApiClient>()

    val apiBaseUrlDirty = apiBaseUrlInput != currentApiBaseUrl
    val graphExplorerUrlDirty = graphExplorerUrlInput != currentGraphExplorerUrl
    val hasChanges = apiBaseUrlDirty || graphExplorerUrlDirty

    var healthState by remember { mutableStateOf<HealthState>(HealthState.Idle) }
    var healthManualTrigger by remember { mutableStateOf(0) }

    suspend fun runHealthCheck(url: String) {
        if (url.isBlank()) { healthState = HealthState.Idle; return }
        healthState = HealthState.Checking
        healthState = when (val result = apiClient.service.checkHealth(url)) {
            is ApiResult.Success -> HealthState.Ok(result.data)
            is ApiResult.Error   -> HealthState.Failed("HTTP ${result.code}: ${result.message}")
        }
    }

    LaunchedEffect(apiBaseUrlInput) {
        delay(800)
        runHealthCheck(apiBaseUrlInput)
    }

    LaunchedEffect(healthManualTrigger) {
        if (healthManualTrigger > 0) runHealthCheck(apiBaseUrlInput)
    }

    fun save() {
        if (apiBaseUrlDirty) onApiBaseUrlSave(apiBaseUrlInput)
        if (graphExplorerUrlDirty) onGraphExplorerUrlSave(graphExplorerUrlInput)
        showToast(platformContext, "Settings saved")
    }

    fun discard() {
        apiBaseUrlInput = currentApiBaseUrl
        graphExplorerUrlInput = currentGraphExplorerUrl
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "API",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        },
        bottomBar = { SettingsSaveBar(hasChanges, ::discard, ::save) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Endpoints", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leave as default unless you're using a custom deployment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = apiBaseUrlInput,
                onValueChange = { apiBaseUrlInput = it; healthState = HealthState.Idle },
                label = { Text("API Base URL") },
                placeholder = { Text(AppPreferences.DEFAULT_API_BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { AppIcon(AppIcons.ApiEndpoint) },
                trailingIcon = {
                    if (apiBaseUrlInput != AppPreferences.DEFAULT_API_BASE_URL) {
                        IconButton(onClick = {
                            apiBaseUrlInput = AppPreferences.DEFAULT_API_BASE_URL
                            healthState = HealthState.Idle
                        }) { AppIcon(AppIcons.ResetToDefault) }
                    }
                },
                singleLine = true,
                colors = if (apiBaseUrlDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Connection", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(
                            onClick = { healthManualTrigger++ },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            enabled = healthState !is HealthState.Checking
                        ) {
                            if (healthState is HealthState.Checking) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Checking…", style = MaterialTheme.typography.labelMedium)
                            } else {
                                AppIcon(AppIcons.TestConnection, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test connection", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    when (val hs = healthState) {
                        is HealthState.Idle -> Text(
                            "Tap \"Test connection\" to verify the API endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is HealthState.Checking -> {}
                        is HealthState.Ok -> {
                            val s = hs.status
                            val ok = s.status == "ok"
                            val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(if (ok) AppIcons.Success else AppIcons.Warning, modifier = Modifier.size(16.dp), tint = color)
                                Column {
                                    Text(if (ok) "Connected" else "Server error", style = MaterialTheme.typography.bodySmall, color = color)
                                    val details = listOfNotNull(
                                        s.db?.let { "DB: $it" },
                                        s.dbMs?.let { "${it.toInt()} ms" },
                                        s.version?.let { "v$it" }
                                    ).joinToString(" · ")
                                    if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        is HealthState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(AppIcons.Unreachable, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Text(hs.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = graphExplorerUrlInput,
                onValueChange = { graphExplorerUrlInput = it },
                label = { Text("Crucible Web URL") },
                placeholder = { Text("https://crucible.lbl.gov/explore/") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { AppIcon(AppIcons.WebUrl) },
                singleLine = true,
                supportingText = { Text("Web interface for browsing and exploring resources", style = MaterialTheme.typography.bodySmall) },
                colors = if (graphExplorerUrlDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
