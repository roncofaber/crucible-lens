package crucible.lens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.network.ConnectivityObserver
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.PreferencesManager
import crucible.lens.di.initKoin
import crucible.lens.ui.navigation.DeepLinkTarget
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.navigation.parseDeepLink
import crucible.lens.ui.theme.CrucibleScannerTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var openScanner by mutableStateOf(false)
    private var deepLinkTarget by mutableStateOf<DeepLinkTarget?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "crucible.lens.OPEN_SCANNER") {
            deepLinkTarget = null
            openScanner = true
        } else {
            openScanner = false
            deepLinkTarget = intent.dataString?.let(::parseDeepLink)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        ConnectivityObserver.init(this)

        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            initKoin(platformModule = module { single<AppPreferences> { preferencesManager } })
        }

        // Keep splash visible until DataStore has emitted its first snapshot —
        // all StateFlows will have their real values by then, no flash possible.
        splashScreen.setKeepOnScreenCondition { !preferencesManager.isLoaded.value }

        deepLinkTarget = intent?.dataString?.let(::parseDeepLink)
        openScanner = intent?.action == "crucible.lens.OPEN_SCANNER"

        setContent {
            val navController = rememberNavController()
            // StateFlows always have their current value — no initial value needed
            val themeMode by preferencesManager.themeMode.collectAsState()
            val accentColor by preferencesManager.accentColor.collectAsState()
            val accentContrast by preferencesManager.accentContrast.collectAsState()
            val useDynamicColor by preferencesManager.useDynamicColor.collectAsState()
            val darkTheme = themeMode == PreferencesManager.THEME_MODE_DARK ||
                (themeMode == PreferencesManager.THEME_MODE_SYSTEM && isSystemInDarkTheme())

            // Targeting SDK 35+ means the OS enforces edge-to-edge unconditionally - content
            // already draws behind the status/navigation bars regardless of any opt-in here. What
            // isn't automatic is icon *contrast*: status/nav bar icons default to light (made for
            // a dark backdrop), so they vanish against this app's light theme unless explicitly
            // told to switch dark when the resolved theme is light. Tied to `darkTheme` (which
            // already folds in the in-app Light/Dark/System preference, not just the OS setting)
            // so toggling the in-app theme flips icon contrast immediately, not just a system-wide
            // dark mode change.
            val view = LocalView.current
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }

            CrucibleScannerTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                accentColor = accentColor,
                accentContrast = accentContrast
            ) {
                NavGraph(
                    navController = navController,
                    deepLinkTarget = deepLinkTarget,
                    onDeepLinkOpened = { deepLinkTarget = null },
                    openScanner = openScanner,
                    onScannerOpened = { openScanner = false }
                )
            }
        }
    }
}
