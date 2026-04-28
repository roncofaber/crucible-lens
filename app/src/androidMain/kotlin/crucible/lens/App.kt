package crucible.lens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.compose.rememberNavController
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme

/**
 * Android actual for App — wires up the full NavGraph with preferences.
 * Preferences are injected via the Android-specific PreferencesManager
 * (DataStore-backed). On iOS, this will use multiplatform-settings.
 *
 * This is a thin bridge — all navigation and UI logic lives in NavGraph
 * which is already shared Compose code.
 */
@Composable
actual fun App() {
    // On Android we still use MainActivity's setContent with full NavGraph.
    // This actual is provided so the expect compiles; MainActivity.kt
    // continues to call NavGraph directly for now during migration.
    val navController = rememberNavController()
    CrucibleScannerTheme {
        // Minimal fallback — MainActivity bypasses this and calls NavGraph directly
    }
}
