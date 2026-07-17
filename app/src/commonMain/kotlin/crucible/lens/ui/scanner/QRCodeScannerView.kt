package crucible.lens.ui.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import crucible.lens.platform.QRScannerWithPermission

@Composable
fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        QRScannerWithPermission(
            modifier = Modifier.fillMaxSize(),
            onScanned = { code ->
                onCodeScanned(code)
                true
            }
        )

        // Viewfinder square
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

        // Back button overlay
        if (onBack != null) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
