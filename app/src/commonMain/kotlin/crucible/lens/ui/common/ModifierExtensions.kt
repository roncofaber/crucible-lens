package crucible.lens.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades the trailing edge of content horizontally using a mask.
 * The fade starts at [startFraction] of the width and reaches full transparency at the end.
 * Only applies when [visible] is true (e.g. when the content is scrollable).
 */
fun Modifier.fadeEndEdge(
    visible: Boolean = true,
    startFraction: Float = 0.75f
): Modifier = if (!visible) this else this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width * startFraction,
                endX = size.width
            ),
            blendMode = BlendMode.DstIn
        )
    }
