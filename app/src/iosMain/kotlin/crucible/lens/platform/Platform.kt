package crucible.lens.platform

import androidx.compose.runtime.Composable

actual class PlatformContext

@Composable
actual fun getPlatformContext(): PlatformContext = PlatformContext()
