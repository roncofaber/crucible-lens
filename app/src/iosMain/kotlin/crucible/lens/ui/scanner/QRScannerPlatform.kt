package crucible.lens.ui.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun QRCodeScannerView(
    modifier: Modifier,
    onCodeScanned: (String) -> Unit
) {
    // TODO: implement with AVFoundation
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("QR Scanner not yet available on iOS")
    }
}
