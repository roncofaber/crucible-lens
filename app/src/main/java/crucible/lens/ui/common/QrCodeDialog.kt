package crucible.lens.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import crucible.lens.data.model.CrucibleResource

// Single resource QR dialog (legacy - for compatibility)
@Composable
fun QrCodeDialog(mfid: String, name: String, onDismiss: () -> Unit) {
    val bitmap = remember(mfid) { generateQrBitmap(mfid, 512) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(280.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    mfid,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Swipeable QR dialog for navigating between resources
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QrCodeDialogWithNavigation(
    resources: List<CrucibleResource>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onPageChange: (Int) -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, resources.size - 1),
        pageCount = { resources.size }
    )

    // Trigger page change callback when user swipes
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            onPageChange(pagerState.currentPage)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // Title with navigation arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous indicator
                if (pagerState.currentPage > 0) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Spacer(modifier = Modifier.size(20.dp))
                }

                // Current resource name
                Text(
                    text = resources.getOrNull(pagerState.currentPage)?.name ?: "",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                // Next indicator
                if (pagerState.currentPage < resources.size - 1) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Spacer(modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                val resource = resources[pageIndex]
                val bitmap = remember(resource.uniqueId) { generateQrBitmap(resource.uniqueId, 512) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(280.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        resource.uniqueId,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Page indicator
                    if (resources.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${pagerState.currentPage + 1} / ${resources.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 2)
    )
    return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        for (x in 0 until size) {
            for (y in 0 until size) {
                setPixel(
                    x,
                    y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
    }
}
