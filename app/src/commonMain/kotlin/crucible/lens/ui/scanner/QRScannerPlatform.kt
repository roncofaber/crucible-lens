package crucible.lens.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import qrscanner.QrScanner

@Composable
fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit
) {
    QrScanner(
        modifier = modifier,
        flashlightOn = false,
        openImagePicker = false,
        onCompletion = onCodeScanned,
        imagePickerHandler = {},
        onFailure = {}
    )
}
