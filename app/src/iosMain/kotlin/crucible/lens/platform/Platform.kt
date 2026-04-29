package crucible.lens.platform

import androidx.compose.runtime.Composable

actual class PlatformContext

@Composable
actual fun getPlatformContext(): PlatformContext = PlatformContext()

@Composable
actual fun supportsDynamicColor(): Boolean = false // Dynamic color is Android 12+ only
