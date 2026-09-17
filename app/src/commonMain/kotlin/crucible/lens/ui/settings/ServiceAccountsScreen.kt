@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.model.PlatformRole
import crucible.lens.data.model.ServiceAccountCredential
import crucible.lens.data.model.ServiceAccountDetail
import crucible.lens.data.model.ServiceAccountSummary
import crucible.lens.data.util.formatDateTime
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.getPlatformContext
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.ConfirmationDialog

@Composable
fun ServiceAccountsScreen(
    viewModel: ServiceAccountsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val context = getPlatformContext()

    state.credential?.let { credential ->
        CredentialDialog(
            credential = credential,
            onCopy = { copyToClipboard(context, credential.apiKey, "Service account API key") },
            onDismiss = viewModel::dismissCredential
        )
    }
    if (showCreate) {
        CreateServiceAccountDialog(
            isCreating = state.isCreating,
            error = state.createError,
            onCreate = viewModel::create,
            onDismiss = {
                if (!state.isCreating) {
                    showCreate = false
                    viewModel.clearCreateError()
                }
            }
        )
        LaunchedEffect(state.credential) {
            if (state.credential != null) showCreate = false
        }
    }
    state.selected?.let { account ->
        ServiceAccountDetailDialog(
            account = account,
            isSavingRole = state.isSavingRole,
            isRotating = state.isRotatingKey,
            error = state.mutationError,
            onRoleChange = viewModel::updateRole,
            onRotate = viewModel::rotateKey,
            onDismiss = viewModel::dismissDetail
        )
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Service accounts",
                onBack = onBack,
                actions = { IconButton(onClick = onHome) { AppIcon(AppIcons.Home) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (val accounts = state.accounts) {
                LoadState.Loading -> LoadingContent(title = "Loading service accounts")
                is LoadState.Error -> ErrorCard(title = "Could not load service accounts", message = accounts.message, onRetry = viewModel::load)
                is LoadState.Success -> if (accounts.data.isEmpty()) {
                    Text("No service accounts", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(accounts.data, key = { it.uniqueId }) { account ->
                            ServiceAccountRow(account = account, onClick = { viewModel.open(account) })
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            }
            if (state.isLoadingDetail) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.detailError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter)) }
            FloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) { AppIcon(AppIcons.Add) }
        }
    }
}

@Composable
private fun ServiceAccountRow(account: ServiceAccountSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(AppIcons.Key, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("@${account.username}", style = MaterialTheme.typography.titleMedium)
                Text(account.platformRole.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(AppIcons.NavigateNext)
        }
    }
}

@Composable
private fun CreateServiceAccountDialog(
    isCreating: Boolean,
    error: String?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create service account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The API key is shown only once after creation.")
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !isCreating,
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(username) }, enabled = username.isNotBlank() && !isCreating) {
                if (isCreating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") } }
    )
}

@Composable
private fun ServiceAccountDetailDialog(
    account: ServiceAccountDetail,
    isSavingRole: Boolean,
    isRotating: Boolean,
    error: String?,
    onRoleChange: (PlatformRole) -> Unit,
    onRotate: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmRotation by remember(account.uniqueId) { mutableStateOf(false) }
    if (confirmRotation) {
        ConfirmationDialog(
            icon = AppIcons.Key,
            title = "Rotate API key?",
            text = "The current key will stop working immediately. The replacement key will be shown only once.",
            confirmLabel = "Rotate",
            isDestructive = true,
            onConfirm = {
                confirmRotation = false
                onRotate()
            },
            onDismiss = { confirmRotation = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@${account.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(account.uniqueId, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text("Platform role", style = MaterialTheme.typography.labelLarge)
                if (account.platformRole == PlatformRole.Support) {
                    Text("Support", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    listOf(PlatformRole.None, PlatformRole.Contributor, PlatformRole.Admin).forEach { role ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = account.platformRole == role, onClick = { onRoleChange(role) }, enabled = !isSavingRole && !isRotating)
                            Text(role.label)
                        }
                    }
                }
                account.apiKeyStatus?.let {
                    Text(if (it.valid) "API key valid" else "API key invalid", style = MaterialTheme.typography.labelLarge)
                    Text("Created ${formatDateTime(it.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("Expires ${formatDateTime(it.expiresAt)}", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { confirmRotation = true }, enabled = !isSavingRole && !isRotating) {
                if (isRotating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Rotate key")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSavingRole && !isRotating) { Text("Close") } }
    )
}

@Composable
private fun CredentialDialog(
    credential: ServiceAccountCredential,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save this API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This key for @${credential.username} will not be shown again.")
                Text(credential.apiKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    AppIcon(AppIcons.CopyToClipboard, modifier = Modifier.size(18.dp))
                    Text("Copy API key", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("I saved it") } }
    )
}

private val PlatformRole.label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
