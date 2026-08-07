package crucible.lens.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow

/**
 * Shared style for machine-ID text (mfid, project/instrument ID) - `bodySmall`, monospace by
 * default, `onSurfaceVariant` since an ID is secondary to the name on a name-first row or card.
 * `onSurfaceVariant` only pairs safely with the `surface` family, though - a caller placing this on
 * a non-neutral container (e.g. a `secondaryContainer`-tinted header) must pass that container's
 * own `onX` role via [color] instead of leaving the default, or contrast isn't guaranteed. Purely
 * visual: callers attach their own click/copy behaviour (or none) via [modifier] - some IDs are
 * tap-to-copy themselves, some sit next to a separate copy button, some are inert and reachable
 * only via a row's long-press menu, so interaction isn't baked in here.
 */
@Composable
fun IdText(
    text: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}
