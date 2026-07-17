package crucible.lens.ui.common
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

/** Standard animation duration — 200ms. */
private const val STANDARD_MS = 200

/** Fast animation duration — 150ms, for nested elements inside already-animated containers. */
private const val FAST_MS = 150

/** Float animation spec — used in animateFloatAsState (e.g. ExpandChevron rotation). */
val StandardAnim = tween<Float>(STANDARD_MS)
val FastAnim = tween<Float>(FAST_MS)

/** IntSize animation spec — used in animateContentSize. */
val StandardSizeAnim = tween<IntSize>(STANDARD_MS)
val FastSizeAnim = tween<IntSize>(FAST_MS)

/**
 * A chevron icon that rotates smoothly between collapsed (-90°) and expanded (0°).
 * Use this everywhere an expandable section needs an animated indicator — it ensures
 * all expand/collapse affordances look and feel identical.
 */
@Composable
fun ExpandChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    fast: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = if (fast) FastAnim else StandardAnim,
        label = "expand_chevron"
    )
    Icon(
        imageVector = Icons.Default.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = modifier.rotate(rotation),
        tint = MaterialTheme.colorScheme.primary
    )
}
