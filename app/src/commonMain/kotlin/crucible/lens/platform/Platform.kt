package crucible.lens.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

expect class PlatformContext

@Composable
expect fun getPlatformContext(): PlatformContext

@Composable
expect fun supportsDynamicColor(): Boolean

@Composable
expect fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme?
