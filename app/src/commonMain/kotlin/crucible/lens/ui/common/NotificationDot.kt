package crucible.lens.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single place to change the look of every "something needs your attention" indicator in the
 * app (e.g. a project lead's pending join request count) — change it here once instead of
 * hunting down every call site. Not for always-visible numeric badges (see SearchScreen's
 * filter-count Badge), which are a different pattern (a count that's always shown, not an
 * attention flag that's only shown above zero).
 *
 * [count]: null or <= 0 hides the badge entirely; otherwise shows the number, sized down from
 * M3's default (16.dp) to 12.dp since a bare digit doesn't need the default's padding.
 */
@Composable
fun NotificationDot(
    count: Int?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BadgedBox(
        badge = {
            if (count != null && count > 0) {
                Badge(modifier = Modifier.size(12.dp)) {
                    Text(count.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        modifier = modifier,
        content = content
    )
}
