package crucible.lens.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Confirmation shown when a navigation action (back, home) would discard in-progress form input.
 * Callers gate this on their own "has the user typed anything" check — this dialog has no opinion
 * on what counts as unsaved, only on what to do once something does.
 */
@Composable
fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved changes that will be lost.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep editing") } }
    )
}
