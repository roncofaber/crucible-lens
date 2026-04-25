package crucible.lens.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

object ScrollbarDefaults {
    val ThumbWidth = 4.dp
    val ContainerWidth = 16.dp
    val MinThumbHeight = 40.dp
}

/**
 * Scrollbar for LazyColumn using item-index fractions instead of pixel-height estimation.
 * This avoids thumb jumping when items expand/collapse or when item heights vary.
 */
@Composable
fun LazyColumnScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    thumbWidth: Dp = ScrollbarDefaults.ThumbWidth
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isActive = isDragging || listState.isScrollInProgress
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = if (isActive) 100 else 600),
        label = "scrollbar_alpha"
    )

    Box(
        modifier = modifier
            .width(ScrollbarDefaults.ContainerWidth)
            .onGloballyPositioned { containerSize = it.size }
    ) {
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo

        if (totalItems <= 0 || visibleItems.isEmpty() || containerSize.height <= 0) return@Box

        // Fraction of content visible — used for thumb height.
        // Index-based: stable regardless of item heights or expand/collapse state.
        val visibleCount = visibleItems.size
        val thumbHeightFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.05f, 1f)
        if (thumbHeightFraction >= 1f) return@Box

        val thumbWidthPx = with(density) { thumbWidth.toPx() }
        val minThumbPx = with(density) { ScrollbarDefaults.MinThumbHeight.toPx() }
        val thumbHeightPx = (containerSize.height * thumbHeightFraction)
            .coerceAtLeast(minThumbPx)
            .coerceAtMost(containerSize.height.toFloat())

        val scrollableItems = (totalItems - visibleCount).coerceAtLeast(1)

        // Detect stuck sticky headers: if there's a gap between the first and second visible
        // item indices, items have scrolled off-screen behind a pinned header.
        // In that case, use the second item's index so the thumb tracks within the group.
        val sortedVisible = visibleItems.sortedBy { it.index }
        val firstItem = sortedVisible.firstOrNull()
        val secondItem = sortedVisible.drop(1).firstOrNull()
        val isStuckStickyHeader = firstItem != null && secondItem != null &&
                (secondItem.index - firstItem.index) > 1
        val effectiveFirstIndex = if (isStuckStickyHeader) secondItem!!.index
                                  else listState.firstVisibleItemIndex

        val scrollFraction = (effectiveFirstIndex.toFloat() / scrollableItems).coerceIn(0f, 1f)
        val thumbTop = scrollFraction * (containerSize.height - thumbHeightPx)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(thumbAlpha)
                .pointerInput(scrollableItems) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val fraction = (offset.y / containerSize.height).coerceIn(0f, 1f)
                            scope.launch {
                                listState.scrollToItem((fraction * scrollableItems).toInt())
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / containerSize.height).coerceIn(0f, 1f)
                        scope.launch {
                            listState.scrollToItem((fraction * scrollableItems).toInt())
                        }
                    }
                }
        ) {
            // Faint track
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.15f),
                topLeft = Offset(size.width - thumbWidthPx, 0f),
                size = Size(thumbWidthPx, size.height),
                cornerRadius = CornerRadius(thumbWidthPx / 2)
            )
            // Thumb
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(size.width - thumbWidthPx, thumbTop),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2)
            )
        }
    }
}
