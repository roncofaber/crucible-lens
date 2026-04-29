package crucible.lens.ui.common

import androidx.compose.runtime.Composable
import crucible.lens.data.model.CrucibleResource

@Composable
expect fun QrCodeDialog(mfid: String, name: String, onDismiss: () -> Unit)

@Composable
expect fun QrCodeDialogWithNavigation(
    resources: List<CrucibleResource>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onPageChange: (Int) -> Unit = {}
)
