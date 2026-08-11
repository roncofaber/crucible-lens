package crucible.lens.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row

/**
 * Section header for a list — a resource group, or a collapsed "Hidden" section. Icon + title on
 * the leading edge, count badge and (optionally) a chevron trailing at the far right — the title
 * takes whatever space is left between them via `weight(1f)`, rather than the badge sitting
 * immediately after the title, so every header's badge/chevron cluster lines up regardless of how
 * long the title is.
 *
 * Ranked above its rows by **role and container**: a **title** role (`titleMedium`, 16sp Medium) against
 * the rows' **body** role (`bodyMedium`, 14sp). Both draw their text `onSurface` and neither
 * overrides `fontWeight`.
 *
 * **The container colour depends on [expanded], not a constant** — this is the one piece of state
 * that matters: a header only earns "chrome sitting above the list" treatment
 * (`surfaceContainerHigh`, one step above a resting card's `surfaceContainerLow`) while it's
 * actually pinning via `stickyHeader` above its own visible children, the same situation as a
 * scrolled top app bar. A *collapsed* header isn't separating anything from anything — it's just a
 * row — so it drops to plain `surface` (matching the page) with a leading `outlineVariant` divider
 * for row-to-row separation instead. Applying `surfaceContainerHigh` unconditionally is what
 * produced the original bug this fixes: group by type with everything collapsed rendered as a
 * solid wall of identically-tinted bars with no separation between them and an abrupt cutoff where
 * the list ended, rather than a normal, calm, scannable list of rows. The colour crossfades
 * (`animateColorAsState`) so expanding a header visibly "promotes" it instead of snapping.
 *
 * The icon and chevron are `onSurfaceVariant`, not `primary` — with dozens of these down a long
 * scroll, a repeated structural element is chrome, not the one or two things per screen an accent
 * should draw the eye to. The count badge stays a genuine `secondaryContainer`/`onSecondaryContainer`
 * pairing, matching every other count badge in the app, rather than `primaryContainer` — accent is
 * reserved for actual calls to action (the selected tab indicator, primary buttons), not repeated
 * information chrome.
 *
 * Pass [onToggle] only when the section can actually collapse. When it is null the chevron is not
 * drawn and the row is not clickable, because a control that renders as interactive and does
 * nothing is worse than no control — Search hit exactly that.
 */
@Composable
fun SectionHeader(
    title: String,
    count: Int,
    icon: AppIconToken,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null
) {
    // Same tuning as AppAnimations.kt's EffectsDefaultSpring - animateColorAsState needs an
    // AnimationSpec<Color>, so that Float-typed instance can't be reused directly, but the
    // spec itself (a no-bounce spring for opacity/colour transitions) is the same one.
    val colorSpec = spring<Color>(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
    val containerColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        animationSpec = colorSpec,
        label = "section_header_container"
    )
    // Always present, never conditionally added/removed - a hard on/off toggle would change the
    // header's total height by the divider's thickness right as it collapses/expands, shifting
    // the row's content by a couple of px. Crossfading its colour to transparent instead keeps
    // the reserved space constant, matching the "keep the slot present" principle used for
    // lazy-list item visibility elsewhere in the app.
    val dividerColor by animateColorAsState(
        targetValue = if (expanded) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = colorSpec,
        label = "section_header_divider"
    )
    Surface(
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // 24dp, matching the rows' leading icons rather than undercutting them at 18dp.
                    AppIcon(icon, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (onToggle != null) {
                    ExpandChevron(
                        expanded = expanded,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
