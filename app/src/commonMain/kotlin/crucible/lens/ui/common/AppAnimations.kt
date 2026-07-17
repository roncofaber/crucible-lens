package crucible.lens.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

/** Standard expand/collapse animation — 200ms, used for cards and section headers. */
val StandardAnim = tween<Float>(200)

/** Fast expand/collapse animation — 150ms, used for nested elements within already-animated containers. */
val FastAnim = tween<Float>(150)

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
