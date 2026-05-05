package crucible.lens.ui.navigation
import crucible.lens.platform.*

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import kotlinx.coroutines.launch
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.ui.home.HomeScreen
import crucible.lens.ui.history.HistoryScreen
import crucible.lens.ui.scanner.QRCodeScannerView
import crucible.lens.ui.search.SearchScreen
import crucible.lens.ui.settings.SettingsScreen
import crucible.lens.ui.settings.ApiSettingsScreen
import crucible.lens.ui.settings.AiSettingsScreen
import crucible.lens.ui.settings.OrcidLoginScreen
import crucible.lens.ui.settings.AppearanceSettingsScreen
import crucible.lens.ui.settings.CacheSettingsScreen
import crucible.lens.ui.settings.AboutSettingsScreen
import crucible.lens.ui.viewmodel.ResourceDetailViewModel
import crucible.lens.ui.viewmodel.UiState
import crucible.lens.ui.detail.ResourceDetailScreen
import crucible.lens.ui.projects.ProjectsListScreen
import crucible.lens.ui.projects.ProjectDetailScreen
import crucible.lens.ui.instruments.InstrumentListScreen
import crucible.lens.ui.instruments.InstrumentDetailScreen
import crucible.lens.ui.common.LoadingContent
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.util.DuplicateHolder
import crucible.lens.ui.create.CreateSampleScreen
import crucible.lens.ui.create.CreateDatasetScreen
import crucible.lens.ui.create.AddFilesScreen
import crucible.lens.ui.metadata.MetadataEditorScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState


import kotlin.math.roundToInt

private fun encodeRouteSegment(s: String) =
    s.replace("%", "%25").replace("/", "%2F").replace("?", "%3F").replace("&", "%26").replace("=", "%3D").replace(" ", "%20")

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Settings : Screen("settings")
    object SettingsApi : Screen("settings/api")
    object SettingsAi : Screen("settings/ai")
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsCache : Screen("settings/cache")
    object SettingsAbout : Screen("settings/about")
    object Projects : Screen("projects")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object Detail : Screen("detail/{mfid}?groupBy={groupBy}") {
        fun createRoute(mfid: String, groupBy: String? = null) =
            "detail/${encodeRouteSegment(mfid)}?groupBy=${groupBy ?: ""}"
    }
    object History : Screen("history")
    object Search : Screen("search")
    object OrcidLogin : Screen("orcid-login")
    object CreateSample : Screen("create-sample?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId != null) "create-sample?projectId=${encodeRouteSegment(projectId)}" else "create-sample?projectId="
    }
    object CreateDataset : Screen("create-dataset?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId != null) "create-dataset?projectId=${encodeRouteSegment(projectId)}" else "create-dataset?projectId="
    }
    object Instruments : Screen("instruments")
    object InstrumentDetail : Screen("instrument/{instrumentId}") {
        fun createRoute(id: String) = "instrument/${encodeRouteSegment(id)}"
    }
    object MetadataEditor : Screen("metadata-editor")
    object AddFiles : Screen("add-files?datasetId={datasetId}") {
        fun createRoute(datasetId: String? = null) =
            if (datasetId != null) "add-files?datasetId=$datasetId" else "add-files?datasetId="
    }
}


@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    apiKey: String?,
    apiBaseUrl: String,
    graphExplorerUrl: String,
    themeMode: String,
    accentColor: String,
    useDynamicColor: Boolean = false,
    darkTheme: Boolean,
    lastVisitedResource: String?,
    lastVisitedResourceName: String?,
    floatingScanButton: Boolean,
    deepLinkUuid: String?,
    openScanner: Boolean = false,
    onScannerOpened: () -> Unit = {},
    pinnedProjects: Set<String>,
    resourceHistory: List<HistoryItem>,
    onHistoryAdd: (String, String) -> Unit,
    onClearHistory: () -> Unit = {},
    onApiKeySave: (String) -> Unit,
    onApiBaseUrlSave: (String) -> Unit,
    onGraphExplorerUrlSave: (String) -> Unit,
    aiApiKey: String? = null,
    aiApiUrl: String = crucible.lens.data.preferences.AppPreferences.DEFAULT_AI_API_URL,
    aiDirectMode: Boolean = false,
    onAiApiKeySave: (String) -> Unit = {},
    onAiApiUrlSave: (String) -> Unit = {},
    onAiDirectModeSave: (Boolean) -> Unit = {},
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    onUseDynamicColorSave: (Boolean) -> Unit = {},
    onLastVisitedResourceSave: (String, String) -> Unit,
    onFloatingScanButtonSave: (Boolean) -> Unit,
    onTogglePinnedProject: (String) -> Unit,
    hiddenProjects: Set<String>,
    onToggleHideProject: (String) -> Unit,
    pinnedInstruments: Set<String> = emptySet(),
    onTogglePinnedInstrument: (String) -> Unit = {},
    hiddenInstruments: Set<String> = emptySet(),
    onToggleHideInstrument: (String) -> Unit = {},
    userOrcid: String? = null,
    onUserOrcidSave: (String?) -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: ResourceDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ResourceDetailViewModel() as T
            }
        }
    )
) {
    val platformCtx = getPlatformContext()

    LaunchedEffect(deepLinkUuid) {
        if (!deepLinkUuid.isNullOrBlank()) {
            navController.navigate(Screen.Detail.createRoute(deepLinkUuid))
        }
    }

    // Kick off full background data sync whenever the API key becomes available,
    // so projects, samples, datasets and instruments are cached for instant search.
    LaunchedEffect(apiKey) {
        if (!apiKey.isNullOrBlank()) {
            viewModel.startBackgroundSync()
        }
    }

    LaunchedEffect(openScanner, apiKey) {
        if (openScanner && !apiKey.isNullOrBlank()) {
            navController.navigate(Screen.Scanner.route) {
                launchSingleTop = true
            }
            onScannerOpened()
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showFab = floatingScanButton &&
        currentRoute != null &&
        currentRoute != Screen.Home.route &&
        !currentRoute.startsWith("settings") &&
        currentRoute != Screen.Scanner.route

    val fabOffsetX = remember { Animatable(0f) }
    val fabOffsetY = remember { Animatable(0f) }
    var fabInitialized by remember { mutableStateOf(false) }
    val fabScope = rememberCoroutineScope()

    // Stable navigation lambdas — hoisted so NavGraph recompositions (e.g. due to isSyncing,
    // apiKey, etc.) don't create new lambda instances and force child screens to recompose.
    val navigateBack: () -> Unit = remember(navController) { { navController.popBackStack() } }
    val navigateHome = remember(navController) {
        {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val navigateSearch = remember(navController) { { navController.navigate(Screen.Search.route) } }
    val navigateSettings = remember(navController) { { navController.navigate(Screen.Settings.route) } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        val fabSizePx = with(density) { 56.dp.toPx() }
        val scrollButtonSizePx = with(density) { 42.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(screenWidthPx, screenHeightPx) {
            if (!fabInitialized) {
                // Position at bottom-right, above where scroll-to-top button appears
                fabOffsetX.snapTo(screenWidthPx - fabSizePx - marginPx)
                fabOffsetY.snapTo(screenHeightPx - marginPx - scrollButtonSizePx - gapPx - fabSizePx)
                fabInitialized = true
            }
        }
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        // UNIFIED TRANSITIONS - Simple and consistent
        // Forward: slide in from right
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
            slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(300))
        },
        // Forward: fade out current screen
        exitTransition = {
            fadeOut(animationSpec = tween(200))
        },
        // Back: fade in previous screen
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        // Back: slide out to right
        popExitTransition = {
            fadeOut(animationSpec = tween(200)) +
            slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(200))
        }
    ) {
        composable(
            route = Screen.Home.route,
            // EXCEPTION: Home button from detail/project should fade only (context jump)
            enterTransition = {
                val fromDetailOrProject = initialState.destination.route?.startsWith("detail/") == true ||
                                         initialState.destination.route?.startsWith("project") == true
                if (fromDetailOrProject) {
                    // Fade only - jumping contexts, not navigating linearly
                    fadeIn(animationSpec = tween(300))
                } else {
                    // Use default (slide from right)
                    null
                }
            },
            exitTransition = {
                val goingToDetailOrProject = targetState.destination.route?.startsWith("detail/") == true ||
                                            targetState.destination.route?.startsWith("project") == true
                if (goingToDetailOrProject) {
                    // Slide out left to coordinate with detail sliding in from right
                    fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(300)
                    )
                } else {
                    // Use default (fade)
                    null
                }
            }
        ) {
            HomeScreen(
                graphExplorerUrl = graphExplorerUrl,
                isDarkTheme = darkTheme,
                lastVisitedResource = lastVisitedResource,
                lastVisitedResourceName = lastVisitedResourceName,
                apiKey = apiKey,
                onScanClick = {
                    if (apiKey.isNullOrBlank()) {
                        navController.navigate(Screen.Settings.route)
                    } else {
                        viewModel.reset()
                        navController.navigate(Screen.Scanner.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onManualEntry = { uuid ->
                    if (apiKey.isNullOrBlank()) {
                        navController.navigate(Screen.Settings.route)
                    } else {
                        navController.navigate(Screen.Detail.createRoute(uuid))
                    }
                },
                onBrowseProjects = {
                    if (apiKey.isNullOrBlank()) {
                        navController.navigate(Screen.Settings.route)
                    } else {
                        navController.navigate(Screen.Projects.route)
                    }
                },
                onBrowseInstruments = {
                    if (apiKey.isNullOrBlank()) {
                        navController.navigate(Screen.Settings.route)
                    } else {
                        navController.navigate(Screen.Instruments.route)
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onHistory = {
                    navController.navigate(Screen.History.route)
                },
                onSearch = {
                    navController.navigate(Screen.Search.route)
                },
                pinnedProjects = pinnedProjects,
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                onTogglePinnedProject = onTogglePinnedProject,
                pinnedInstruments = pinnedInstruments,
                onInstrumentClick = { id ->
                    navController.navigate(Screen.InstrumentDetail.createRoute(id))
                },
                onTogglePinnedInstrument = onTogglePinnedInstrument,
                onCreateSample = {
                    navController.navigate(Screen.CreateSample.createRoute())
                },
                onCreateDataset = {
                    navController.navigate(Screen.CreateDataset.createRoute())
                },
                isSyncing = isSyncing
            )
        }

        composable(Screen.Scanner.route) {
            QRCodeScannerView(
                onCodeScanned = { code ->
                    val uuid = runCatching {
                        if (code.contains("://")) code.substringAfterLast('/').substringBefore('?').trim()
                        else code
                    }.getOrDefault(code).trim()
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onBack = navigateBack
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                currentApiKey = apiKey,
                onNavigateToApi = { navController.navigate(Screen.SettingsApi.route) },
                onNavigateToAi = { navController.navigate(Screen.SettingsAi.route) },
                onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                onNavigateToCache = { navController.navigate(Screen.SettingsCache.route) },
                onNavigateToAbout = { navController.navigate(Screen.SettingsAbout.route) },
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(Screen.SettingsApi.route) {
            ApiSettingsScreen(
                currentApiKey = apiKey,
                currentApiBaseUrl = apiBaseUrl,
                currentGraphExplorerUrl = graphExplorerUrl,
                onApiKeySave = onApiKeySave,
                onApiBaseUrlSave = onApiBaseUrlSave,
                onGraphExplorerUrlSave = onGraphExplorerUrlSave,
                onUserOrcidSave = onUserOrcidSave,
                onSignOut = onSignOut,
                onSignIn = { navController.navigate(Screen.OrcidLogin.route) },
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(Screen.SettingsAi.route) {
            AiSettingsScreen(
                currentAiDirectMode = aiDirectMode,
                currentAiApiKey = aiApiKey,
                currentAiApiUrl = aiApiUrl,
                onAiDirectModeSave = onAiDirectModeSave,
                onAiApiKeySave = onAiApiKeySave,
                onAiApiUrlSave = onAiApiUrlSave,
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(Screen.OrcidLogin.route) {
            OrcidLoginScreen(
                loginUrl = "${apiBaseUrl}user_apikey",
                onBack = navigateBack,
                onKeyFound = { key ->
                    onApiKeySave(key)
                    showToast(platformCtx, "API key saved")
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.SettingsAppearance.route) {
            AppearanceSettingsScreen(
                currentThemeMode = themeMode,
                currentAccentColor = accentColor,
                currentFloatingScanButton = floatingScanButton,
                currentUseDynamicColor = useDynamicColor,
                onThemeModeSave = onThemeModeSave,
                onAccentColorSave = onAccentColorSave,
                onUseDynamicColorSave = onUseDynamicColorSave,
                onFloatingScanButtonSave = onFloatingScanButtonSave,
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(Screen.SettingsAbout.route) {
            AboutSettingsScreen(
                isDarkTheme = darkTheme,
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(Screen.SettingsCache.route) {
            CacheSettingsScreen(
                onBack = navigateBack,
                onHome = navigateHome
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("mfid") { type = NavType.StringType },
                navArgument("groupBy") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
            // Uses all default transitions - even sibling navigation
        ) { backStackEntry ->
            val mfid = backStackEntry.savedStateHandle.get<String>("mfid") ?: ""
            val siblingGroupBy = backStackEntry.savedStateHandle.get<String>("groupBy")?.takeIf { it.isNotBlank() }

            LaunchedEffect(mfid) {
                viewModel.fetchResource(mfid)
            }

            // Reset horizontal translation for ALL states to prevent diagonal animation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 0f
                    }
            ) {
                AnimatedContent(
                    targetState = uiState,
                    // All Success states share the same composable instance so that
                    // isRefreshing changes recompose (not recreate) ResourceDetailScreen.
                    // This preserves pullRefreshState so endRefresh() is called correctly.
                    contentKey = { state ->
                        when (state) {
                            is UiState.Idle    -> "idle"
                            is UiState.Loading -> "loading"
                            is UiState.Success -> "success"
                            is UiState.Error   -> "error"
                        }
                    },
                    transitionSpec = {
                        // All transitions fade smoothly to work with NavGraph slide
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                    label = "resource state"
                ) { state ->
                when (state) {
                    is UiState.Idle -> {
                        // Shown for the 1-2 frames before LaunchedEffect fires fetchResource.
                        // Plain background so there is no transparent/white flash on entry.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                    is UiState.Loading -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Loading...") },
                                    navigationIcon = {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            LoadingContent(
                                title = "Loading Resource",
                                modifier = Modifier.padding(padding)
                            )
                        }
                    }
                is UiState.Success -> {
                    // Save last visited resource and add to history
                    LaunchedEffect(state.resource) {
                        onLastVisitedResourceSave(state.resource.uniqueId, state.resource.name)
                        onHistoryAdd(state.resource.uniqueId, state.resource.name)
                    }

                    ResourceDetailScreen(
                        resource = state.resource,
                        thumbnails = state.thumbnails,
                        isRefreshing = state.isRefreshing,
                        graphExplorerUrl = graphExplorerUrl,
                        siblingGroupBy = siblingGroupBy,
                        onSaveToHistory = { uuid, name ->
                            onLastVisitedResourceSave(uuid, name)
                            onHistoryAdd(uuid, name)
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToResource = { newMfid ->
                            navController.navigate(Screen.Detail.createRoute(newMfid))
                        },
                        onNavigateToProject = { projectId ->
                            navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                        },
                        onNavigateToInstrument = { instrumentId ->
                            navController.navigate(Screen.InstrumentDetail.createRoute(instrumentId))
                        },
                        onSearch = navigateSearch,
                        onHome = navigateHome,
                        onRefresh = { uuid ->
                            viewModel.refreshResource(uuid)
                        },
                        getCardState = { key -> viewModel.getCardState(state.resource.uniqueId, key) },
                        onCardStateChange = { key, value -> viewModel.setCardState(state.resource.uniqueId, key, value) },
                        loadedResources = viewModel.loadedResources,
                        enrichedUuids = viewModel.enrichedUuids,
                        failedEnrichmentUuids = viewModel.failedEnrichmentUuids,
                        loadedThumbnails = viewModel.loadedThumbnails,
                        onSeedThumbnails = viewModel::seedThumbnails,
                        onNavigateToAddFiles = { datasetUuid ->
                            navController.navigate(Screen.AddFiles.createRoute(datasetUuid))
                        },
                        recentHistory = resourceHistory,
                        onDuplicate = { resource ->
                            when (resource) {
                                is Sample -> {
                                    DuplicateHolder.putSample(DuplicateHolder.SamplePrefill(
                                        name = resource.name,
                                        type = resource.sampleType,
                                        description = resource.description,
                                        timestamp = resource.timestamp,
                                        projectId = resource.projectId
                                    ))
                                    navController.navigate(Screen.CreateSample.createRoute())
                                }
                                is Dataset -> {
                                    DuplicateHolder.putDataset(DuplicateHolder.DatasetPrefill(
                                        name = resource.name,
                                        measurement = resource.measurement,
                                        instrumentName = resource.instrumentName,
                                        dataFormat = resource.dataFormat,
                                        sessionName = resource.sessionName,
                                        timestamp = resource.timestamp,
                                        projectId = resource.projectId
                                    ))
                                    navController.navigate(Screen.CreateDataset.createRoute())
                                }
                            }
                        }
                    )
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(0.9f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Unable to Load Resource",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                                )

                                Text(
                                    text = "Possible causes:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ErrorHint("• Invalid or incorrect MFID")
                                    ErrorHint("• Network connection issues")
                                    ErrorHint("• API key not configured")
                                    ErrorHint("• Resource not found in system")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { navController.popBackStack() }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Go Back")
                                    }
                                    Button(
                                        onClick = { viewModel.fetchResource(mfid) }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end AnimatedContent
            } // end Box wrapper
        }

        composable(Screen.Projects.route) {
            ProjectsListScreen(
                onBack = navigateBack,
                onHome = navigateHome,
                onSearch = navigateSearch,
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                pinnedProjects = pinnedProjects,
                onTogglePin = onTogglePinnedProject,
                hiddenProjects = hiddenProjects,
                onToggleHide = onToggleHideProject
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            // Uses all default transitions
        ) { backStackEntry ->
            val projectId = backStackEntry.savedStateHandle.get<String>("projectId") ?: ""
            ProjectDetailScreen(
                projectId = projectId,
                graphExplorerUrl = graphExplorerUrl,
                onBack = navigateBack,
                onHome = navigateHome,
                onSearch = navigateSearch,
                onResourceClick = { mfid, groupBy ->
                    navController.navigate(Screen.Detail.createRoute(mfid, groupBy))
                },
                isPinned = projectId in pinnedProjects,
                onTogglePin = { onTogglePinnedProject(projectId) },
                isHidden = projectId in hiddenProjects,
                onCreateSample = {
                    navController.navigate(Screen.CreateSample.createRoute(projectId))
                },
                onCreateDataset = {
                    navController.navigate(Screen.CreateDataset.createRoute(projectId))
                }
            )
        }

        composable(Screen.Instruments.route) {
            InstrumentListScreen(
                onBack = navigateBack,
                onHome = navigateHome,
                onSearch = navigateSearch,
                onInstrumentClick = { id ->
                    navController.navigate(Screen.InstrumentDetail.createRoute(id))
                },
                pinnedInstruments = pinnedInstruments,
                onTogglePin = onTogglePinnedInstrument,
                hiddenInstruments = hiddenInstruments,
                onToggleHide = onToggleHideInstrument
            )
        }

        composable(
            route = Screen.InstrumentDetail.route,
            arguments = listOf(navArgument("instrumentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val instrumentId = backStackEntry.savedStateHandle.get<String>("instrumentId") ?: ""
            InstrumentDetailScreen(
                instrumentId = instrumentId,
                isPinned = instrumentId in pinnedInstruments,
                onTogglePin = { onTogglePinnedInstrument(instrumentId) },
                onBack = navigateBack,
                onHome = navigateHome,
                onSearch = navigateSearch,
                onDatasetClick = { mfid ->
                    navController.navigate(Screen.Detail.createRoute(mfid))
                }
            )
        }

        composable(
            route = Screen.CreateSample.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val projectId = backStackEntry.savedStateHandle.get<String>("projectId")?.ifBlank { null }
            CreateSampleScreen(
                initialProjectId = projectId,
                onBack = navigateBack,
                onCreated = { uuid ->
                    navController.popBackStack()
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onOpenMetadataEditor = { navController.navigate(Screen.MetadataEditor.route) }
            )
        }

        composable(
            route = Screen.CreateDataset.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val projectId = backStackEntry.savedStateHandle.get<String>("projectId")?.ifBlank { null }
            CreateDatasetScreen(
                initialProjectId = projectId,
                onBack = navigateBack,
                onCreated = { uuid ->
                    navController.popBackStack()
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onOpenMetadataEditor = { navController.navigate(Screen.MetadataEditor.route) },
                onOpenFilesScreen = { navController.navigate(Screen.AddFiles.createRoute()) }
            )
        }

        composable(
            route = Screen.AddFiles.route,
            arguments = listOf(navArgument("datasetId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val datasetId = backStackEntry.savedStateHandle.get<String>("datasetId")?.ifBlank { null }
            AddFilesScreen(
                onBack = navigateBack,
                onDone = navigateBack,
                datasetUuid = datasetId
            )
        }

        composable(Screen.MetadataEditor.route) {
            MetadataEditorScreen(
                onBack = navigateBack,
                onDone = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("metadata_updated", true)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                history = resourceHistory,
                onBack = navigateBack,
                onHome = navigateHome,
                onSearch = navigateSearch,
                onItemClick = { uuid ->
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onClearHistory = onClearHistory,
                graphExplorerUrl = graphExplorerUrl
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                apiKey = apiKey,
                userOrcid = userOrcid,
                onBack = navigateBack,
                onHome = navigateHome,
                onResourceClick = { uuid ->
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                }
            )
        }
    } // end NavHost

    if (showFab && fabInitialized) {
        FloatingActionButton(
            onClick = {
                if (!apiKey.isNullOrBlank()) {
                    viewModel.reset()
                    navController.navigate(Screen.Scanner.route) {
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(Screen.Settings.route)
                }
            },
            modifier = Modifier
                .offset { IntOffset(fabOffsetX.value.roundToInt(), fabOffsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    val fabSnapSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    fun snapToEdge() {
                        val distLeft   = fabOffsetX.value
                        val distRight  = screenWidthPx  - fabSizePx - fabOffsetX.value
                        val distTop    = fabOffsetY.value
                        val distBottom = screenHeightPx - fabSizePx - fabOffsetY.value
                        val xBounds = marginPx..(screenWidthPx  - fabSizePx - marginPx)
                        val yBounds = marginPx..(screenHeightPx - fabSizePx - marginPx)
                        if (minOf(distLeft, distRight) <= minOf(distTop, distBottom)) {
                            val targetX = if (distLeft <= distRight) marginPx else screenWidthPx - fabSizePx - marginPx
                            fabScope.launch { fabOffsetX.animateTo(targetX, animationSpec = fabSnapSpec) }
                            fabScope.launch { fabOffsetY.animateTo(fabOffsetY.value.coerceIn(yBounds), animationSpec = fabSnapSpec) }
                        } else {
                            val targetY = if (distTop <= distBottom) marginPx else screenHeightPx - fabSizePx - marginPx
                            fabScope.launch { fabOffsetY.animateTo(targetY, animationSpec = fabSnapSpec) }
                            fabScope.launch { fabOffsetX.animateTo(fabOffsetX.value.coerceIn(xBounds), animationSpec = fabSnapSpec) }
                        }
                    }
                    detectDragGestures(
                        onDragEnd = { snapToEdge() },
                        onDragCancel = { snapToEdge() }
                    ) { _, dragAmount ->
                        fabScope.launch {
                            fabOffsetX.snapTo((fabOffsetX.value + dragAmount.x).coerceIn(0f, screenWidthPx - fabSizePx))
                            fabOffsetY.snapTo((fabOffsetY.value + dragAmount.y).coerceIn(0f, screenHeightPx - fabSizePx))
                        }
                    }
                },
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = "Scan QR Code",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
    } // end BoxWithConstraints
}

@Composable
private fun ErrorHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
    )
}
