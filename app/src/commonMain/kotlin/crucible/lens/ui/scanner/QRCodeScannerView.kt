package crucible.lens.ui.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

@Composable
fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        ScannerWithPermissions(
            modifier = Modifier.fillMaxSize(),
            onScanned = { code ->
                onCodeScanned(code)
                true // stop scanning after first result
            },
            types = listOf(CodeType.QR),
            enableTorch = false
        )

        // Viewfinder square — helps users frame the QR code
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(
                        width = 3.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }
    }
}
