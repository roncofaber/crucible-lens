package crucible.lens.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.TransferOwnershipResponse
import crucible.lens.data.model.User
import crucible.lens.data.util.userDisplayName

data class OwnershipTransferDraft(
    val query: String = "",
    val results: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val actionError: String? = null
)

sealed class OwnershipTransferState {
    data object Idle : OwnershipTransferState()
    data class Selecting(val draft: OwnershipTransferDraft = OwnershipTransferDraft()) : OwnershipTransferState()
    data class Previewing(val draft: OwnershipTransferDraft) : OwnershipTransferState()
    data class PreviewReady(val preview: TransferOwnershipResponse, val error: String? = null) : OwnershipTransferState()
    data class Transferring(val preview: TransferOwnershipResponse) : OwnershipTransferState()
    data class Success(val newOwner: User) : OwnershipTransferState()
}

sealed class ResourceRenameState {
    data object Idle : ResourceRenameState()
    data class Editing(val value: String, val error: String? = null) : ResourceRenameState()
    data class Saving(val value: String) : ResourceRenameState()
    data class Success(val resourceId: String) : ResourceRenameState()
}

@Composable
fun ResourceIdRenameDialog(
    resourceName: String,
    value: String,
    error: String?,
    isSaving: Boolean,
    impactText: String,
    onValueChange: (String) -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Rename $resourceName ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !isSaving,
                    label = { Text("${resourceName.replaceFirstChar { it.uppercase() }} ID") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Use 3 to 25 letters, numbers, underscores, or hyphens") }
                )
                Text(impactText)
            }
        },
        confirmButton = {
            TextButton(onClick = onRename, enabled = !isSaving) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") } }
    )
}

@Composable
fun OwnershipTransferPickerSheet(
    title: String,
    supportingText: String,
    draft: OwnershipTransferDraft,
    onQueryChange: (String) -> Unit,
    onSelect: (User) -> Unit,
    onDismiss: () -> Unit
) {
    SearchPickerSheet(
        title = title,
        query = draft.query,
        onQueryChange = onQueryChange,
        isSearching = draft.isSearching,
        results = draft.results,
        onDismiss = onDismiss,
        label = "Search user",
        key = { it.uniqueId ?: it.username ?: it.hashCode().toString() },
        supportingContent = {
            val message = draft.actionError ?: draft.searchError
            if (message != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (draft.searchError != null) TextButton(onClick = { onQueryChange(draft.query) }) { Text("Retry") }
                }
            } else {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        emptyContent = {
            if (draft.query.length >= 3 && !draft.isSearching && draft.searchError == null) {
                Text("No eligible users found for \"${draft.query}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        itemContent = { user ->
            UserResultItem(
                user = user,
                onClick = { onSelect(user) },
                trailingContent = { AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }
    )
}

@Composable
fun OwnershipTransferProgressDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Preparing transfer") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Confirming the selected account with Crucible")
            }
        },
        confirmButton = {}
    )
}

@Composable
fun OwnershipTransferConfirmationDialog(
    title: String,
    description: String,
    preview: TransferOwnershipResponse,
    error: String?,
    isTransferring: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isTransferring) onDismiss() },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.ManageMembers)
                Text(title)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transfer ownership from ${userDisplayName(preview.previousOwner)} to ${userDisplayName(preview.newOwner)}?")
                Text(description)
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isTransferring) {
                if (isTransferring) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Transfer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isTransferring) { Text("Cancel") } }
    )
}
