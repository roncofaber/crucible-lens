package crucible.lens.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QRScannerWithPermission(
    modifier: Modifier,
    onScanned: (String) -> Boolean
)
