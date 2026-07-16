package crucible.lens.ui.settings

import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.showToast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
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
import crucible.lens.data.model.User
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
    onUserOrcidSave: (String?) -> Unit = {},
    onSignOut: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey ?: "") }
    LaunchedEffect(currentApiKey) {
        if (!currentApiKey.isNullOrBlank()) apiKeyInput = currentApiKey
    }
    var apiBaseUrlInput by remember { mutableStateOf(currentApiBaseUrl) }
    var graphExplorerUrlInput by remember { mutableStateOf(currentGraphExplorerUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
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

    var account by remember { mutableStateOf<User?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountErrorMsg by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(currentApiKey, refreshTrigger) {
        if (currentApiKey.isNullOrBlank()) {
            account = null; accountErrorMsg = null; return@LaunchedEffect
        }
        accountLoading = true; accountErrorMsg = null
        try {
            when (val resp = ApiClient.service.getAccount()) {
                is ApiResult.Success -> {
                    val body = resp.data.userInfo
                    account = body
                    accountErrorMsg = if (body == null) "No account info returned" else null
                    onUserOrcidSave(body?.uniqueId)
                }
                is ApiResult.Error -> {
                    account = null
                    accountErrorMsg = resp.message
                }
            }
        } catch (e: Exception) {
            account = null
            accountErrorMsg = e.message ?: "Unknown error"
        }
        accountLoading = false
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
            Text("API Key", style = MaterialTheme.typography.titleLarge)
            Text(
                "Enter your Crucible API key to access samples and datasets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with ORCID")
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isApiKeyVisible) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                colors = if (apiKeyDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

            when {
                accountLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) }

                account != null -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val acc = account ?: return@Row
                            Text(
                                "${acc.firstName ?: ""} ${acc.lastName ?: ""}".trim(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            acc.email?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            acc.uniqueId?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                accountErrorMsg != null -> Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(accountErrorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { refreshTrigger++ }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

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
