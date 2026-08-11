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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.User
import crucible.lens.data.util.formatDateTime
import crucible.lens.data.util.userDisplayName
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ConfirmationDialog
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
    onNavigateToOrcidLogin: () -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val profileState by viewModel.profileState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    // Plain val (not `by`), so the compiler can smart-cast it below - a delegated property's
    // getter isn't guaranteed to return the same value on repeated reads.
    val activeDraft: EditUiState.Editing? = viewModel.activeDraft.collectAsState().value
    val joinRequests by viewModel.joinRequests.collectAsState()
    val reviewerInfo by viewModel.reviewerInfo.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var joinRequestsExpanded by remember { mutableStateOf(false) }
    val currentApiKey by viewModel.currentApiKey.collectAsState()
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    val isEditing = editState !is EditUiState.Idle
    val isSaving = editState is EditUiState.Saving
    val saveError: SaveErrorReason? = (editState as? EditUiState.SaveError)?.reason

    val usernameFormatValid = activeDraft?.usernameFormatValid ?: true
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
    // Keyed on currentApiKey (not Unit) so a key set outside this screen's own save flow —
    // e.g. OrcidLoginScreen writes the key straight to prefs/ApiClient without going through
    // this ViewModel — still triggers a profile refresh once the key change propagates here,
    // instead of leaving a stale pre-login profileState until the screen is recreated.
    LaunchedEffect(currentApiKey) { viewModel.loadProfile() }

    if (showSignOutDialog) {
        ConfirmationDialog(
            icon = AppIcons.SignOut,
            title = "Sign out?",
            text = "You will need to sign in again to access Crucible.",
            confirmLabel = "Sign out",
            isDestructive = true,
            onConfirm = { showSignOutDialog = false; viewModel.signOut() },
            onDismiss = { showSignOutDialog = false }
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
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    ExpandChevron(expanded = advancedExpanded)
                                }
                                if (advancedExpanded) {
                                    val apiKeyPlatformCtx = getPlatformContext()
                                    Text(
                                        "Manually set API key",
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { copyToClipboard(apiKeyPlatformCtx, apiKeyInput) },
                                                    enabled = apiKeyInput.isNotBlank()
                                                ) {
                                                    AppIcon(AppIcons.CopyToClipboard)
                                                }
                                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                                    AppIcon(if (apiKeyVisible) AppIcons.HideContent else AppIcons.ShowContent)
                                                }
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

                        if (joinRequests.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateContentSize(StandardSizeAnim)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { joinRequestsExpanded = !joinRequestsExpanded }
                                            .padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "My Join Requests (${joinRequests.size})",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        ExpandChevron(expanded = joinRequestsExpanded)
                                    }
                                    if (joinRequestsExpanded) {
                                        Column(
                                            modifier = Modifier.padding(bottom = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            joinRequests.forEach { request ->
                                                JoinRequestRow(request, reviewer = request.reviewerId?.let { reviewerInfo[it] }, onUserClick = onUserClick)
                                            }
                                        }
                                    }
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

    Card(modifier = Modifier.fillMaxWidth().animateContentSize(StandardSizeAnim)) {
        Column {
            if (isEditing && draft != null) {
                ProfileEditFields(
                    draft = draft,
                    orcid = user.uniqueId,
                    isSaving = isSaving,
                    saveError = saveError,
                    onFirstNameChanged = onFirstNameChanged,
                    onLastNameChanged = onLastNameChanged,
                    onEmailChanged = onEmailChanged,
                    onUsernameChanged = onUsernameChanged
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        size = 48.dp,
                        orcid = user.uniqueId
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            displayName ?: "No name set",
                            style = MaterialTheme.typography.titleMedium,
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

                HorizontalDivider()

                if (!user.email.isNullOrBlank()) {
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
                            style = MaterialTheme.typography.bodySmall,
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
private fun JoinRequestRow(request: JoinRequest, reviewer: User?, onUserClick: (String) -> Unit = {}) {
    val (statusLabel, statusColor) = when (request.status) {
        "approved" -> "Approved" to MaterialTheme.colorScheme.primary
        "rejected" -> "Rejected" to MaterialTheme.colorScheme.error
        else -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(AppIcons.Project, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(request.groupName, style = MaterialTheme.typography.bodyMedium)
            Text(formatDateTime(request.requestTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val reviewerId = request.reviewerId
            if (reviewerId != null) {
                val reviewerLabel = userDisplayName(reviewer?.firstName, reviewer?.lastName, reviewer?.username, reviewerId)
                Text(
                    "Reviewed by $reviewerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onUserClick(reviewer?.username ?: reviewerId) }
                )
            }
        }
        Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
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
            Text("Or enter your API key directly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
