@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.platform.displayVersionName
import crucible.lens.platform.isDebugBuild
import crucible.lens.ui.common.AppScaffold

@Composable
fun SettingsScreen(
    currentApiKey: String?,
    userUsername: String?,
    syncedCount: Int = 0,
    totalProjectCount: Int = 0,
    onNavigateToAccount: () -> Unit,
    onNavigateToApi: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToCache: () -> Unit,
    onNavigateToSyncedProjects: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToTypography: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsRow(
                icon = AppIcons.User,
                title = "Account",
                subtitle = when {
                    !currentApiKey.isNullOrBlank() && !userUsername.isNullOrBlank() -> "@$userUsername"
                    !currentApiKey.isNullOrBlank() -> "Signed in"
                    else -> "Not signed in"
                },
                onClick = onNavigateToAccount
            )
            SettingsRow(
                icon = AppIcons.ApiEndpoint,
                title = "API",
                subtitle = "Server endpoints, connection test",
                onClick = onNavigateToApi
            )
            SettingsRow(
                icon = AppIcons.Appearance,
                title = "Appearance",
                subtitle = "Theme, accent color, animations",
                onClick = onNavigateToAppearance
            )
            SettingsRow(
                icon = AppIcons.FileStorage,
                title = "Cache",
                subtitle = "Pre-loaded data for faster browsing",
                onClick = onNavigateToCache
            )
            SettingsRow(
                icon = AppIcons.Syncing,
                title = "Synced projects",
                subtitle = "$syncedCount of $totalProjectCount syncing",
                onClick = onNavigateToSyncedProjects
            )
            SettingsRow(
                icon = AppIcons.Info,
                title = "About",
                subtitle = "Crucible Lens ${displayVersionName()}",
                onClick = onNavigateToAbout
            )
            if (isDebugBuild) {
                SettingsRow(
                    icon = AppIcons.Description,
                    title = "Typography",
                    subtitle = "Debug - type scale reference",
                    onClick = onNavigateToTypography
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: AppIconToken,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(icon, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
                }
            }
            AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
