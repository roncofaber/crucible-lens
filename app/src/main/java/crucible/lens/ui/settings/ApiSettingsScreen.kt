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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
    onHome: () -> Unit,
    onSearch: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey ?: "") }
    var apiBaseUrlInput by remember { mutableStateOf(currentApiBaseUrl) }
    var graphExplorerUrlInput by remember { mutableStateOf(currentGraphExplorerUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

        }
    }
}
