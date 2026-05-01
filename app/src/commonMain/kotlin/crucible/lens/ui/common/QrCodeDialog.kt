package crucible.lens.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import crucible.lens.data.model.CrucibleResource
import qrgenerator.qrkitpainter.rememberQrKitPainter

@Composable
fun QrCodeDialog(mfid: String, name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val scrollState = rememberScrollState()
            val showFade = scrollState.canScrollForward
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        if (showFade) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = size.width * 0.75f,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.horizontalScroll(scrollState)
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 0.dp
                ) {
                    Image(
                        painter = rememberQrKitPainter(mfid),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(256.dp)
                    )
                }
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

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

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) onPageChange(pagerState.currentPage)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    Icon(
                        Icons.Default.ChevronLeft, contentDescription = "Previous",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Spacer(Modifier.size(20.dp))
                }
                // Scrollable title with right-side fade for long names
                key(pagerState.currentPage) {
                    val scrollState = rememberScrollState()
                    val showFade = scrollState.canScrollForward
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                if (showFade) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color.Black, Color.Transparent),
                                            startX = size.width * 0.75f,
                                            endX = size.width
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    ) {
                        Text(
                            text = resources.getOrNull(pagerState.currentPage)?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.horizontalScroll(scrollState)
                        )
                    }
                }
                if (pagerState.currentPage < resources.size - 1) {
                    Icon(
                        Icons.Default.ChevronRight, contentDescription = "Next",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Spacer(Modifier.size(20.dp))
                }
            }
        },
        text = {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val resource = resources.getOrNull(page)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resource != null) {
                        Surface(
                            color = Color.White,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 0.dp
                        ) {
                            Image(
                                painter = rememberQrKitPainter(resource.uniqueId),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(256.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            resource.uniqueId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
