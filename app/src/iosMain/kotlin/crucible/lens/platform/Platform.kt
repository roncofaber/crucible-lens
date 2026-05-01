package crucible.lens.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual abstract class PlatformContext

private class IosPlatformContext : PlatformContext()

@Composable
actual fun getPlatformContext(): PlatformContext = IosPlatformContext()

@Composable
actual fun supportsDynamicColor(): Boolean = false

@Composable
actual fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
