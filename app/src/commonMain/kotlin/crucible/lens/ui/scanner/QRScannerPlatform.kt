package crucible.lens.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

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
