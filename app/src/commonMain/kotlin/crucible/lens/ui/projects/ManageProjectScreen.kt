@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.projects

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import crucible.lens.data.util.formatDateTime
import crucible.lens.data.util.userDisplayName
import crucible.lens.data.util.userSortKey
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.ui.common.ResolvedPicker
import crucible.lens.ui.common.SearchPickerField
import crucible.lens.ui.common.SearchPickerSheet
import crucible.lens.ui.common.UserAvatar
import crucible.lens.ui.common.UserChipLeading
import crucible.lens.ui.common.UserIdentityRow
import crucible.lens.ui.common.UserPickerItemContent
import crucible.lens.ui.common.UserResultItem
import crucible.lens.ui.common.rememberDebouncedSearchResults
import crucible.lens.ui.detail.components.ClickableInfoRow
import crucible.lens.ui.detail.components.InfoRow
import crucible.lens.ui.theme.emphasizedTitleMedium

@Composable
fun ManageProjectScreen(
    viewModel: ManageProjectViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val pendingRemove by viewModel.pendingRemove.collectAsState()
    val isAddMemberSheetVisible by viewModel.isAddMemberSheetVisible.collectAsState()
    val leaveError by viewModel.leaveError.collectAsState()
    var showLeaveDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (pendingRemove != null) {
        val user = pendingRemove!!
        val displayName = userDisplayName(user)
        ConfirmationDialog(
            icon = AppIcons.PersonRemove,
            title = "Remove member?",
            text = "Remove $displayName from the project?",
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = { viewModel.removeMember() },
            onDismiss = { viewModel.cancelRemove() }
        )
    }

    if (showLeaveDialog) {
        ConfirmationDialog(
            icon = AppIcons.SignOut,
            title = "Leave project?",
            text = "You'll lose access to this project's samples and datasets.",
            confirmLabel = "Leave",
            isDestructive = true,
            onConfirm = { showLeaveDialog = false; viewModel.leaveProject(onLeft = onHome) },
            onDismiss = { showLeaveDialog = false }
        )
    }

    if (isAddMemberSheetVisible) {
        // Live snapshot, not a static one from when the sheet opened — a user's row flips to
        // "Added" as soon as viewModel.addMember() actually succeeds, no optimistic guessing.
        val memberIds = (state as? ManageProjectState.Loaded)?.members?.mapNotNull { it.uniqueId }?.toSet() ?: emptySet()
        AddMemberSheet(viewModel = viewModel, memberIds = memberIds, onDismiss = { viewModel.hideAddMemberSheet() })
    }

    LaunchedEffect(leaveError) {
        leaveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissLeaveError()
        }
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Manage Project",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    val loaded = state as? ManageProjectState.Loaded
                    if (loaded != null && editState is ProjectEditState.Idle) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                AppIcon(AppIcons.MoreVert)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                // Any member can add another (the API authorizes admins or
                                // project members, not just the lead); editing project info and
                                // leaving stay lead-only/non-lead-only respectively.
                                DropdownMenuItem(
                                    text = { Text("Add member") },
                                    leadingIcon = { AppIcon(AppIcons.PersonAdd) },
                                    onClick = { menuExpanded = false; viewModel.showAddMemberSheet() }
                                )
                                if (loaded.isLead) {
                                    DropdownMenuItem(
                                        text = { Text("Edit project") },
                                        leadingIcon = { AppIcon(AppIcons.Edit) },
                                        onClick = { menuExpanded = false; viewModel.startEdit() }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Leave project") },
                                    leadingIcon = { AppIcon(AppIcons.SignOut) },
                                    enabled = !loaded.isLead,
                                    onClick = { menuExpanded = false; showLeaveDialog = true }
                                )
                            }
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                is ManageProjectState.Loading -> LoadingContent(title = "Loading project")
                is ManageProjectState.Error -> ErrorCard(
                    title = "Could not load project",
                    message = s.message,
                    onRetry = { viewModel.load() }
                )
                is ManageProjectState.Loaded -> {
                    when (val es = editState) {
                        is ProjectEditState.Idle -> ProjectInfoCard(s.project, onUserClick = onUserClick)
                        is ProjectEditState.Editing, is ProjectEditState.Saving, is ProjectEditState.SaveError -> {
                            val draft = when (es) {
                                is ProjectEditState.Editing -> es
                                is ProjectEditState.SaveError -> es.draft
                                else -> return@Column
                            }
                            val isSaving = es is ProjectEditState.Saving
                            val saveError = (es as? ProjectEditState.SaveError)?.message
                            ProjectEditCard(
                                draft = draft,
                                isSaving = isSaving,
                                saveError = saveError,
                                onTitleChanged = viewModel::onTitleChanged,
                                onOrganizationChanged = viewModel::onOrganizationChanged,
                                onLeadUsernameChanged = viewModel::onLeadUsernameChanged,
                                onSelectLead = viewModel::selectLeadUser,
                                onSave = { viewModel.saveProject() },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        }
                    }
                    if (s.isLead && s.joinRequests.isNotEmpty()) {
                        PendingRequestsCard(
                            requests = s.joinRequests,
                            requesterInfo = s.requesterInfo,
                            onApprove = { viewModel.approveJoinRequest(it) },
                            onReject = { viewModel.rejectJoinRequest(it) },
                            onUserClick = onUserClick
                        )
                    }
                    MembersCard(
                        members = s.members,
                        isLead = s.isLead,
                        leadOrcid = s.project.projectLeadOrcid,
                        onAddMember = { viewModel.showAddMemberSheet() },
                        onRemoveMember = { viewModel.confirmRemove(it) },
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoCard(project: Project, onUserClick: (String) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = AppIcons.Project, label = "Title", value = project.title ?: "—")
            InfoRow(icon = AppIcons.Business, label = "Organization", value = project.organization ?: "—")
            val lead = project.lead
            val leadDisplay = lead?.let { userDisplayName(it) } ?: "—"
            val leadIdentifier = lead?.username ?: lead?.uniqueId
            if (leadIdentifier != null) {
                ClickableInfoRow(icon = AppIcons.Person, label = "Project lead", value = leadDisplay, onClick = { onUserClick(leadIdentifier) })
            } else {
                InfoRow(icon = AppIcons.Person, label = "Project lead", value = leadDisplay)
            }
            InfoRow(icon = AppIcons.Tag, label = "Project ID", value = project.projectId)
        }
    }
}

@Composable
private fun ProjectEditCard(
    draft: ProjectEditState.Editing,
    isSaving: Boolean,
    saveError: String?,
    onTitleChanged: (String) -> Unit,
    onOrganizationChanged: (String) -> Unit,
    onLeadUsernameChanged: (String) -> Unit,
    onSelectLead: (User) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Project", style = MaterialTheme.typography.emphasizedTitleMedium)

            if (saveError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(saveError, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChanged,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = draft.organization,
                onValueChange = onOrganizationChanged,
                label = { Text("Organization") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            SearchPickerField(
                query = draft.leadUsername,
                onQueryChange = onLeadUsernameChanged,
                isSearching = draft.isLeadSearching,
                results = draft.leadSearch,
                onSelect = onSelectLead,
                label = "Project lead",
                enabled = !isSaving,
                resolution = ResolvedPicker(
                    keyOf = { it.username },
                    resolvedLabel = { userDisplayName(it) },
                    onClear = { onLeadUsernameChanged("") },
                    resolvedLeading = { user -> UserChipLeading(user) }
                ),
                itemContent = { user -> UserPickerItemContent(user) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) { Text("Cancel") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isSaving && draft.title.isNotBlank()) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}

@Composable
private fun PendingRequestsCard(
    requests: List<JoinRequest>,
    requesterInfo: Map<String, User>,
    onApprove: (JoinRequest) -> Unit,
    onReject: (JoinRequest) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pending Requests (${requests.size})", style = MaterialTheme.typography.emphasizedTitleMedium)
            requests.forEach { request ->
                val requester = requesterInfo[request.requesterId]
                val requesterIdentifier = requester?.username ?: request.requesterId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(
                        firstName = requester?.firstName,
                        lastName = requester?.lastName,
                        size = 36.dp,
                        orcid = requester?.uniqueId ?: request.requesterId,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(
                        modifier = Modifier.weight(1f).clickable { onUserClick(requesterIdentifier) }
                    ) {
                        Text(userDisplayName(requester?.firstName, requester?.lastName, requester?.username, request.requesterId), style = MaterialTheme.typography.bodyMedium)
                        if (!request.reason.isNullOrBlank()) {
                            Text(request.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatDateTime(request.requestTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onApprove(request) },
                        modifier = Modifier.size(32.dp).semantics { contentDescription = "Approve request" }
                    ) {
                        AppIcon(AppIcons.Check, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { onReject(request) },
                        modifier = Modifier.size(32.dp).semantics { contentDescription = "Reject request" }
                    ) {
                        AppIcon(AppIcons.UsernameTaken, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersCard(
    members: List<User>,
    isLead: Boolean,
    leadOrcid: String?,
    onAddMember: () -> Unit,
    onRemoveMember: (User) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(StandardSizeAnim),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Members (${members.size})", style = MaterialTheme.typography.emphasizedTitleMedium)
                ExpandChevron(expanded = expanded)
            }
            if (expanded) {
                // Add member sits above the member list as a row matching the member rows below
                // it (circular icon in the avatar slot, left-aligned label) — WhatsApp-style "add
                // participant" as the list's first row, not a separate button glued above it. Any
                // member can add another (the API authorizes admins or project members, not just
                // the lead — see POST /projects/{id}/users/{orcid}).
                //
                // Solid primary/onPrimary, not primaryContainer/primary: every real member avatar
                // below gets a bold, per-ORCID-derived hue (see UserAvatar's orcidToColor), so a
                // softer container/on-container pairing here read as just another muted avatar
                // rather than a distinct action. primary/onPrimary is the same high-contrast
                // pairing M3 uses for FABs, which is the right register for "add new."
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddMember),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            AppIcon(AppIcons.PersonAdd, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text("Add member", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                val sortedMembers = remember(members) { members.sortedBy { userSortKey(it) } }
                sortedMembers.forEach { member ->
                    val memberIdentifier = member.username ?: member.uniqueId
                    UserIdentityRow(
                        user = member,
                        avatarContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        avatarContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = if (memberIdentifier != null) ({ onUserClick(memberIdentifier) }) else null
                    ) {
                        // The lead can't remove themselves from their own project via this list.
                        if (isLead && member.uniqueId != null && member.uniqueId != leadOrcid) {
                            IconButton(onClick = { onRemoveMember(member) }) {
                                AppIcon(AppIcons.PersonRemove, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (members.isEmpty()) {
                    Text("No members yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AddMemberSheet(
    viewModel: ManageProjectViewModel,
    memberIds: Set<String>,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.memberSearchResults.collectAsState()
    val isSearching by viewModel.isMemberSearching.collectAsState()
    val isAdding by viewModel.isAddingMember.collectAsState()

    SearchPickerSheet(
        title = "Add Member",
        query = query,
        onQueryChange = { query = it; viewModel.searchMembers(it) },
        isSearching = isSearching,
        results = searchResults,
        onDismiss = onDismiss,
        label = "Search user",
        key = { it.uniqueId ?: it.username ?: it.hashCode().toString() },
        emptyContent = {
            if (query.length >= 3 && !isSearching) {
                Text("No users found for \"$query\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        itemContent = { user ->
            val alreadyMember = user.uniqueId != null && user.uniqueId in memberIds
            UserResultItem(
                user = user,
                trailingContent = {
                    if (alreadyMember) {
                        Text("Added", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(
                            onClick = { viewModel.addMember(user) },
                            enabled = !isAdding,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("Add") }
                    }
                }
            )
        }
    )
}
