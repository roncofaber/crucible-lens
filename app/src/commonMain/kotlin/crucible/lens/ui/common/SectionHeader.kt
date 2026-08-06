package crucible.lens.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row

/**
 * Section header for a list — a resource group, or a collapsed "Hidden" section. Icon + title +
 * count badge, optionally with a chevron, above a divider marking the section boundary.
 *
 * Ranked above its rows by **role and container**: a **title** role (`titleMedium`, 16sp Medium) against
 * the rows' **body** role (`bodyMedium`, 14sp), on a `surfaceContainer` container against their
 * plain `surface`. Both draw their text `onSurface` and neither overrides `fontWeight` — the
 * accent lives in the icon, container colour, and count badge, not the title, per M3's rule that
 * `primary` text is for hyperlinks.
 *
 * `surfaceContainer` is the right role here because M3 expresses elevation as tonal colour rather
 * than shadow, and this header pins via `stickyHeader`, so it needs to read as chrome sitting
 * above the list. That only works because every accent in `ui/theme/accents/` is a fully
 * hand-curated static `ColorScheme` with every role assigned; before that, unset roles fell
 * through to M3's purple-seeded baseline and looked foreign in every palette but purple.
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 24dp, matching the rows' leading icons rather than undercutting them at 18dp.
                AppIcon(icon, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                // onSurface, not primary: M3 puts text on onSurface (or onSurfaceVariant) and
                // reserves primary for hyperlinks. The accent still reads here — it's in the
                // container, the icon and the count badge — without tinting the text itself,
                // which also keeps contrast safe on the lighter palettes.
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (onToggle != null) {
                ExpandChevron(expanded = expanded, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
