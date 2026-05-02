package crucible.lens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.network.ConnectivityObserver
import crucible.lens.data.preferences.PreferencesManager
import crucible.lens.ui.navigation.NavGraph
import crucible.lens.ui.theme.CrucibleScannerTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var openScanner by mutableStateOf(false)
    private var initialThemeMode by mutableStateOf(PreferencesManager.THEME_MODE_SYSTEM)
    private var initialAccentColor by mutableStateOf(PreferencesManager.DEFAULT_ACCENT_COLOR)


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intents when activity is already running
        if (intent.action == "crucible.lens.OPEN_SCANNER") {
            openScanner = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        ConnectivityObserver.init(this)

        var preferencesReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !preferencesReady }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withTimeout(2_000L) {
                    initialThemeMode = preferencesManager.themeMode.first()
                    initialAccentColor = preferencesManager.accentColor.first()
                }
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) { preferencesReady = true }
        }

        val deepLinkUuid: String? = intent?.data?.pathSegments?.lastOrNull()?.takeIf { it.length > 8 }
        openScanner = intent?.action == "crucible.lens.OPEN_SCANNER"

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
                initial = initialThemeMode
            )
            val accentColor by preferencesManager.accentColor.collectAsState(
                initial = initialAccentColor
            )
            val useDynamicColor by preferencesManager.useDynamicColor.collectAsState(initial = false)
            val lastVisitedResource by preferencesManager.lastVisitedResource.collectAsState(
                initial = null
            )
            val lastVisitedResourceName by preferencesManager.lastVisitedResourceName.collectAsState(
                initial = null
            )
            val floatingScanButton by preferencesManager.floatingScanButton.collectAsState(
                initial = false
            )
            val pinnedProjects by preferencesManager.pinnedProjects.collectAsState(
                initial = emptySet()
            )
            val hiddenProjects by preferencesManager.hiddenProjects.collectAsState(
                initial = emptySet()
            )
            val resourceHistory by preferencesManager.resourceHistory.collectAsState(
                initial = emptyList()
            )
            val pinnedInstruments by preferencesManager.pinnedInstruments.collectAsState(initial = emptySet())
            val hiddenInstruments by preferencesManager.hiddenInstruments.collectAsState(initial = emptySet())
            val userOrcid by preferencesManager.userOrcid.collectAsState(initial = null)
            val aiApiKey by preferencesManager.aiApiKey.collectAsState(initial = null)
            val aiApiUrl by preferencesManager.aiApiUrl.collectAsState(
                initial = crucible.lens.data.preferences.AppPreferences.DEFAULT_AI_API_URL
            )
            val aiDirectMode by preferencesManager.aiDirectMode.collectAsState(initial = false)
            val scope = rememberCoroutineScope()

            // Set API key and base URL in client when they change
            apiKey?.let { key ->
                ApiClient.setApiKey(key)
            }
            ApiClient.setBaseUrl(apiBaseUrl)
            aiApiKey?.let { ApiClient.setAiApiKey(it) }
            ApiClient.setAiApiUrl(aiApiUrl)
            ApiClient.setAiDirectMode(aiDirectMode)

            // Determine dark theme based on theme mode
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                PreferencesManager.THEME_MODE_DARK -> true
                PreferencesManager.THEME_MODE_LIGHT -> false
                else -> systemInDarkTheme
            }

            CrucibleScannerTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamicColor,
                accentColor = accentColor
            ) {

                NavGraph(
                    navController = navController,
                    apiKey = apiKey,
                    apiBaseUrl = apiBaseUrl,
                    graphExplorerUrl = graphExplorerUrl,
                    themeMode = themeMode,
                    accentColor = accentColor,
                    useDynamicColor = useDynamicColor,
                    darkTheme = darkTheme,
                    lastVisitedResource = lastVisitedResource,
                    lastVisitedResourceName = lastVisitedResourceName,
                    floatingScanButton = floatingScanButton,
                    deepLinkUuid = deepLinkUuid,
                    openScanner = openScanner,
                    onScannerOpened = { openScanner = false },
                    pinnedProjects = pinnedProjects,
                    resourceHistory = resourceHistory,
                    onHistoryAdd = { uuid, name ->
                        scope.launch { preferencesManager.addToHistory(uuid, name) }
                    },
                    onClearHistory = {
                        scope.launch { preferencesManager.clearHistory() }
                    },
                    onApiKeySave = { key ->
                        scope.launch {
                            preferencesManager.saveApiKey(key)
                            ApiClient.setApiKey(key)
                            CacheManager.clearAll()
                        }
                    },
                    onApiBaseUrlSave = { url ->
                        scope.launch {
                            preferencesManager.saveApiBaseUrl(url)
                            ApiClient.setBaseUrl(url)
                            CacheManager.clearAll()
                            PersistentProjectCache.clear(this@MainActivity)
                        }
                    },
                    onGraphExplorerUrlSave = { url ->
                        scope.launch {
                            preferencesManager.saveGraphExplorerUrl(url)
                        }
                    },
                    aiApiKey = aiApiKey,
                    aiApiUrl = aiApiUrl,
                    aiDirectMode = aiDirectMode,
                    onAiApiKeySave = { key ->
                        scope.launch {
                            preferencesManager.saveAiApiKey(key)
                            ApiClient.setAiApiKey(key)
                        }
                    },
                    onAiApiUrlSave = { url ->
                        scope.launch {
                            preferencesManager.saveAiApiUrl(url)
                            ApiClient.setAiApiUrl(url)
                        }
                    },
                    onAiDirectModeSave = { enabled ->
                        scope.launch {
                            preferencesManager.saveAiDirectMode(enabled)
                            ApiClient.setAiDirectMode(enabled)
                        }
                    },
                    onThemeModeSave = { mode ->
                        scope.launch {
                            preferencesManager.saveThemeMode(mode)
                        }
                    },
                    onAccentColorSave = { color ->
                        scope.launch { preferencesManager.saveAccentColor(color) }
                    },
                    onUseDynamicColorSave = { enabled ->
                        scope.launch { preferencesManager.saveUseDynamicColor(enabled) }
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
                    hiddenProjects = hiddenProjects,
                    onToggleHideProject = { id ->
                        scope.launch {
                            preferencesManager.toggleHiddenProject(id)
                        }
                    },
                    pinnedInstruments = pinnedInstruments,
                    onTogglePinnedInstrument = { id ->
                        scope.launch { preferencesManager.togglePinnedInstrument(id) }
                    },
                    hiddenInstruments = hiddenInstruments,
                    onToggleHideInstrument = { id ->
                        scope.launch { preferencesManager.toggleHiddenInstrument(id) }
                    },
                    userOrcid = userOrcid,
                    onUserOrcidSave = { orcid ->
                        scope.launch { preferencesManager.saveUserOrcid(orcid) }
                    },
                    onSignOut = {
                        scope.launch {
                            preferencesManager.clearApiKey()
                            preferencesManager.saveUserOrcid(null)
                        }
                    }
                )
            }
        }
    }
}
