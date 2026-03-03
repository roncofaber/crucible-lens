package crucible.lens.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A pull-to-refresh indicator with smooth spring animation.
 * The indicator scales up as you pull down and bounces back when released,
 * creating a satisfying spring-like effect.
 *
 * @param state The pull-to-refresh state from rememberPullToRefreshState()
 * @param modifier Optional modifier for positioning (typically Modifier.align(Alignment.TopCenter))
 * @param visible Whether the indicator should be visible (default: shows when pulling or refreshing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedPullToRefreshIndicator(
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    visible: Boolean = state.isRefreshing || state.verticalOffset > 0f
) {
    if (visible) {
        // Calculate pull progress (0 to 1)
        val pullProgress = (state.verticalOffset / 150f).coerceIn(0f, 1f)

        // Animated scale with spring effect - starts at 0.7x and grows to 1.0x
        val indicatorScale by animateFloatAsState(
            targetValue = if (state.isRefreshing) {
                1f
            } else {
                0.7f + (pullProgress * 0.3f)
            },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "pull_refresh_scale"
        )

        // Animated alpha for smooth fade in/out
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (state.isRefreshing) 1f else pullProgress,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "pull_refresh_alpha"
        )

        PullToRefreshContainer(
            state = state,
            modifier = modifier
                .graphicsLayer {
                    // Apply spring-animated scale for bouncy effect
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                    // Smooth fade in/out
                    alpha = indicatorAlpha
                }
        )
    }
}
