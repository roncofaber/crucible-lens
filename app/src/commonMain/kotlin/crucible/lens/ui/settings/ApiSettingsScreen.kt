package crucible.lens.ui.settings

import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.showToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.HealthStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface HealthState {
    object Idle : HealthState
    object Checking : HealthState
    data class Ok(val status: HealthStatus) : HealthState
    data class Failed(val message: String) : HealthState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    currentApiKey: String?,
    currentApiBaseUrl: String,
    currentGraphExplorerUrl: String,
    onApiKeySave: (String) -> Unit,
    onApiBaseUrlSave: (String) -> Unit,
    onGraphExplorerUrlSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey ?: "") }
    LaunchedEffect(currentApiKey) {
        if (!currentApiKey.isNullOrBlank()) apiKeyInput = currentApiKey
    }
    var apiBaseUrlInput by remember { mutableStateOf(currentApiBaseUrl) }
    var graphExplorerUrlInput by remember { mutableStateOf(currentGraphExplorerUrl) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    val platformContext = getPlatformContext()

    val apiKeyDirty = apiKeyInput != (currentApiKey ?: "")
    val apiBaseUrlDirty = apiBaseUrlInput != currentApiBaseUrl
    val graphExplorerUrlDirty = graphExplorerUrlInput != currentGraphExplorerUrl
    val hasChanges = apiKeyDirty || apiBaseUrlDirty || graphExplorerUrlDirty

    var healthState by remember { mutableStateOf<HealthState>(HealthState.Idle) }
    var healthManualTrigger by remember { mutableStateOf(0) }

    suspend fun runHealthCheck(url: String) {
        if (url.isBlank()) { healthState = HealthState.Idle; return }
        healthState = HealthState.Checking
        healthState = when (val result = ApiClient.service.checkHealth(url)) {
            is ApiResult.Success -> HealthState.Ok(result.data)
            is ApiResult.Error   -> HealthState.Failed("HTTP ${result.code}: ${result.message}")
        }
    }

    // Auto-check with 800 ms debounce when the URL changes
    LaunchedEffect(apiBaseUrlInput) {
        delay(800)
        runHealthCheck(apiBaseUrlInput)
    }

    // Immediate check when the user taps "Test"
    LaunchedEffect(healthManualTrigger) {
        if (healthManualTrigger > 0) runHealthCheck(apiBaseUrlInput)
    }

    fun save() {
        if (apiKeyDirty) onApiKeySave(apiKeyInput)
        if (apiBaseUrlDirty) onApiBaseUrlSave(apiBaseUrlInput)
        if (graphExplorerUrlDirty) onGraphExplorerUrlSave(graphExplorerUrlInput)
        showToast(platformContext, "Settings saved")
    }

    fun discard() {
        apiKeyInput = currentApiKey ?: ""
        apiBaseUrlInput = currentApiBaseUrl
        graphExplorerUrlInput = currentGraphExplorerUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHome, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(24.dp))
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = hasChanges,
                enter = expandVertically(expandFrom = androidx.compose.ui.Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Bottom)
            ) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = ::discard,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Discard")
                        }
                        Button(
                            onClick = ::save,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save changes")
                        }
                    }
                }
            }
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                placeholder = { Text(crucible.lens.data.preferences.AppPreferences.DEFAULT_API_BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                trailingIcon = {
                    if (apiBaseUrlInput != crucible.lens.data.preferences.AppPreferences.DEFAULT_API_BASE_URL) {
                        IconButton(onClick = {
                            apiBaseUrlInput = crucible.lens.data.preferences.AppPreferences.DEFAULT_API_BASE_URL
                            healthState = HealthState.Idle
                        }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset to default")
                        }
                    }
                },
                singleLine = true,
                colors = if (apiBaseUrlDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

            // Health status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val hs = healthState) {
                    is HealthState.Idle -> {
                        Text(
                            "REST API endpoint for fetching resources",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is HealthState.Checking -> {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            "Checking server…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is HealthState.Ok -> {
                        val s = hs.status
                        val color = if (s.status == "ok") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                        Icon(
                            if (s.status == "ok") Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = color
                        )
                        val latency = s.dbMs?.let { " · DB ${it.toInt()} ms" } ?: ""
                        val ver = s.version?.let { " · v$it" } ?: ""
                        Text(
                            "Server ${s.status}, DB ${s.db ?: "?"}$latency$ver",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is HealthState.Failed -> {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            hs.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                TextButton(
                    onClick = { healthManualTrigger++ },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Test", style = MaterialTheme.typography.labelMedium)
                }
            }

            OutlinedTextField(
                value = graphExplorerUrlInput,
                onValueChange = { graphExplorerUrlInput = it },
                label = { Text("Graph Explorer URL") },
                placeholder = { Text("https://crucible-graph-explorer-...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                singleLine = true,
                supportingText = { Text("Web interface for viewing entity graphs", style = MaterialTheme.typography.bodySmall) },
                colors = if (graphExplorerUrlDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).animateContentSize(tween(200))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (advancedExpanded) {
                        Text(
                            "For service accounts and developers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("API key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            // Extra bottom space so content isn't hidden behind the save bar
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun dirtyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
)
