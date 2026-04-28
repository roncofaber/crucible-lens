package crucible.lens.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit
)
