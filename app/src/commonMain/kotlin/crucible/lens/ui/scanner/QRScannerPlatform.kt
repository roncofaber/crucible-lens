package crucible.lens.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kalinjul.easyqrscan.scanner.CodeType
import io.github.kalinjul.easyqrscan.scanner.ScannerWithPermissions

@Composable
fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit
) {
    ScannerWithPermissions(
        modifier = modifier,
        onScanned = { onCodeScanned(it) },
        types = listOf(CodeType.QR)
    )
}
