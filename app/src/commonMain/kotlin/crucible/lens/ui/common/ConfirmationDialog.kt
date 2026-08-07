package crucible.lens.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * Shared shell for a "confirm this one action" dialog — icon inline to the left of the title
 * (not stacked above it, `AlertDialog`'s own default) plus a body sentence and confirm/cancel.
 *
 * [title] must be phrased as a question ending in "?" — that's the one thing distinguishing this
 * from a form or informational dialog, which reach for a bare `AlertDialog` instead (see
 * `dev/style.md`). [isDestructive] tints both the icon and the confirm label `error` — matching
 * the majority convention already in place (Sign out, Remove member, Leave project), not the
 * filled-error-`Button` treatment a couple of dialogs used instead — both actions in an M3 dialog
 * are meant to be `TextButton`s, not one text and one filled.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: AppIconToken? = null,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    AppIcon(icon, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(title)
            }
        },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}
