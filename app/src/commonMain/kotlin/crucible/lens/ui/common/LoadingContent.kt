package crucible.lens.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Consistent loading indicator used across all screens.
 * Shows a spinner, a title, and a rotating loading message.
 * The [modifier] controls how the outer Box is sized/positioned.
 *
 * [contentAlignment] defaults to true center, which is right whenever this fills the whole
 * content area. Inside a `LazyColumn` an item can't see the viewport, so centre it against the
 * viewport explicitly with `Modifier.fillParentMaxHeight(...)` from `LazyItemScope` rather than
 * biasing the alignment — see ProjectsListScreen/InstrumentListScreen.
 */
@Composable
fun LoadingContent(
    title: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val loadingMessage = LoadingMessage()
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AnimatedContent(
                targetState = loadingMessage,
                transitionSpec = {
                    fadeIn(animationSpec = ContentCrossfadeSpec) togetherWith
                        fadeOut(animationSpec = ContentCrossfadeSpec)
                },
                label = "loading message"
            ) { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
