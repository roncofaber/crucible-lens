package crucible.lens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.network.ConnectivityObserver
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.PreferencesManager
import crucible.lens.di.initKoin
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var openScanner by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "crucible.lens.OPEN_SCANNER") openScanner = true
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

        val deepLinkUuid: String? = intent?.data?.pathSegments?.lastOrNull()?.takeIf { it.length > 8 }
        openScanner = intent?.action == "crucible.lens.OPEN_SCANNER"

        setContent {
            val navController = rememberNavController()
            // StateFlows always have their current value — no initial value needed
            val themeMode by preferencesManager.themeMode.collectAsState()
            val accentColor by preferencesManager.accentColor.collectAsState()
            val useDynamicColor by preferencesManager.useDynamicColor.collectAsState()
            val darkTheme = themeMode == PreferencesManager.THEME_MODE_DARK ||
                (themeMode == PreferencesManager.THEME_MODE_SYSTEM && isSystemInDarkTheme())

            CrucibleScannerTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                accentColor = accentColor
            ) {
                NavGraph(
                    navController = navController,
                    deepLinkUuid = deepLinkUuid,
                    openScanner = openScanner,
                    onScannerOpened = { openScanner = false }
                )
            }
        }
    }
}
