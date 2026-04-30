package crucible.lens.platform

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual typealias PlatformContext = Context

@Composable
actual fun getPlatformContext(): PlatformContext = LocalContext.current

@Composable
actual fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= 31

@Composable
actual fun resolveDynamicColorScheme(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < 31) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
