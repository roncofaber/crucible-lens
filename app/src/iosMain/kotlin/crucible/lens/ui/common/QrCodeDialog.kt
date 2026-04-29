package crucible.lens.ui.common

import androidx.compose.runtime.Composable
import crucible.lens.data.model.CrucibleResource

@Composable
actual fun QrCodeDialog(mfid: String, name: String, onDismiss: () -> Unit) {}

@Composable
actual fun QrCodeDialogWithNavigation(
    resources: List<CrucibleResource>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onPageChange: (Int) -> Unit
) {}
