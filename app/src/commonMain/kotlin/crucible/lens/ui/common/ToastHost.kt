package crucible.lens.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.platform.ToastBus
import kotlinx.coroutines.delay

/**
 * Renders transient messages posted to [ToastBus] as a bottom banner.
 * Used on platforms with no native toast/snackbar equivalent (currently iOS only —
 * Android keeps its native Toast via the showToast actual).
 */
@Composable
fun BoxScope.ToastHost() {
    var currentMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ToastBus.messages.collect { message ->
            currentMessage = message
        }
    }

    LaunchedEffect(currentMessage) {
        if (currentMessage != null) {
            delay(2000)
            currentMessage = null
        }
    }

    AnimatedVisibility(
        visible = currentMessage != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = currentMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }
    }
}
