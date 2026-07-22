package crucible.lens.ui.common

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.IntSize

// Screen-level navigation transitions use tween (M3 spec: easing curves for screen-to-screen
// transitions, springs for component-level). Enter is longer than exit — gives the incoming
// screen time to settle while the outgoing screen exits quickly.
const val NavEnterDuration = 300
const val NavExitDuration = 200

// Spatial spring specs — for movement, rotation, size (M3 standard scheme: no bounce)
val SpatialDefaultSpring: FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
val SpatialFastSpring: FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, 600f)
val SpatialDefaultSizeSpring: FiniteAnimationSpec<IntSize> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
val SpatialFastSizeSpring: FiniteAnimationSpec<IntSize> = spring(Spring.DampingRatioNoBouncy, 600f)

// Effects spring specs — for opacity and color (no overshoot)
val EffectsDefaultSpring: FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
val EffectsFastSpring: FiniteAnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, 600f)

// Backward-compat aliases used across the app — now spring-based
val StandardAnim: FiniteAnimationSpec<Float> = SpatialDefaultSpring
val FastAnim: FiniteAnimationSpec<Float> = SpatialFastSpring
val StandardSizeAnim: FiniteAnimationSpec<IntSize> = SpatialDefaultSizeSpring
val FastSizeAnim: FiniteAnimationSpec<IntSize> = SpatialFastSizeSpring

/**
 * Rotating chevron used consistently for all expand/collapse affordances.
 * `fast = true` uses a stiffer spring for nested elements inside already-animating containers.
 */
@Composable
fun ExpandChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    fast: Boolean = false
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = if (fast) SpatialFastSpring else SpatialDefaultSpring,
        label = "expand_chevron"
    )
    AppIcon(
        AppIcons.ExpandMore,
        modifier = modifier.rotate(rotation),
        tint = MaterialTheme.colorScheme.primary
    )
}
