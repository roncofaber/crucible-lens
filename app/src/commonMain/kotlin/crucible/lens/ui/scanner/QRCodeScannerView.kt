package crucible.lens.ui.scanner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.Scanner

@Composable
fun QRCodeScannerView(
    modifier: Modifier = Modifier,
    onCodeScanned: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val accentColor = MaterialTheme.colorScheme.primary

    val factory = rememberPermissionsControllerFactory()
    val controller = remember(factory) { factory.createPermissionsController() }
    BindEffect(controller)

    var cameraGranted by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        try {
            if (!controller.isPermissionGranted(Permission.CAMERA)) {
                controller.providePermission(Permission.CAMERA)
            }
            cameraGranted = controller.isPermissionGranted(Permission.CAMERA)
        } catch (_: Exception) {
            cameraGranted = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraGranted) {
            Scanner(
                modifier = Modifier.fillMaxSize(),
                onScanned = { code ->
                    onCodeScanned(code)
                    true
                },
                types = listOf(CodeType.QR),
                enableTorch = false
            )
        }

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
