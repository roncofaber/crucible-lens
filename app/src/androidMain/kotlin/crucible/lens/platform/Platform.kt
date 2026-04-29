package crucible.lens.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual typealias PlatformContext = Context

@Composable
actual fun getPlatformContext(): PlatformContext = LocalContext.current

@Composable
actual fun supportsDynamicColor(): Boolean = android.os.Build.VERSION.SDK_INT >= 31
