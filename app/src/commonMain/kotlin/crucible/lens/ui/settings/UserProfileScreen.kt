@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.User
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.UserAvatar

@Composable
fun UserProfileScreen(
    identifier: String,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    val platformCtx = getPlatformContext()
    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(identifier) {
        isLoading = true
        error = null
        val isOrcid = identifier.contains("-") && identifier.length > 10
        val result: ApiResult<User> = if (isOrcid) {
            when (val r = ApiClient.service.resolveUsers(orcids = listOf(identifier))) {
                is ApiResult.Success -> r.data[identifier]?.let { ApiResult.Success(it) }
                    ?: ApiResult.Error(404, "User not found")
                is ApiResult.Error -> r
            }
        } else {
            ApiClient.service.getUserByUsername(identifier)
        }
        when (result) {
            is ApiResult.Success -> user = result.data
            is ApiResult.Error -> error = result.message
        }
        isLoading = false
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = user?.username?.let { "@$it" } ?: "User Profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingContent(title = "Loading profile")
                error != null -> ErrorCard(
                    title = "Could not load profile",
                    message = error ?: "Unknown error",
                    modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
                )
                user != null -> {
                    val u = user!!
                    val displayName = listOfNotNull(u.firstName, u.lastName)
                        .joinToString(" ").ifBlank { null }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column {
                            // ── Header: avatar + name + username ──────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    firstName = u.firstName,
                                    lastName = u.lastName,
                                    size = 48.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (displayName != null) {
                                        Text(
                                            displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (u.username != null) {
                                        Text(
                                            "@${u.username}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // ── ORCID row ─────────────────────────────────────
                            if (u.uniqueId != null) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            openUrl(platformCtx, "https://orcid.org/${u.uniqueId}")
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        AppIcons.Orcid,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            "ORCID",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            u.uniqueId,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    AppIcon(
                                        AppIcons.OpenExternal,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
