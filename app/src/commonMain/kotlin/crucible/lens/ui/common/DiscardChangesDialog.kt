package crucible.lens.ui.common

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
    ConfirmationDialog(
        title = "Discard changes?",
        text = "You have unsaved changes that will be lost.",
        confirmLabel = "Discard",
        dismissLabel = "Keep editing",
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
