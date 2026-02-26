package gov.lbl.crucible.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.preferences.HistoryItem
import gov.lbl.crucible.scanner.data.preferences.PreferencesManager
import gov.lbl.crucible.scanner.ui.navigation.NavGraph
import gov.lbl.crucible.scanner.ui.theme.CrucibleScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)

        val deepLinkUuid: String? = intent?.data?.pathSegments?.lastOrNull()?.takeIf { it.length > 8 }

        setContent {
            val navController = rememberNavController()
            val apiKey by preferencesManager.apiKey.collectAsState(initial = null)
            val apiBaseUrl by preferencesManager.apiBaseUrl.collectAsState(
                initial = PreferencesManager.DEFAULT_API_BASE_URL
            )
            val graphExplorerUrl by preferencesManager.graphExplorerUrl.collectAsState(
                initial = PreferencesManager.DEFAULT_GRAPH_EXPLORER_URL
            )
            val themeMode by preferencesManager.themeMode.collectAsState(
                initial = PreferencesManager.THEME_MODE_SYSTEM
            )
            val accentColor by preferencesManager.accentColor.collectAsState(
                initial = PreferencesManager.DEFAULT_ACCENT_COLOR
            )
            val lastVisitedResource by preferencesManager.lastVisitedResource.collectAsState(
                initial = null
            )
            val lastVisitedResourceName by preferencesManager.lastVisitedResourceName.collectAsState(
                initial = null
            )
            val smoothAnimations by preferencesManager.smoothAnimations.collectAsState(
                initial = true
            )
            val floatingScanButton by preferencesManager.floatingScanButton.collectAsState(
                initial = true
            )
            val pinnedProjects by preferencesManager.pinnedProjects.collectAsState(
                initial = emptySet()
            )
            val resourceHistory by preferencesManager.resourceHistory.collectAsState(
                initial = emptyList()
            )
            val scope = rememberCoroutineScope()

            // Set API key and base URL in client when they change
            apiKey?.let { key ->
                ApiClient.setApiKey(key)
            }
            ApiClient.setBaseUrl(apiBaseUrl)

            // Determine dark theme based on theme mode
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                PreferencesManager.THEME_MODE_DARK -> true
                PreferencesManager.THEME_MODE_LIGHT -> false
                else -> systemInDarkTheme
            }

            CrucibleScannerTheme(
                darkTheme = darkTheme,
                dynamicColor = false, // Disable dynamic colors to use custom accent colors
                accentColor = accentColor
            ) {

                NavGraph(
                    navController = navController,
                    apiKey = apiKey,
                    apiBaseUrl = apiBaseUrl,
                    graphExplorerUrl = graphExplorerUrl,
                    themeMode = themeMode,
                    accentColor = accentColor,
                    darkTheme = darkTheme,
                    lastVisitedResource = lastVisitedResource,
                    lastVisitedResourceName = lastVisitedResourceName,
                    smoothAnimations = smoothAnimations,
                    floatingScanButton = floatingScanButton,
                    deepLinkUuid = deepLinkUuid,
                    pinnedProjects = pinnedProjects,
                    resourceHistory = resourceHistory,
                    onHistoryAdd = { uuid, name ->
                        scope.launch {
                            preferencesManager.addToHistory(uuid, name)
                        }
                    },
                    onApiKeySave = { key ->
                        scope.launch {
                            preferencesManager.saveApiKey(key)
                            ApiClient.setApiKey(key)
                        }
                    },
                    onApiBaseUrlSave = { url ->
                        scope.launch {
                            preferencesManager.saveApiBaseUrl(url)
                            ApiClient.setBaseUrl(url)
                        }
                    },
                    onGraphExplorerUrlSave = { url ->
                        scope.launch {
                            preferencesManager.saveGraphExplorerUrl(url)
                        }
                    },
                    onThemeModeSave = { mode ->
                        scope.launch {
                            preferencesManager.saveThemeMode(mode)
                        }
                    },
                    onAccentColorSave = { color ->
                        scope.launch {
                            preferencesManager.saveAccentColor(color)
                        }
                    },
                    onLastVisitedResourceSave = { uuid, name ->
                        scope.launch {
                            preferencesManager.saveLastVisitedResource(uuid, name)
                        }
                    },
                    onSmoothAnimationsSave = { enabled ->
                        scope.launch {
                            preferencesManager.saveSmoothAnimations(enabled)
                        }
                    },
                    onFloatingScanButtonSave = { enabled ->
                        scope.launch {
                            preferencesManager.saveFloatingScanButton(enabled)
                        }
                    },
                    onTogglePinnedProject = { id ->
                        scope.launch {
                            preferencesManager.togglePinnedProject(id)
                        }
                    }
                )
            }
        }
    }
}
