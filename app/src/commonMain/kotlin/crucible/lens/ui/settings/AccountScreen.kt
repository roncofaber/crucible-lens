package crucible.lens.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.User
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.UserAvatar
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.detail.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onNavigateToOrcidLogin: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var lastDraft by remember { mutableStateOf<EditUiState.Editing?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(editState) {
        if (editState is EditUiState.Editing) {
            lastDraft = editState as EditUiState.Editing
        }
    }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Sign out?") },
            text = { Text("You will need to sign in again to access Crucible.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; viewModel.signOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (profileState) {
                is ProfileUiState.Idle, is ProfileUiState.Loading -> LoadingContent(title = "Loading account")
                is ProfileUiState.NotLoggedIn -> NotLoggedInCard(
                    onNavigateToOrcidLogin = onNavigateToOrcidLogin,
                    apiKeyInput = apiKeyInput,
                    apiKeyVisible = apiKeyVisible,
                    onApiKeyChanged = { apiKeyInput = it },
                    onApiKeyVisibilityToggle = { apiKeyVisible = !apiKeyVisible },
                    onApiKeySave = { viewModel.saveApiKey(apiKeyInput.trim()) }
                )
                is ProfileUiState.Error -> ErrorCard(
                    title = "Could not load profile",
                    message = (profileState as ProfileUiState.Error).message,
                    onRetry = { viewModel.retryLoad() }
                )
                is ProfileUiState.Loaded -> {
                    val user = (profileState as ProfileUiState.Loaded).user
                    AccountHeaderCard(user)
                    when (val es = editState) {
                        is EditUiState.Idle -> ProfileViewCard(user, onEdit = { viewModel.startEdit() })
                        is EditUiState.Editing -> ProfileEditForm(
                            draft = es,
                            isSaving = false,
                            onFirstNameChanged = viewModel::onFirstNameChanged,
                            onLastNameChanged = viewModel::onLastNameChanged,
                            onEmailChanged = viewModel::onEmailChanged,
                            onUsernameChanged = viewModel::onUsernameChanged,
                            onSave = { viewModel.saveProfile() },
                            onCancel = { viewModel.cancelEdit() }
                        )
                        is EditUiState.Saving -> {
                            val draftToShow = lastDraft ?: EditUiState.Editing("", "", "", "")
                            ProfileEditForm(
                                draft = draftToShow,
                                isSaving = true,
                                onFirstNameChanged = {},
                                onLastNameChanged = {},
                                onEmailChanged = {},
                                onUsernameChanged = {},
                                onSave = {},
                                onCancel = {}
                            )
                        }
                        is EditUiState.SaveError -> {
                            ProfileEditForm(
                                draft = es.draft,
                                isSaving = false,
                                saveError = es.reason,
                                onFirstNameChanged = viewModel::onFirstNameChanged,
                                onLastNameChanged = viewModel::onLastNameChanged,
                                onEmailChanged = viewModel::onEmailChanged,
                                onUsernameChanged = viewModel::onUsernameChanged,
                                onSave = { viewModel.saveProfile() },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out")
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).animateContentSize(tween(200))) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded }.padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Icon(if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (advancedExpanded) {
                                Text("Manually set API key (for service accounts)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("API key") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (apiKeyVisible) "Hide key" else "Show key")
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { viewModel.saveApiKey(apiKeyInput.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = apiKeyInput.isNotBlank()) {
                                    Text("Apply key")
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotLoggedInCard(
    onNavigateToOrcidLogin: () -> Unit,
    apiKeyInput: String,
    apiKeyVisible: Boolean,
    onApiKeyChanged: (String) -> Unit,
    onApiKeyVisibilityToggle: () -> Unit,
    onApiKeySave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Sign in to view and edit your profile", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onNavigateToOrcidLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in with ORCID")
            }
            HorizontalDivider()
            Text("Or enter your API key directly", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = onApiKeyChanged,
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onApiKeyVisibilityToggle) {
                        Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (apiKeyVisible) "Hide" else "Show")
                    }
                }
            )
            Button(onClick = onApiKeySave, modifier = Modifier.fillMaxWidth(), enabled = apiKeyInput.isNotBlank()) {
                Text("Sign in with API key")
            }
        }
    }
}

@Composable
private fun AccountHeaderCard(user: User) {
    val platformCtx = getPlatformContext()
    val displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { "Unknown" }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(firstName = user.firstName, lastName = user.lastName, size = 52.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!user.username.isNullOrBlank()) {
                    Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("No username set", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!user.uniqueId.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.uniqueId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { copyToClipboard(platformCtx, user.uniqueId) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy ORCID", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileViewCard(user: User, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = Icons.Default.Person, label = "First name", value = user.firstName ?: "—")
            InfoRow(icon = Icons.Default.Person, label = "Last name", value = user.lastName ?: "—")
            InfoRow(icon = Icons.Default.Email, label = "Email", value = user.email ?: "—")
            InfoRow(icon = Icons.Default.Badge, label = "Username", value = if (!user.username.isNullOrBlank()) "@${user.username}" else "—")
            InfoRow(icon = Icons.Default.Badge, label = "ORCID", value = user.uniqueId ?: "—")
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit profile")
            }
        }
    }
}

@Composable
private fun ProfileEditForm(
    draft: EditUiState.Editing,
    isSaving: Boolean,
    saveError: SaveErrorReason? = null,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val usernamePattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
    val usernameFormatValid = draft.username.isBlank() || usernamePattern.matches(draft.username.lowercase())
    val canSave = !isSaving &&
        draft.firstName.isNotBlank() &&
        draft.lastName.isNotBlank() &&
        draft.usernameCheck !is UsernameCheckState.Checking &&
        draft.usernameCheck !is UsernameCheckState.Taken &&
        usernameFormatValid

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(tween(200)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (saveError != null) {
                val msg = when (saveError) {
                    SaveErrorReason.UsernameTaken -> "That username is already taken."
                    SaveErrorReason.Generic -> "Failed to save — check your connection."
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(
                value = draft.firstName,
                onValueChange = onFirstNameChanged,
                label = { Text("First name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.lastName,
                onValueChange = onLastNameChanged,
                label = { Text("Last name") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.email,
                onValueChange = onEmailChanged,
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.username,
                onValueChange = { onUsernameChanged(it.lowercase()) },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { Text("@", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp)) },
                trailingIcon = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Checking -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        is UsernameCheckState.Available -> Icon(Icons.Default.CheckCircle, "Available", tint = MaterialTheme.colorScheme.primary)
                        is UsernameCheckState.Taken -> Icon(Icons.Default.Cancel, "Taken", tint = MaterialTheme.colorScheme.error)
                        else -> {}
                    }
                },
                supportingText = {
                    when (draft.usernameCheck) {
                        is UsernameCheckState.Available -> Text("Available", color = MaterialTheme.colorScheme.primary)
                        is UsernameCheckState.Taken -> Text("Already taken", color = MaterialTheme.colorScheme.error)
                        else -> if (!usernameFormatValid && draft.username.isNotBlank()) {
                            Text("3–32 chars: lowercase letters, digits, hyphens, underscores", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Cancel")
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = canSave) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}
