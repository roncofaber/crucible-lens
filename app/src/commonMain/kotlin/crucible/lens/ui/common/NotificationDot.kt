package crucible.lens.ui.common

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Single place to change the look of every "something needs your attention" dot in the app
 * (e.g. a project lead's pending join requests) — change the dot here once instead of hunting
 * down every call site. Not for numeric badges (see SearchScreen's filter-count Badge), which
 * are a different, always-visible-with-content pattern.
 */
@Composable
fun NotificationDot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BadgedBox(
        badge = { if (visible) Badge() },
        modifier = modifier,
        content = content
    )
}
