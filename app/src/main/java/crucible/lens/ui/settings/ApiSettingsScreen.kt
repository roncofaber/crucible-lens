package crucible.lens.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import crucible.lens.data.cache.CacheManager
import kotlinx.coroutines.launch

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
    var apiBaseUrlInput by remember { mutableStateOf(currentApiBaseUrl) }
    var graphExplorerUrlInput by remember { mutableStateOf(currentGraphExplorerUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheInfo by remember { mutableStateOf<Pair<Long?, Int>?>(null) }

    LaunchedEffect(Unit) {
        val age = CacheManager.getProjectsAgeMinutes()
        val projects = CacheManager.getProjects()
        val count = projects?.size ?: 0
        cacheInfo = age to count
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("API") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onApiKeySave(apiKeyInput)
                    onApiBaseUrlSave(apiBaseUrlInput)
                    onGraphExplorerUrlSave(graphExplorerUrlInput)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "API settings saved",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("API Key", style = MaterialTheme.typography.titleLarge)
            Text(
                "Enter your Crucible API key to access samples and datasets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Get API key link
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://crucible.lbl.gov/api/v1/user_apikey"))
                    context.startActivity(intent)
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Column {
                            Text("Get Your API Key", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("crucible.lbl.gov/api/v1/user_apikey", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
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
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Endpoints", style = MaterialTheme.typography.titleLarge)
            Text(
                "Leave as default unless you're using a custom deployment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = apiBaseUrlInput,
                onValueChange = { apiBaseUrlInput = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://crucible.lbl.gov/api/v1/") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                singleLine = true,
                supportingText = { Text("REST API endpoint for fetching resources", style = MaterialTheme.typography.bodySmall) }
            )

            OutlinedTextField(
                value = graphExplorerUrlInput,
                onValueChange = { graphExplorerUrlInput = it },
                label = { Text("Graph Explorer URL") },
                placeholder = { Text("https://crucible-graph-explorer-...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                singleLine = true,
                supportingText = { Text("Web interface for viewing entity graphs", style = MaterialTheme.typography.bodySmall) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Cache", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pre-loaded data for faster browsing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cache stats card
            if (cacheInfo != null) {
                val (age, count) = cacheInfo!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Projects Cache",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (age != null) {
                            Text(
                                "Cached ${age}m ago • $count projects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "No cached data",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    CacheManager.clearAll()
                    cacheInfo = null to 0
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
