package crucible.lens.platform

import androidx.compose.runtime.Composable

expect abstract class PlatformContext

@Composable
expect fun getPlatformContext(): PlatformContext

@Composable
expect fun supportsDynamicColor(): Boolean
