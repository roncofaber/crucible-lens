package crucible.lens.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual class PlatformContext

@Composable
actual fun getPlatformContext(): PlatformContext = PlatformContext()

@Composable
actual fun supportsDynamicColor(): Boolean = false

@Composable
actual fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
