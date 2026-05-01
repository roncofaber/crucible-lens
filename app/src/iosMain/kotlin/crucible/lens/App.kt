package crucible.lens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.platform.getPlatformContext
import crucible.lens.data.network.ConnectivityObserver
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.IosAppPreferences
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme
import kotlinx.coroutines.launch

@Composable
actual fun App() {
    val prefs = remember { IosAppPreferences() }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val platformContext = getPlatformContext()

    ConnectivityObserver.init(Unit)

    val apiKey by prefs.apiKey.collectAsState(initial = null)
    val apiBaseUrl by prefs.apiBaseUrl.collectAsState(initial = AppPreferences.DEFAULT_API_BASE_URL)
    val graphExplorerUrl by prefs.graphExplorerUrl.collectAsState(initial = AppPreferences.DEFAULT_GRAPH_EXPLORER_URL)
    val themeMode by prefs.themeMode.collectAsState(initial = AppPreferences.THEME_MODE_SYSTEM)
    val accentColor by prefs.accentColor.collectAsState(initial = AppPreferences.DEFAULT_ACCENT_COLOR)
    val appIcon by prefs.appIcon.collectAsState(initial = AppPreferences.APP_ICON_LIGHT)
    val lastVisitedResource by prefs.lastVisitedResource.collectAsState(initial = null)
    val lastVisitedResourceName by prefs.lastVisitedResourceName.collectAsState(initial = null)
    val floatingScanButton by prefs.floatingScanButton.collectAsState(initial = true)
    val pinnedProjects by prefs.pinnedProjects.collectAsState(initial = emptySet())
    val hiddenProjects by prefs.hiddenProjects.collectAsState(initial = emptySet())
    val pinnedInstruments by prefs.pinnedInstruments.collectAsState(initial = emptySet())
    val hiddenInstruments by prefs.hiddenInstruments.collectAsState(initial = emptySet())
    val resourceHistory by prefs.resourceHistory.collectAsState(initial = emptyList())
    val userOrcid by prefs.userOrcid.collectAsState(initial = null)

    apiKey?.let { ApiClient.setApiKey(it) }
    ApiClient.setBaseUrl(apiBaseUrl)

    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppPreferences.THEME_MODE_DARK -> true
        AppPreferences.THEME_MODE_LIGHT -> false
        else -> systemInDarkTheme
    }

    CrucibleScannerTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        accentColor = accentColor
    ) {
        NavGraph(
            navController = navController,
            apiKey = apiKey,
            apiBaseUrl = apiBaseUrl,
            graphExplorerUrl = graphExplorerUrl,
            themeMode = themeMode,
            accentColor = accentColor,
            useDynamicColor = false,
            appIcon = appIcon,
            darkTheme = darkTheme,
            lastVisitedResource = lastVisitedResource,
            lastVisitedResourceName = lastVisitedResourceName,
            floatingScanButton = floatingScanButton,
            deepLinkUuid = null,
            openScanner = false,
            onScannerOpened = {},
            pinnedProjects = pinnedProjects,
            resourceHistory = resourceHistory,
            onHistoryAdd = { uuid, name ->
                scope.launch { prefs.addToHistory(uuid, name) }
            },
            onClearHistory = {
                scope.launch { prefs.clearHistory() }
            },
            onApiKeySave = { key ->
                scope.launch {
                    prefs.saveApiKey(key)
                    ApiClient.setApiKey(key)
                    CacheManager.clearAll()
                }
            },
            onApiBaseUrlSave = { url ->
                scope.launch {
                    prefs.saveApiBaseUrl(url)
                    ApiClient.setBaseUrl(url)
                    CacheManager.clearAll()
                    PersistentProjectCache.clear(platformContext)
                }
            },
            onGraphExplorerUrlSave = { url ->
                scope.launch { prefs.saveGraphExplorerUrl(url) }
            },
            onThemeModeSave = { mode ->
                scope.launch { prefs.saveThemeMode(mode) }
            },
            onAccentColorSave = { color ->
                scope.launch { prefs.saveAccentColor(color) }
            },
            onUseDynamicColorSave = { enabled ->
                scope.launch { prefs.saveUseDynamicColor(enabled) }
            },
            onAppIconSave = { /* no-op: dynamic app icons not supported on iOS */ },
            onLastVisitedResourceSave = { uuid, name ->
                scope.launch { prefs.saveLastVisitedResource(uuid, name) }
            },
            onFloatingScanButtonSave = { enabled ->
                scope.launch { prefs.saveFloatingScanButton(enabled) }
            },
            onTogglePinnedProject = { id ->
                scope.launch { prefs.togglePinnedProject(id) }
            },
            hiddenProjects = hiddenProjects,
            onToggleHideProject = { id ->
                scope.launch { prefs.toggleHiddenProject(id) }
            },
            pinnedInstruments = pinnedInstruments,
            onTogglePinnedInstrument = { id ->
                scope.launch { prefs.togglePinnedInstrument(id) }
            },
            hiddenInstruments = hiddenInstruments,
            onToggleHideInstrument = { id ->
                scope.launch { prefs.toggleHiddenInstrument(id) }
            },
            userOrcid = userOrcid,
            onUserOrcidSave = { orcid ->
                scope.launch { prefs.saveUserOrcid(orcid) }
            },
            onSignOut = {
                scope.launch {
                    prefs.clearApiKey()
                    prefs.saveUserOrcid(null)
                    CacheManager.clearAll()
                }
            }
        )
    }
}
