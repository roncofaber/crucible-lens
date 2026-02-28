package crucible.lens.ui.common

import androidx.compose.animation.core.animateFloatAsState
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

/**
 * Default values for LazyColumnScrollbar
 */
object ScrollbarDefaults {
    val ThumbWidth = 4.dp
    val ContainerWidth = 16.dp
    val MinThumbHeight = 40.dp
    const val ThumbAlphaActive = 1f
    const val ThumbAlphaInactive = 0.3f
}

/**
 * A custom scrollbar for LazyColumn that properly handles dynamic content heights
 * including expanded/collapsed items.
 *
 * @param listState The LazyListState to observe and control
 * @param modifier Modifier to apply to the scrollbar container
 * @param thumbColor Color of the scrollbar thumb
 * @param thumbWidth Width of the scrollbar thumb
 */
@Composable
fun LazyColumnScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
    thumbWidth: Dp = ScrollbarDefaults.ThumbWidth
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging || listState.isScrollInProgress) {
            ScrollbarDefaults.ThumbAlphaActive
        } else {
            ScrollbarDefaults.ThumbAlphaInactive
        },
        label = "scrollbar_alpha"
    )

    // Persistent cache of all item sizes we've ever seen (survives scrolling)
    val knownItemSizes = remember { mutableStateMapOf<Int, Int>() }

    Box(
        modifier = modifier
            .width(ScrollbarDefaults.ContainerWidth)
            .onGloballyPositioned { containerSize = it.size }
    ) {
        // Force recomposition when list state changes
        val layoutInfo = listState.layoutInfo
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleItems = layoutInfo.visibleItemsInfo

        if (viewportHeight > 0 && containerSize.height > 0 && layoutInfo.totalItemsCount > 0 && visibleItems.isNotEmpty()) {
            // Update our persistent cache with currently visible item sizes
            visibleItems.forEach { itemInfo ->
                knownItemSizes[itemInfo.index] = itemInfo.size
            }

            // Calculate average from visible items for unknown items
            val defaultItemHeight = visibleItems.sumOf { it.size } / visibleItems.size.toFloat()

            // Calculate total height using known sizes when available, fallback to average
            val estimatedTotalHeight = remember(knownItemSizes.size, layoutInfo.totalItemsCount, defaultItemHeight) {
                var height = 0f
                for (i in 0 until layoutInfo.totalItemsCount) {
                    height += knownItemSizes[i]?.toFloat() ?: defaultItemHeight
                }
                height
            }

            // Calculate current scroll position by summing items before first visible
            val currentScrollY = remember(firstVisibleIndex, firstVisibleOffset, knownItemSizes.size, defaultItemHeight) {
                var scrollY = 0f
                for (i in 0 until firstVisibleIndex) {
                    scrollY += knownItemSizes[i]?.toFloat() ?: defaultItemHeight
                }
                scrollY + firstVisibleOffset
            }

            // Thumb metrics
            val contentToViewportRatio = (viewportHeight / estimatedTotalHeight).coerceIn(0.05f, 1f)
            val thumbHeight = (containerSize.height * contentToViewportRatio).coerceIn(
                with(density) { ScrollbarDefaults.MinThumbHeight.toPx() },
                containerSize.height.toFloat()
            )

            val scrollRange = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)
            val scrollFraction = (currentScrollY / scrollRange).coerceIn(0f, 1f)
            val thumbTop = scrollFraction * (containerSize.height - thumbHeight)

            // Only show scrollbar if content is scrollable
            if (contentToViewportRatio < 1f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(thumbAlpha)
                        .pointerInput(estimatedTotalHeight) {
                            var dragStartY = 0f
                            var dragStartScrollOffset = 0f

                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragStartY = offset.y
                                    dragStartScrollOffset = currentScrollY

                                    // Jump to tapped position if not on thumb
                                    if (offset.y < thumbTop || offset.y > thumbTop + thumbHeight) {
                                        val clickProgress = (offset.y / containerSize.height).coerceIn(0f, 1f)
                                        val targetScroll = clickProgress * scrollRange

                                        // Find which item index corresponds to this scroll position
                                        var accumulatedHeight = 0f
                                        var targetIndex = 0
                                        for (i in 0 until layoutInfo.totalItemsCount) {
                                            val itemHeight = knownItemSizes[i]?.toFloat() ?: defaultItemHeight
                                            if (accumulatedHeight + itemHeight > targetScroll) {
                                                targetIndex = i
                                                break
                                            }
                                            accumulatedHeight += itemHeight
                                        }
                                        val targetOffset = (targetScroll - accumulatedHeight).toInt().coerceAtLeast(0)

                                        scope.launch {
                                            listState.scrollToItem(targetIndex, targetOffset)
                                        }
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            ) { change, _ ->
                                change.consume()

                                // Calculate new scroll position based on drag
                                val targetScroll = (dragStartScrollOffset + (change.position.y - dragStartY) / (containerSize.height - thumbHeight) * scrollRange)
                                    .coerceIn(0f, scrollRange)

                                // Find which item index corresponds to this scroll position
                                var accumulatedHeight = 0f
                                var targetIndex = 0
                                for (i in 0 until layoutInfo.totalItemsCount) {
                                    val itemHeight = knownItemSizes[i]?.toFloat() ?: defaultItemHeight
                                    if (accumulatedHeight + itemHeight > targetScroll) {
                                        targetIndex = i
                                        break
                                    }
                                    accumulatedHeight += itemHeight
                                }
                                val targetOffset = (targetScroll - accumulatedHeight).toInt().coerceAtLeast(0)

                                scope.launch {
                                    listState.scrollToItem(targetIndex, targetOffset)
                                }
                            }
                        }
                ) {
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(size.width - with(density) { thumbWidth.toPx() }, thumbTop),
                        size = Size(
                            with(density) { thumbWidth.toPx() },
                            thumbHeight
                        ),
                        cornerRadius = CornerRadius(
                            with(density) { (thumbWidth / 2).toPx() }
                        )
                    )
                }
            }
        }
    }
}
