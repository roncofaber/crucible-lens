package crucible.lens.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QRScannerScreen(
    onQRCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
)
