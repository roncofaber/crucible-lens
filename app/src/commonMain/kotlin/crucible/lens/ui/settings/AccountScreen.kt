@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.ui.common.UserAvatar
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNavigateToOrcidLogin: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var lastDraft by remember { mutableStateOf<EditUiState.Editing?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    val currentApiKey by viewModel.currentApiKey.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    val isEditing = editState !is EditUiState.Idle
    val isSaving = editState is EditUiState.Saving
    val activeDraft: EditUiState.Editing? = when (val es = editState) {
        is EditUiState.Editing -> es
        is EditUiState.Saving -> lastDraft
        is EditUiState.SaveError -> es.draft
        else -> null
    }
    val saveError: SaveErrorReason? = (editState as? EditUiState.SaveError)?.reason

    val usernamePattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
    val usernameFormatValid = activeDraft == null ||
        activeDraft.username.isBlank() ||
        usernamePattern.matches(activeDraft.username.lowercase())
    val canSave = !isSaving && activeDraft != null &&
        activeDraft.firstName.isNotBlank() &&
        activeDraft.lastName.isNotBlank() &&
        activeDraft.usernameCheck !is UsernameCheckState.Checking &&
        activeDraft.usernameCheck !is UsernameCheckState.Taken &&
        usernameFormatValid

    LaunchedEffect(currentApiKey) {
        if (apiKeyInput.isEmpty() && !currentApiKey.isNullOrBlank()) {
            apiKeyInput = currentApiKey!!
        }
    }
    LaunchedEffect(editState) {
        val es = editState
        if (es is EditUiState.Editing) lastDraft = es
    }
    // Keyed on currentApiKey (not Unit) so a key set outside this screen's own save flow —
    // e.g. OrcidLoginScreen writes the key straight to prefs/ApiClient without going through
    // this ViewModel — still triggers a profile refresh once the key change propagates here,
    // instead of leaving a stale pre-login profileState until the screen is recreated.
    LaunchedEffect(currentApiKey) { viewModel.loadProfile() }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { AppIcon(AppIcons.SignOut) },
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
            AppTopBar(
                title = "Account",
                onBack = if (isEditing) viewModel::cancelEdit else onBack,
                navIcon = if (isEditing) AppIcons.ClearInput else AppIcons.Back,
                actions = {
                    if (isEditing) {
                        if (isSaving) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = viewModel::saveProfile, enabled = canSave) {
                                AppIcon(AppIcons.Check)
                            }
                        }
                    } else {
                        if (profileState is ProfileUiState.Loaded) {
                            IconButton(onClick = viewModel::startEdit) {
                                AppIcon(AppIcons.Edit)
                            }
                        }
                        IconButton(onClick = onHome) {
                            AppIcon(AppIcons.Home)
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

                    ProfileCard(
                        user = user,
                        isEditing = isEditing,
                        isSaving = isSaving,
                        draft = activeDraft,
                        saveError = saveError,
                        onFirstNameChanged = viewModel::onFirstNameChanged,
                        onLastNameChanged = viewModel::onLastNameChanged,
                        onEmailChanged = viewModel::onEmailChanged,
                        onUsernameChanged = viewModel::onUsernameChanged,
                    )

                    if (!isEditing) {
                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            AppIcon(AppIcons.SignOut, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign out")
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .animateContentSize(StandardSizeAnim)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { advancedExpanded = !advancedExpanded }
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Advanced",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    ExpandChevron(expanded = advancedExpanded)
                                }
                                if (advancedExpanded) {
                                    Text(
                                        "Manually set API key (for service accounts)",
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
                                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                                AppIcon(if (apiKeyVisible) AppIcons.HideContent else AppIcons.ShowContent)
                                            }
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.saveApiKey(apiKeyInput.trim()) },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = apiKeyInput.isNotBlank()
                                    ) {
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
}

@Composable
private fun ProfileCard(
    user: User,
    isEditing: Boolean,
    isSaving: Boolean,
    draft: EditUiState.Editing?,
    saveError: SaveErrorReason?,
    onFirstNameChanged: (String) -> Unit,
    onLastNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
) {
    val platformCtx = getPlatformContext()
    val displayName = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { null }
    val usernamePattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
    val usernameFormatValid = draft == null ||
        draft.username.isBlank() ||
        usernamePattern.matches(draft.username.lowercase())

    Card(modifier = Modifier.fillMaxWidth().animateContentSize(StandardSizeAnim)) {
        Column {
            // Header: avatar + composite name (view) or avatar + first/last fields (edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = if (isEditing) Alignment.Top else Alignment.CenterVertically
            ) {
                UserAvatar(
                    firstName = if (isEditing) draft?.firstName else user.firstName,
                    lastName = if (isEditing) draft?.lastName else user.lastName,
                    size = 48.dp,
                    modifier = if (isEditing) Modifier.padding(top = 8.dp) else Modifier
                )
                if (isEditing && draft != null) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            displayName ?: "No name set",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (displayName != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!user.username.isNullOrBlank()) {
                            Text(
                                "@${user.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Body: email row (view) or email + username fields (edit)
            if (isEditing && draft != null) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (saveError != null) {
                        val msg = when (saveError) {
                            SaveErrorReason.UsernameTaken -> "That username is already taken."
                            SaveErrorReason.Generic -> "Failed to save — check your connection."
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                msg,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.email,
                        onValueChange = onEmailChanged,
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        singleLine = true,
                        leadingIcon = {
                            AppIcon(AppIcons.Email, modifier = Modifier.size(20.dp))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = draft.username,
                        onValueChange = { onUsernameChanged(it.lowercase()) },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving,
                        singleLine = true,
                        leadingIcon = {
                            Text(
                                "@",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        trailingIcon = {
                            when (draft.usernameCheck) {
                                is UsernameCheckState.Checking -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                is UsernameCheckState.Available -> AppIcon(
                                    AppIcons.Success,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                is UsernameCheckState.Taken -> AppIcon(
                                    AppIcons.UsernameTaken,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                else -> {}
                            }
                        },
                        supportingText = {
                            when (draft.usernameCheck) {
                                is UsernameCheckState.Available -> Text(
                                    "Available",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                is UsernameCheckState.Taken -> Text(
                                    "Already taken",
                                    color = MaterialTheme.colorScheme.error
                                )
                                else -> if (!usernameFormatValid && draft.username.isNotBlank()) {
                                    Text(
                                        "3–32 chars: lowercase letters, digits, hyphens, underscores",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            } else if (!user.email.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        AppIcons.Email,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(user.email, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ORCID row — always read-only, tappable
            if (!user.uniqueId.isNullOrBlank()) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUrl(platformCtx, "https://orcid.org/${user.uniqueId}") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        AppIcons.Orcid,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            "ORCID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            user.uniqueId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AppIcon(
                        AppIcons.OpenExternal,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
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
            AppIcon(AppIcons.User, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        AppIcon(if (apiKeyVisible) AppIcons.HideContent else AppIcons.ShowContent)
                    }
                }
            )
            Button(onClick = onApiKeySave, modifier = Modifier.fillMaxWidth(), enabled = apiKeyInput.isNotBlank()) {
                Text("API key")
            }
        }
    }
}
