@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.model.Project
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.util.isSameUser
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.showToast
import crucible.lens.ui.common.AddOrAddedAction
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.IdText
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.SearchPickerSheet
import crucible.lens.ui.common.UserAvatar
import org.koin.compose.koinInject

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    identifier: String,
    onBack: () -> Unit,
    onHome: () -> Unit = {}
) {
    val platformCtx = getPlatformContext()
    val prefs = koinInject<AppPreferences>()
    val myProfile by prefs.userProfile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val myProjects by viewModel.myProjects.collectAsStateWithLifecycle()
    val addToProjectState by viewModel.addToProjectState.collectAsStateWithLifecycle()
    val membershipState by viewModel.membershipState.collectAsStateWithLifecycle()
    var showAddToProjectSheet by remember { mutableStateOf(false) }

    LaunchedEffect(identifier) { viewModel.load(identifier) }

    LaunchedEffect(addToProjectState) {
        val result = addToProjectState
        if (result is AddToProjectState.Added) {
            val name = result.project.title ?: result.project.projectId
            showToast(platformCtx, "Added to $name")
            viewModel.consumeAddToProjectResult()
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = (state as? UserProfileState.Loaded)?.user?.username?.let { "@$it" } ?: "User Profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UserProfileState.Loading -> LoadingContent(title = "Loading profile")
                is UserProfileState.Error -> ErrorCard(
                    title = "Could not load profile",
                    message = s.message,
                    modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
                )
                is UserProfileState.Loaded -> {
                    val u = s.user
                    val displayName = listOfNotNull(u.firstName, u.lastName)
                        .joinToString(" ").ifBlank { null }
                    // Adding yourself to a project is already covered by "Request to join" on
                    // ProjectDetailScreen - a second control here for your own profile would be
                    // a confusing, redundant path to the same outcome.
                    val isOwnProfile = isSameUser(myProfile, u)

                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(modifier = Modifier.fillMaxWidth()) {
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
                                        size = 48.dp,
                                        orcid = u.uniqueId
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (displayName != null) {
                                            Text(
                                                displayName,
                                                style = MaterialTheme.typography.titleMedium
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
                                                style = MaterialTheme.typography.bodySmall,
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

                        if (!isOwnProfile && u.username != null && myProjects.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.checkProjectMembership()
                                    showAddToProjectSheet = true
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                AppIcon(AppIcons.AddMember, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add to Project")
                            }
                        }
                    }

                    if (showAddToProjectSheet) {
                        AddToProjectSheet(
                            projects = myProjects,
                            membershipState = membershipState,
                            addToProjectState = addToProjectState,
                            onRetryMembership = { viewModel.retryProjectMembership() },
                            onAdd = { viewModel.addToProject(it) },
                            onDismiss = {
                                viewModel.consumeAddToProjectResult()
                                showAddToProjectSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToProjectSheet(
    projects: List<Project>,
    membershipState: ProjectMembershipState,
    addToProjectState: AddToProjectState,
    onRetryMembership: () -> Unit,
    onAdd: (Project) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(projects, query) {
        if (query.isBlank()) {
            projects
        } else {
            projects.filter { project ->
                project.title?.contains(query, ignoreCase = true) == true ||
                    project.projectId.contains(query, ignoreCase = true)
            }
        }
    }
    val snapshot = when (membershipState) {
        is ProjectMembershipState.Checking -> membershipState.previous
        is ProjectMembershipState.Ready -> membershipState.snapshot
        ProjectMembershipState.Idle -> null
    }
    val isCheckingMembership = membershipState is ProjectMembershipState.Checking
    val membershipFailureCount = snapshot?.failedProjectIds?.size ?: 0

    SearchPickerSheet(
        title = "Add to Project",
        query = query,
        onQueryChange = { query = it },
        isSearching = false,
        results = filtered,
        onDismiss = { if (addToProjectState !is AddToProjectState.Adding) onDismiss() },
        label = "Search your projects",
        key = { it.projectId },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isCheckingMembership) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Checking project membership",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isCheckingMembership && membershipFailureCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            membershipFailureMessage(membershipFailureCount),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRetryMembership) { Text("Retry") }
                    }
                }
                val addError = addToProjectState as? AddToProjectState.Error
                if (addError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            addError.message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { onAdd(addError.project) }) { Text("Retry") }
                    }
                }
            }
        },
        emptyContent = {
            Text(
                "No projects match \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        itemContent = { project ->
            val showId = project.title != null && project.title != project.projectId
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(AppIcons.Project, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            project.title ?: project.projectId,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showId) {
                            IdText(project.projectId)
                        }
                    }
                    AddOrAddedAction(
                        added = project.projectId in snapshot?.memberProjectIds.orEmpty(),
                        isAdding = (addToProjectState as? AddToProjectState.Adding)?.project?.projectId == project.projectId,
                        enabled = !isCheckingMembership &&
                            project.projectId in snapshot?.resolvedProjectIds.orEmpty() &&
                            addToProjectState !is AddToProjectState.Adding,
                        onAdd = { onAdd(project) }
                    )
                }
            }
        }
    )
}

internal fun membershipFailureMessage(count: Int): String = if (count == 1) {
    "Could not verify membership for 1 project"
} else {
    "Could not verify membership for $count projects"
}
