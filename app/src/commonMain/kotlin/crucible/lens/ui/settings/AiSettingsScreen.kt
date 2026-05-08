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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import crucible.lens.data.preferences.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    currentAiDirectMode: Boolean,
    currentAiApiKey: String?,
    currentAiApiUrl: String,
    onAiDirectModeSave: (Boolean) -> Unit,
    onAiApiKeySave: (String) -> Unit,
    onAiApiUrlSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    var directMode by remember { mutableStateOf(currentAiDirectMode) }
    var aiApiKeyInput by remember { mutableStateOf(currentAiApiKey ?: "") }
    var aiApiUrlInput by remember { mutableStateOf(currentAiApiUrl) }
    var isAiApiKeyVisible by remember { mutableStateOf(false) }
    val platformContext = getPlatformContext()

    val directModeDirty = directMode != currentAiDirectMode
    val aiApiKeyDirty = aiApiKeyInput != (currentAiApiKey ?: "")
    val aiApiUrlDirty = aiApiUrlInput != currentAiApiUrl
    val hasChanges = directModeDirty || aiApiKeyDirty || aiApiUrlDirty

    val directModeNeedsKey = directMode && aiApiKeyInput.isBlank()

    fun save() {
        if (directModeDirty) onAiDirectModeSave(directMode)
        if (aiApiKeyDirty) onAiApiKeySave(aiApiKeyInput)
        if (aiApiUrlDirty) onAiApiUrlSave(aiApiUrlInput)
        showToast(platformContext, "Settings saved")
    }

    fun discard() {
        directMode = currentAiDirectMode
        aiApiKeyInput = currentAiApiKey ?: ""
        aiApiUrlInput = currentAiApiUrl
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Extraction") },
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
                enter = expandVertically(expandFrom = Alignment.Bottom),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = ::discard, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Discard")
                        }
                        Button(onClick = ::save, modifier = Modifier.weight(1f)) {
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
            Text("Extraction Mode", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose how scientific metadata is extracted from notebook photos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (directMode) "Direct AI API call" else "Via Crucible server",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (directMode)
                                    "Phone calls the AI API directly — bypasses server firewall"
                                else
                                    "Crucible server handles the AI call",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = directMode, onCheckedChange = { directMode = it })
                    }

                    if (directModeNeedsKey) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "AI API key required for direct mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (!directMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "In server mode the key and URL below are forwarded to the Crucible server, " +
                            "which uses them for the AI call instead of its own credentials. " +
                            "Leave blank to use the server's default credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("AI API Credentials", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = aiApiKeyInput,
                onValueChange = { aiApiKeyInput = it },
                label = { Text("AI API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isAiApiKeyVisible = !isAiApiKeyVisible }) {
                        Icon(
                            if (isAiApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isAiApiKeyVisible) "Hide" else "Show"
                        )
                    }
                },
                visualTransformation = if (isAiApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = if (aiApiKeyDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors(),
                isError = directModeNeedsKey
            )

            OutlinedTextField(
                value = aiApiUrlInput,
                onValueChange = { aiApiUrlInput = it },
                label = { Text("AI API URL") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                trailingIcon = {
                    if (aiApiUrlInput != AppPreferences.DEFAULT_AI_API_URL) {
                        IconButton(onClick = { aiApiUrlInput = AppPreferences.DEFAULT_AI_API_URL }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset to default")
                        }
                    }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = { Text("Default: ${AppPreferences.DEFAULT_AI_API_URL}", style = MaterialTheme.typography.bodySmall) },
                colors = if (aiApiUrlDirty) dirtyFieldColors() else OutlinedTextFieldDefaults.colors()
            )

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
