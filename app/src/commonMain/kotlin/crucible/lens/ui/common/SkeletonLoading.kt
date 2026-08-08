package crucible.lens.ui.common

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Row-shaped placeholders for a list's initial `LoadState.Loading`, shown instead of a spinner so
 * the loading state previews what's about to appear and the swap to real content doesn't jump —
 * the skeleton row occupies the same footprint as the row it becomes. Not for small,
 * individually-loading pieces (a count chip, a submit button) - a spinner is still the right call
 * there; see `dev/style.md`'s "Skeleton loading placeholders" section for the line between the two.
 *
 * Pulses between `surfaceContainerHigh` and `surfaceContainerHighest` - both real M3 roles,
 * crossfaded via `InfiniteTransition.animateColor` - rather than animating alpha on a single
 * color, per the project's "no custom alpha" rule (`dev/style.md`). Same technique
 * `SectionHeader`'s expand/collapse container crossfade uses, just looped instead of one-shot.
 */
@Composable
private fun rememberSkeletonPulseColor(): State<Color> {
    val transition = rememberInfiniteTransition(label = "skeleton_pulse")
    return transition.animateColor(
        initialValue = MaterialTheme.colorScheme.surfaceContainerHigh,
        targetValue = MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = infiniteRepeatable(
            animation = tween(SkeletonPulseDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_pulse_color"
    )
}

/** A single pulsing placeholder shape - the building block `SkeletonRow` composes from. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall
) {
    val color by rememberSkeletonPulseColor()
    Box(modifier = modifier.background(color, shape))
}

/**
 * A placeholder row matching this app's two real row shapes: a `ListItem`-based row with a
 * leading icon (`ProjectCard`, `InstrumentCard`) when [hasLeadingIcon] is true, or an icon-less
 * `ResourceRow` (search results, dataset lists) when it's false - the 56dp start inset below is
 * `ResourceCard.kt`'s own `padding(start = 56.dp, ...)`, reused exactly so the text column lines
 * up with the real row it precedes.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    hasLeadingIcon: Boolean = true,
    hasSupportingLine: Boolean = true,
    hasTrailing: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (hasSupportingLine) 72.dp else 56.dp)
            .padding(
                start = if (hasLeadingIcon) 16.dp else 56.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasLeadingIcon) {
            SkeletonBlock(modifier = Modifier.size(24.dp), shape = CircleShape)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp))
            if (hasSupportingLine) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp))
            }
        }
        if (hasTrailing) {
            SkeletonBlock(
                modifier = Modifier.width(40.dp).height(20.dp),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}
