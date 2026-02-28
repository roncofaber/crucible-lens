package crucible.lens.ui.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.ui.home.HomeScreen
import crucible.lens.ui.history.HistoryScreen
import crucible.lens.ui.scanner.QRScannerScreen
import crucible.lens.ui.search.SearchScreen
import crucible.lens.ui.settings.SettingsScreen
import crucible.lens.ui.settings.ApiSettingsScreen
import crucible.lens.ui.settings.AppearanceSettingsScreen
import crucible.lens.ui.settings.AboutSettingsScreen
import crucible.lens.ui.viewmodel.ScannerViewModel
import crucible.lens.ui.viewmodel.UiState
import crucible.lens.ui.detail.ResourceDetailScreen
import crucible.lens.ui.projects.ProjectsListScreen
import crucible.lens.ui.projects.ProjectDetailScreen
import crucible.lens.ui.common.LoadingMessage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import android.net.Uri
import kotlin.math.roundToInt

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Settings : Screen("settings")
    object SettingsApi : Screen("settings/api")
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsAbout : Screen("settings/about")
    object Projects : Screen("projects")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object Detail : Screen("detail/{mfid}") {
        fun createRoute(mfid: String) = "detail/${Uri.encode(mfid)}"
    }
    object History : Screen("history")
    object Search : Screen("search")
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    apiKey: String?,
    apiBaseUrl: String,
    graphExplorerUrl: String,
    themeMode: String,
    accentColor: String,
    appIcon: String,
    darkTheme: Boolean,
    lastVisitedResource: String?,
    lastVisitedResourceName: String?,
    floatingScanButton: Boolean,
    deepLinkUuid: String?,
    pinnedProjects: Set<String>,
    resourceHistory: List<HistoryItem>,
    onHistoryAdd: (String, String) -> Unit,
    onApiKeySave: (String) -> Unit,
    onApiBaseUrlSave: (String) -> Unit,
    onGraphExplorerUrlSave: (String) -> Unit,
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    onAppIconSave: (String) -> Unit,
    onLastVisitedResourceSave: (String, String) -> Unit,
    onFloatingScanButtonSave: (Boolean) -> Unit,
    onTogglePinnedProject: (String) -> Unit,
    archivedProjects: Set<String>,
    onToggleArchive: (String) -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    LaunchedEffect(deepLinkUuid) {
        if (!deepLinkUuid.isNullOrBlank()) {
            viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
            navController.navigate(Screen.Detail.createRoute(deepLinkUuid))
        }
    }
    val uiState by viewModel.uiState.collectAsState()

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        val fabSizePx = with(density) { 56.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(screenWidthPx, screenHeightPx) {
            if (!fabInitialized) {
                fabOffsetX.snapTo(screenWidthPx - fabSizePx - marginPx)
                fabOffsetY.snapTo(screenHeightPx - fabSizePx - marginPx)
                fabInitialized = true
            }
        }
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { it / 10 })
        },
        exitTransition = {
            fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it / 10 })
        }
    ) {
        composable(Screen.Home.route) {
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
                        navController.navigate(Screen.Scanner.route)
                    }
                },
                onManualEntry = { uuid ->
                    if (apiKey.isNullOrBlank()) {
                        navController.navigate(Screen.Settings.route)
                    } else {
                        viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
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
                }
            )
        }

        composable(Screen.Scanner.route) {
            QRScannerScreen(
                onQRCodeScanned = { code ->
                    // If the scanned code is a URL (e.g. a graph-explorer share link),
                    // extract the last path segment as the UUID. Otherwise use as-is.
                    val uuid = runCatching {
                        val parsed = Uri.parse(code)
                        if (parsed.scheme != null) parsed.lastPathSegment ?: code else code
                    }.getOrDefault(code).trim()
                    viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
                    navController.navigate(Screen.Detail.createRoute(uuid))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                currentApiKey = apiKey,
                onNavigateToApi = { navController.navigate(Screen.SettingsApi.route) },
                onNavigateToAppearance = { navController.navigate(Screen.SettingsAppearance.route) },
                onNavigateToAbout = { navController.navigate(Screen.SettingsAbout.route) },
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
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
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.SettingsAppearance.route) {
            AppearanceSettingsScreen(
                currentThemeMode = themeMode,
                currentAccentColor = accentColor,
                currentAppIcon = appIcon,
                currentFloatingScanButton = floatingScanButton,
                onThemeModeSave = onThemeModeSave,
                onAccentColorSave = onAccentColorSave,
                onAppIconSave = onAppIconSave,
                onFloatingScanButtonSave = onFloatingScanButtonSave,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(Screen.SettingsAbout.route) {
            AboutSettingsScreen(
                isDarkTheme = darkTheme,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("mfid") { type = NavType.StringType }),
            enterTransition = {
                val isSibling = initialState.destination.route?.startsWith("detail/") == true
                val fromProject = initialState.destination.route?.startsWith("project/") == true
                when {
                    isSibling -> EnterTransition.None
                    fromProject -> fadeIn(animationSpec = tween(300))
                    else -> fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { it / 10 })
                }
            },
            exitTransition = {
                val isSibling = targetState.destination.route?.startsWith("detail/") == true
                if (isSibling) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = tween(200))
                }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it / 10 })
            }
        ) { backStackEntry ->
            val mfid = backStackEntry.arguments?.getString("mfid") ?: ""

            LaunchedEffect(mfid) {
                viewModel.fetchResource(mfid)
                viewModel.prepareSiblingNav(0)  // reset direction after navigation starts
            }

            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    // Success→Success: sibling nav, NavGraph handles the visual.
                    // Idle→*: new composable just appeared, skip the crossfade so there
                    // is no fade-from-nothing that would reveal the background color.
                    val isSuccessToSuccess =
                        initialState is UiState.Success && targetState is UiState.Success
                    val isFromIdle = initialState is UiState.Idle
                    if (isSuccessToSuccess || isFromIdle) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
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
                    // Delay everything so fast swipes (cache hits) show nothing at all.
                    // The NavGraph BoxWithConstraints background fills the gap invisibly.
                    var showContent by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(400L)
                        showContent = true
                    }
                    val loadingMessage = LoadingMessage()

                    if (showContent) Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                                modifier = Modifier.padding(32.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 4.dp
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Loading Resource",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        AnimatedContent(
                                            targetState = loadingMessage,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(500)) togetherWith
                                                    fadeOut(animationSpec = tween(500))
                                            },
                                            label = "loading message"
                                        ) { message ->
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
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
                        mfid = mfid,
                        isRefreshing = state.isRefreshing,
                        graphExplorerUrl = graphExplorerUrl,
                        darkTheme = darkTheme,
                        siblingNavDirection = viewModel.siblingNavDirection,
                        onResetSiblingNavDirection = { viewModel.resetSiblingNavDirection() },
                        onSaveToHistory = { uuid, name ->
                            onLastVisitedResourceSave(uuid, name)
                            onHistoryAdd(uuid, name)
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToResource = { newMfid ->
                            viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
                            navController.navigate(Screen.Detail.createRoute(newMfid))
                        },
                        onNavigateToProject = { projectId ->
                            navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                        },
                        onNavigateToSibling = { siblingMfid, direction ->
                            viewModel.prepareSiblingNav(direction)
                            navController.navigate(Screen.Detail.createRoute(siblingMfid)) {
                                popUpTo(Screen.Detail.route) { inclusive = true }
                            }
                        },
                        onSearch = { navController.navigate(Screen.Search.route) },
                        onHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        },
                        onRefresh = {
                            viewModel.refreshResource(mfid)
                        },
                        getCardState = { key -> viewModel.getCardState(state.resource.uniqueId, key) },
                        onCardStateChange = { key, value -> viewModel.setCardState(state.resource.uniqueId, key, value) }
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
            }
        }

        composable(
            route = Screen.Projects.route,
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { it / 10 })
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it / 10 })
            }
        ) {
            ProjectsListScreen(
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                pinnedProjects = pinnedProjects,
                onTogglePin = onTogglePinnedProject,
                archivedProjects = archivedProjects,
                onToggleArchive = onToggleArchive
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { it / 10 })
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it / 10 })
            }
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProjectDetailScreen(
                projectId = projectId,
                graphExplorerUrl = graphExplorerUrl,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onSearch = { navController.navigate(Screen.Search.route) },
                onResourceClick = { mfid ->
                    viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
                    navController.navigate(Screen.Detail.createRoute(mfid))
                },
                isPinned = projectId in pinnedProjects,
                onTogglePin = { onTogglePinnedProject(projectId) }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                history = resourceHistory,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onItemClick = { uuid ->
                    viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
                    navController.navigate(Screen.Detail.createRoute(uuid))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                apiKey = apiKey,
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onResourceClick = { uuid ->
                    viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
                    navController.navigate(Screen.Detail.createRoute(uuid))
                },
                onProjectClick = { projectId ->
                    viewModel.resetSiblingNavDirection() // Reset before non-sibling navigation
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
                        navController.navigate(Screen.Scanner.route)
                    } else {
                        navController.navigate(Screen.Settings.route)
                    }
                },
                modifier = Modifier
                    .offset { IntOffset(fabOffsetX.value.roundToInt(), fabOffsetY.value.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val distLeft   = fabOffsetX.value
                                val distRight  = screenWidthPx  - fabSizePx - fabOffsetX.value
                                val distTop    = fabOffsetY.value
                                val distBottom = screenHeightPx - fabSizePx - fabOffsetY.value
                                val spec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                val xBounds = marginPx..(screenWidthPx  - fabSizePx - marginPx)
                                val yBounds = marginPx..(screenHeightPx - fabSizePx - marginPx)
                                if (minOf(distLeft, distRight) <= minOf(distTop, distBottom)) {
                                    val targetX = if (distLeft <= distRight) marginPx else screenWidthPx - fabSizePx - marginPx
                                    val targetY = fabOffsetY.value.coerceIn(yBounds)
                                    fabScope.launch { fabOffsetX.animateTo(targetX, animationSpec = spec) }
                                    fabScope.launch { fabOffsetY.animateTo(targetY, animationSpec = spec) }
                                } else {
                                    val targetY = if (distTop <= distBottom) marginPx else screenHeightPx - fabSizePx - marginPx
                                    val targetX = fabOffsetX.value.coerceIn(xBounds)
                                    fabScope.launch { fabOffsetY.animateTo(targetY, animationSpec = spec) }
                                    fabScope.launch { fabOffsetX.animateTo(targetX, animationSpec = spec) }
                                }
                            },
                            onDragCancel = {
                                val distLeft   = fabOffsetX.value
                                val distRight  = screenWidthPx  - fabSizePx - fabOffsetX.value
                                val distTop    = fabOffsetY.value
                                val distBottom = screenHeightPx - fabSizePx - fabOffsetY.value
                                val spec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                val xBounds = marginPx..(screenWidthPx  - fabSizePx - marginPx)
                                val yBounds = marginPx..(screenHeightPx - fabSizePx - marginPx)
                                if (minOf(distLeft, distRight) <= minOf(distTop, distBottom)) {
                                    val targetX = if (distLeft <= distRight) marginPx else screenWidthPx - fabSizePx - marginPx
                                    val targetY = fabOffsetY.value.coerceIn(yBounds)
                                    fabScope.launch { fabOffsetX.animateTo(targetX, animationSpec = spec) }
                                    fabScope.launch { fabOffsetY.animateTo(targetY, animationSpec = spec) }
                                } else {
                                    val targetY = if (distTop <= distBottom) marginPx else screenHeightPx - fabSizePx - marginPx
                                    val targetX = fabOffsetX.value.coerceIn(xBounds)
                                    fabScope.launch { fabOffsetY.animateTo(targetY, animationSpec = spec) }
                                    fabScope.launch { fabOffsetX.animateTo(targetX, animationSpec = spec) }
                                }
                            }
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
