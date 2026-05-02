package crucible.lens.platform

import android.app.ActivityThread
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

actual typealias PlatformContext = Context

// BuildConfig.DEBUG is always false in library (com.android.library) modules.
// Read FLAG_DEBUGGABLE from the application info instead, which reflects the
// actual build type of the consuming androidApp module.
actual val isDebugBuild: Boolean = try {
    val flags = ActivityThread.currentApplication()?.applicationInfo?.flags ?: 0
    (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
} catch (_: Exception) { false }

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
