package crucible.lens

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.api.ApiClient
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.data.preferences.PreferencesManager
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager

    private fun switchAppIcon(icon: String) {
        val packageManager = packageManager
        val lightAlias = ComponentName(this, "crucible.lens.MainActivityLight")
        val darkAlias = ComponentName(this, "crucible.lens.MainActivityDark")

        when (icon) {
            PreferencesManager.APP_ICON_LIGHT -> {
                packageManager.setComponentEnabledSetting(
                    lightAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    darkAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            PreferencesManager.APP_ICON_DARK -> {
                packageManager.setComponentEnabledSetting(
                    lightAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                packageManager.setComponentEnabledSetting(
                    darkAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

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
            val appIcon by preferencesManager.appIcon.collectAsState(
                initial = PreferencesManager.APP_ICON_LIGHT
            )
            val lastVisitedResource by preferencesManager.lastVisitedResource.collectAsState(
                initial = null
            )
            val lastVisitedResourceName by preferencesManager.lastVisitedResourceName.collectAsState(
                initial = null
            )
            val floatingScanButton by preferencesManager.floatingScanButton.collectAsState(
                initial = true
            )
            val pinnedProjects by preferencesManager.pinnedProjects.collectAsState(
                initial = emptySet()
            )
            val archivedProjects by preferencesManager.archivedProjects.collectAsState(
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
                    appIcon = appIcon,
                    darkTheme = darkTheme,
                    lastVisitedResource = lastVisitedResource,
                    lastVisitedResourceName = lastVisitedResourceName,
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
                    onAppIconSave = { icon ->
                        scope.launch {
                            preferencesManager.saveAppIcon(icon)
                            // Switch between icon aliases
                            switchAppIcon(icon)
                        }
                    },
                    onLastVisitedResourceSave = { uuid, name ->
                        scope.launch {
                            preferencesManager.saveLastVisitedResource(uuid, name)
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
                    },
                    archivedProjects = archivedProjects,
                    onToggleArchive = { id ->
                        scope.launch {
                            preferencesManager.toggleArchivedProject(id)
                        }
                    }
                )
            }
        }
    }
}
