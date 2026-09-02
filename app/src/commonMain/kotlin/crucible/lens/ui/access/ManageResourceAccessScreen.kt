@file:OptIn(ExperimentalMaterial3Api::class)

package crucible.lens.ui.access

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.util.accessGrantMutationTarget
import crucible.lens.data.util.asGrantRole
import crucible.lens.data.util.displayLabel
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.ResolvedPicker
import crucible.lens.ui.common.RoleBadge
import crucible.lens.ui.common.RoleDropdownField
import crucible.lens.ui.common.SearchPickerField
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ManageResourceAccessScreen(
    resourceMfid: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: ManageResourceAccessViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val removeState by viewModel.removeState.collectAsStateWithLifecycle()

    LaunchedEffect(resourceMfid) { viewModel.init(resourceMfid) }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Manage Access",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            ManageResourceAccessState.Loading -> LoadingContent(
                title = "Loading access",
                modifier = Modifier.padding(padding)
            )
            is ManageResourceAccessState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                ErrorCard(message = current.message, onRetry = { viewModel.load(forceRefresh = true) })
            }
            is ManageResourceAccessState.Loaded -> PullToRefreshBox(
                isRefreshing = current.isRefreshing,
                onRefresh = { viewModel.load(forceRefresh = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                AccessContent(
                    state = current,
                    onPublicAccessChange = viewModel::setPublicAccess,
                    onAdd = viewModel::showAddAccess,
                    onEdit = viewModel::editGrant,
                    onRetryRefresh = { viewModel.load(forceRefresh = true) }
                )
            }
        }
    }

    addState?.let { current ->
        AddAccessDialog(
            state = current,
            allowedRoles = (state as? ManageResourceAccessState.Loaded)?.allowedRoles.orEmpty(),
            onTypeChange = viewModel::selectCandidateType,
            onQueryChange = viewModel::updateCandidateQuery,
            onRetrySearch = viewModel::retryCandidateSearch,
            onSelectCandidate = viewModel::selectCandidate,
            onRoleChange = viewModel::selectAddRole,
            onConfirm = viewModel::addAccess,
            onDismiss = viewModel::dismissAddAccess
        )
    }
    editState?.let { current ->
        EditAccessDialog(
            state = current,
            allowedRoles = (state as? ManageResourceAccessState.Loaded)?.allowedRoles.orEmpty(),
            onRoleChange = viewModel::selectEditRole,
            onRemove = viewModel::requestRemoveGrant,
            onConfirm = viewModel::saveEditedGrant,
            onDismiss = viewModel::dismissEditGrant
        )
    }
    removeState?.let { current ->
        ConfirmationDialog(
            title = "Remove access?",
            text = current.error ?: "${grantName(current.grant)} will no longer have direct access to this resource.",
            confirmLabel = if (current.isRemoving) "Removing..." else "Remove",
            onConfirm = viewModel::confirmRemoveGrant,
            onDismiss = viewModel::dismissRemoveGrant,
            icon = AppIcons.PersonRemove,
            isDestructive = true,
            confirmEnabled = !current.isRemoving
        )
    }
}

@Composable
private fun AccessContent(
    state: ManageResourceAccessState.Loaded,
    onPublicAccessChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AccessGrant) -> Unit,
    onRetryRefresh: () -> Unit
) {
    val grants = state.grants.filterNot { it.principalType == AccessPrincipalType.Public }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(state.resource.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "Control who can access this resource.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.refreshError?.let { message ->
            item { ErrorCard(message = message, title = "Could not refresh", onRetry = onRetryRefresh) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("Public access") },
                    supportingContent = { Text("Anyone can view this resource without a direct grant.") },
                    leadingContent = { AppIcon(AppIcons.Public) },
                    trailingContent = {
                        if (state.isChangingPublicAccess) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Switch(checked = state.isPublic, onCheckedChange = onPublicAccessChange)
                        }
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                )
                state.publicAccessError?.let { message ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onPublicAccessChange(!state.isPublic) }) { Text("Retry") }
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Access grants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = onAdd, enabled = state.allowedRoles.isNotEmpty()) {
                    AppIcon(AppIcons.Add, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        }
        if (grants.isEmpty()) {
            item {
                Text(
                    "No direct access grants.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(grants, key = { "${it.principalType}:${it.principalId}" }) { grant ->
                AccessGrantRow(
                    grant = grant,
                    editable = accessGrantMutationTarget(grant) != null && grant.permission.asGrantRole() in state.allowedRoles,
                    onEdit = { onEdit(grant) }
                )
            }
        }
    }
}

@Composable
private fun AccessGrantRow(grant: AccessGrant, editable: Boolean, onEdit: () -> Unit) {
    Card {
        ListItem(
            headlineContent = { Text(grantName(grant), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val reference = grant.slug?.takeUnless { it == grant.displayName }
                Text(listOfNotNull(grant.principalType.displayLabel(), reference).joinToString(" - "))
            },
            leadingContent = { AppIcon(principalIcon(grant.principalType)) },
            trailingContent = {
                RoleBadge(grant.permission.name)
            },
            modifier = if (editable) Modifier.clickable(onClick = onEdit) else Modifier
        )
    }
}

@Composable
private fun AddAccessDialog(
    state: AddAccessState,
    allowedRoles: List<ResourceGrantRole>,
    onTypeChange: (AccessCandidateType) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onSelectCandidate: (AccessCandidate) -> Unit,
    onRoleChange: (ResourceGrantRole) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccessCandidateType.entries.forEach { type ->
                        FilterChip(
                            selected = state.candidateType == type,
                            onClick = { onTypeChange(type) },
                            label = { Text(if (type == AccessCandidateType.User) "User" else "Project") },
                            enabled = !state.isSaving
                        )
                    }
                }
                SearchPickerField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    isSearching = state.isSearching,
                    results = state.candidates,
                    onSelect = onSelectCandidate,
                    label = state.candidateType.label,
                    leadingIcon = if (state.candidateType == AccessCandidateType.User) AppIcons.Person else AppIcons.Project,
                    enabled = !state.isSaving,
                    reopenOnFocus = true,
                    searchError = state.searchError,
                    onRetrySearch = onRetrySearch,
                    resolution = ResolvedPicker(
                        keyOf = { it.requestReference },
                        resolvedLabel = { it.displayName },
                        onClear = { onQueryChange("") },
                        resolvedLeading = { candidate -> AppIcon(principalIcon(candidate.principalType)) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    itemContent = { candidate ->
                        Column {
                            Text(candidate.displayName)
                            candidate.supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                )
                RolePicker(state.role, allowedRoles, !state.isSaving, onRoleChange)
                state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.selected != null && !state.isSaving) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Grant")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } }
    )
}

@Composable
private fun EditAccessDialog(
    state: EditAccessState,
    allowedRoles: List<ResourceGrantRole>,
    onRoleChange: (ResourceGrantRole) -> Unit,
    onRemove: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(grantName(state.grant), style = MaterialTheme.typography.titleMedium)
                RolePicker(state.role, allowedRoles, !state.isSaving, onRoleChange)
                HorizontalDivider()
                TextButton(onClick = onRemove, enabled = !state.isSaving) {
                    AppIcon(AppIcons.PersonRemove, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove access", color = MaterialTheme.colorScheme.error)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } }
    )
}

@Composable
private fun RolePicker(
    selected: ResourceGrantRole,
    roles: List<ResourceGrantRole>,
    enabled: Boolean,
    onSelect: (ResourceGrantRole) -> Unit
) {
    RoleDropdownField(
        selectedRole = selected,
        roles = roles,
        roleKey = { it.name },
        roleLabel = { it.displayLabel() },
        onRoleSelected = onSelect,
        modifier = Modifier.fillMaxWidth(),
        label = "Access level",
        enabled = enabled
    )
}

private fun grantName(grant: AccessGrant): String = grant.displayName ?: grant.slug ?: grant.principalId

private fun principalIcon(type: AccessPrincipalType): AppIconToken = when (type) {
    AccessPrincipalType.User, AccessPrincipalType.ServiceAccount -> AppIcons.Person
    AccessPrincipalType.Project -> AppIcons.Project
    AccessPrincipalType.Instrument -> AppIcons.Instrument
    AccessPrincipalType.Public -> AppIcons.Public
    AccessPrincipalType.System, AccessPrincipalType.Unknown -> AppIcons.ManageMembers
}
