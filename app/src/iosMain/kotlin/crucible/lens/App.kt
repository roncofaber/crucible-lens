package crucible.lens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.network.ConnectivityObserver
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.IosAppPreferences
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme

@Composable
actual fun App() {
    val prefs = remember { IosAppPreferences() }
    val navController = rememberNavController()

    ConnectivityObserver.init(Unit)

    // Only collect what's needed for theming — NavGraph collects everything else
    val themeMode by prefs.themeMode.collectAsState(initial = AppPreferences.THEME_MODE_SYSTEM)
    val accentColor by prefs.accentColor.collectAsState(initial = AppPreferences.DEFAULT_ACCENT_COLOR)
    val darkTheme = themeMode == AppPreferences.THEME_MODE_DARK ||
        (themeMode == AppPreferences.THEME_MODE_SYSTEM && isSystemInDarkTheme())

    CrucibleScannerTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        accentColor = accentColor
    ) {
        NavGraph(
            navController = navController,
            prefs = prefs,
            deepLinkUuid = null
        )
    }
}
