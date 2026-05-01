package crucible.lens.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

expect abstract class PlatformContext

/** True in debug builds, false in release. Used to gate verbose logging. */
expect val isDebugBuild: Boolean

@Composable
expect fun getPlatformContext(): PlatformContext

@Composable
expect fun supportsDynamicColor(): Boolean

@Composable
expect fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme?
