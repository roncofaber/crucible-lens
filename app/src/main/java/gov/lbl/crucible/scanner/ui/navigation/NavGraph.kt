package gov.lbl.crucible.scanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import gov.lbl.crucible.scanner.ui.home.HomeScreen
import gov.lbl.crucible.scanner.ui.scanner.QRScannerScreen
import gov.lbl.crucible.scanner.ui.settings.SettingsScreen
import gov.lbl.crucible.scanner.ui.viewmodel.ScannerViewModel
import gov.lbl.crucible.scanner.ui.viewmodel.UiState
import gov.lbl.crucible.scanner.ui.detail.ResourceDetailScreen
import gov.lbl.crucible.scanner.ui.projects.ProjectsListScreen
import gov.lbl.crucible.scanner.ui.projects.ProjectDetailScreen
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Settings : Screen("settings")
    object Projects : Screen("projects")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object Detail : Screen("detail/{mfid}") {
        fun createRoute(mfid: String) = "detail/$mfid"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    apiKey: String?,
    apiBaseUrl: String,
    graphExplorerUrl: String,
    themeMode: String,
    accentColor: String,
    darkTheme: Boolean,
    onApiKeySave: (String) -> Unit,
    onApiBaseUrlSave: (String) -> Unit,
    onGraphExplorerUrlSave: (String) -> Unit,
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                graphExplorerUrl = graphExplorerUrl,
                isDarkTheme = darkTheme,
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
                }
            )
        }

        composable(Screen.Scanner.route) {
            QRScannerScreen(
                onQRCodeScanned = { code ->
                    navController.navigate(Screen.Detail.createRoute(code))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                currentApiKey = apiKey,
                currentApiBaseUrl = apiBaseUrl,
                currentGraphExplorerUrl = graphExplorerUrl,
                currentThemeMode = themeMode,
                currentAccentColor = accentColor,
                onApiKeySave = onApiKeySave,
                onApiBaseUrlSave = onApiBaseUrlSave,
                onGraphExplorerUrlSave = onGraphExplorerUrlSave,
                onThemeModeSave = onThemeModeSave,
                onAccentColorSave = onAccentColorSave,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("mfid") { type = NavType.StringType })
        ) { backStackEntry ->
            val mfid = backStackEntry.arguments?.getString("mfid") ?: ""

            LaunchedEffect(mfid) {
                viewModel.fetchResource(mfid)
            }

            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                                    Text(
                                        text = "Fetching data from Crucible...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    ResourceDetailScreen(
                        resource = state.resource,
                        thumbnails = state.thumbnails,
                        graphExplorerUrl = graphExplorerUrl,
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToResource = { newMfid ->
                            navController.navigate(Screen.Detail.createRoute(newMfid))
                        },
                        onHome = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        }
                    )
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
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
                is UiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        composable(Screen.Projects.route) {
            ProjectsListScreen(
                onBack = { navController.popBackStack() },
                onHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                }
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
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
                onResourceClick = { mfid ->
                    navController.navigate(Screen.Detail.createRoute(mfid))
                }
            )
        }
    }
}

@Composable
private fun ErrorHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
    )
}
