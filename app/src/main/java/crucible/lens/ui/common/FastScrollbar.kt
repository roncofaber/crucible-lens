package crucible.lens.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Calculate scrollbar position and visibility
    val showScrollbar by remember {
        derivedStateOf {
            listState.layoutInfo.totalItemsCount > 10 // Only show if more than 10 items
        }
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (showScrollbar && (isDragging || listState.isScrollInProgress)) 0.8f else 0f,
        animationSpec = tween(durationMillis = if (isDragging || listState.isScrollInProgress) 150 else 500),
        label = "scrollbar_alpha"
    )

    if (showScrollbar) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(48.dp)
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            val scrollProgress = if (listState.layoutInfo.totalItemsCount > 0) {
                listState.firstVisibleItemIndex.toFloat() / listState.layoutInfo.totalItemsCount.toFloat()
            } else 0f

            // Scrollbar track (invisible, just for touch area)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { _, dragAmount ->
                                val proportion = (scrollProgress + dragAmount / size.height)
                                    .coerceIn(0f, 1f)
                                val targetIndex = (proportion * listState.layoutInfo.totalItemsCount).toInt()
                                    .coerceIn(0, listState.layoutInfo.totalItemsCount - 1)
                                scope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    }
            )

            // Scrollbar thumb
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .alpha(scrollbarAlpha)
            ) {
                val thumbHeight = 60.dp
                val maxOffset = maxHeight - thumbHeight
                val thumbOffset = maxOffset * scrollProgress

                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .width(6.dp)
                        .height(thumbHeight)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            CircleShape
                        )
                )
            }
        }
    }
}
