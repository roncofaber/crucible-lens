package crucible.lens.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.Scanner

@Composable
actual fun QRScannerWithPermission(
    modifier: Modifier,
    onScanned: (String) -> Boolean
) {
    Scanner(
        modifier = modifier,
        onScanned = onScanned,
        types = listOf(CodeType.QR),
        enableTorch = false
    )
}
