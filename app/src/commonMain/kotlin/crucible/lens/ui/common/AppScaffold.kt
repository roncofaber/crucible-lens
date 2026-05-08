package crucible.lens.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * Drop-in replacement for Scaffold that automatically shows the offline banner
 * below the top bar, pushing content down. Screens use this identically to
 * Scaffold — the only difference is they get top=0 in their PaddingValues
 * since AppScaffold handles the top inset via an internal Spacer.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = contentColor
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding()))
            OfflineBanner()
            Box(modifier = Modifier.weight(1f)) {
                content(
                    PaddingValues(
                        top = androidx.compose.ui.unit.Dp.Hairline,
                        bottom = paddingValues.calculateBottomPadding(),
                        start = paddingValues.calculateStartPadding(layoutDirection),
                        end = paddingValues.calculateEndPadding(layoutDirection)
                    )
                )
            }
        }
    }
}
