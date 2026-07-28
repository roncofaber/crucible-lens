package crucible.lens.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * True center feels low when this content only fills the leftover area below a tall fixed
 * header (e.g. ProjectHeader + TabRow) — the leftover box's center sits well below the
 * screen's visual center. Biasing upward within that leftover area compensates.
 */
val UpperCenterAlignment: Alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.4f)

/**
 * Consistent loading indicator used across all screens.
 * Shows a spinner, a title, and a rotating loading message.
 * The [modifier] controls how the outer Box is sized/positioned.
 * [contentAlignment] defaults to true center; pass [UpperCenterAlignment] when this content
 * fills the area left over below a tall fixed header, so it doesn't read as anchored low.
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
                    fadeIn(animationSpec = tween(500)) togetherWith
                        fadeOut(animationSpec = tween(500))
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
