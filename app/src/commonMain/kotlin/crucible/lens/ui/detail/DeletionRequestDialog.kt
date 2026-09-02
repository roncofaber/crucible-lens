package crucible.lens.ui.detail
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.CrucibleResource

@Composable
internal fun DeletionRequestDialog(
    resource: CrucibleResource,
    submissionState: DeletionRequestSubmissionState,
    onDismiss: () -> Unit,
    onSubmit: (reason: String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val isSubmitting = submissionState is DeletionRequestSubmissionState.Submitting
    val errorMessage = (submissionState as? DeletionRequestSubmissionState.Error)?.message

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.RequestDeletion)
                Text("Request deletion")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Submit a deletion request for \"${resource.name}\". An admin will review it.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(reason) },
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}
