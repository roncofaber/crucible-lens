@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import crucible.lens.data.util.ProjectMemberRole
import crucible.lens.data.util.formatDateTime
import crucible.lens.data.util.projectMemberComparator
import crucible.lens.data.util.userDisplayName
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AddOrAddedAction
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.OwnershipTransferConfirmationDialog
import crucible.lens.ui.common.OwnershipTransferDraft
import crucible.lens.ui.common.OwnershipTransferPickerSheet
import crucible.lens.ui.common.OwnershipTransferProgressDialog
import crucible.lens.ui.common.ResourceIdRenameDialog
import crucible.lens.ui.common.RoleBadge
import crucible.lens.ui.common.RoleDropdownField
import crucible.lens.ui.common.CompactRoleDropdown
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.ui.common.SearchPickerSheet
import crucible.lens.ui.common.UserAvatar
import crucible.lens.ui.common.UserIdentityRow
import crucible.lens.ui.common.UserResultItem
import crucible.lens.ui.detail.components.ClickableInfoRow
import crucible.lens.ui.detail.components.InfoRow

@Composable
fun ManageProjectScreen(
    viewModel: ManageProjectViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val leadershipTransferState by viewModel.leadershipTransferState.collectAsStateWithLifecycle()
    val projectRenameState by viewModel.projectRenameState.collectAsStateWithLifecycle()
    val memberRoleState by viewModel.memberRoleState.collectAsStateWithLifecycle()
    val pendingRemove by viewModel.pendingRemove.collectAsStateWithLifecycle()
    val removingMemberOrcid by viewModel.removingMemberOrcid.collectAsStateWithLifecycle()
    val removeMemberError by viewModel.removeMemberError.collectAsStateWithLifecycle()
    val reviewingJoinRequestId by viewModel.reviewingJoinRequestId.collectAsStateWithLifecycle()
    val projectActionError by viewModel.projectActionError.collectAsStateWithLifecycle()
    val isAddMemberSheetVisible by viewModel.isAddMemberSheetVisible.collectAsStateWithLifecycle()
    val leaveError by viewModel.leaveError.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var isEditingMemberRoles by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditingProject = editState !is ProjectEditState.Idle
    val canContinueEditingMemberRoles = (state as? ManageProjectState.Loaded)?.let {
        it.membersError == null && it.canManageAccess && it.assignableRoles.isNotEmpty()
    } == true

    LaunchedEffect(canContinueEditingMemberRoles) {
        if (!canContinueEditingMemberRoles && isEditingMemberRoles) {
            isEditingMemberRoles = false
            viewModel.clearMemberRoleError()
        }
    }

    BackHandler(enabled = isEditingProject || isEditingMemberRoles) {
        when {
            isEditingMemberRoles -> {
                isEditingMemberRoles = false
                viewModel.clearMemberRoleError()
            }
            editState !is ProjectEditState.Saving -> viewModel.cancelEdit()
        }
    }

    when (val renameState = projectRenameState) {
        is ProjectRenameState.Editing, is ProjectRenameState.Saving -> {
            val value = when (renameState) {
                is ProjectRenameState.Editing -> renameState.value
                is ProjectRenameState.Saving -> renameState.value
            }
            val error = (renameState as? ProjectRenameState.Editing)?.error
            val isSaving = renameState is ProjectRenameState.Saving
            ResourceIdRenameDialog(
                resourceName = "project",
                value = value,
                error = error,
                isSaving = isSaving,
                impactText = "Renaming changes the project’s web URLs. Pins, sync selection, and app navigation remain connected through the project MFID.",
                onValueChange = viewModel::updateProjectRename,
                onRename = viewModel::renameProject,
                onDismiss = viewModel::dismissProjectRename
            )
        }
        else -> Unit
    }

    when (val transferState = leadershipTransferState) {
        is LeadershipTransferState.Selecting -> OwnershipTransferPickerSheet(
            title = "Transfer Leadership",
            supportingText = "The selected account will become the project owner",
            draft = OwnershipTransferDraft(
                query = transferState.draft.query,
                results = transferState.draft.results,
                isSearching = transferState.draft.isSearching,
                searchError = transferState.draft.searchError,
                actionError = transferState.draft.actionError
            ),
            onQueryChange = viewModel::searchLeadershipCandidates,
            onSelect = viewModel::previewLeadershipTransfer,
            onDismiss = viewModel::dismissLeadershipTransfer
        )
        is LeadershipTransferState.Previewing -> OwnershipTransferProgressDialog()
        is LeadershipTransferState.PreviewReady, is LeadershipTransferState.Transferring -> {
            val preview = (transferState as? LeadershipTransferState.PreviewReady)?.preview
                ?: (transferState as LeadershipTransferState.Transferring).preview
            val error = (transferState as? LeadershipTransferState.PreviewReady)?.error
            val isTransferring = transferState is LeadershipTransferState.Transferring
            OwnershipTransferConfirmationDialog(
                title = "Transfer leadership?",
                description = "The new lead will become the project owner. The previous lead will remain a project administrator.",
                preview = preview,
                error = error,
                isTransferring = isTransferring,
                onConfirm = viewModel::confirmLeadershipTransfer,
                onDismiss = viewModel::dismissLeadershipTransfer
            )
        }
        else -> Unit
    }

    if (pendingRemove != null) {
        val user = pendingRemove!!
        val displayName = userDisplayName(user)
        val isRemoving = user.uniqueId == removingMemberOrcid
        AlertDialog(
            onDismissRequest = { if (!isRemoving) viewModel.cancelRemove() },
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcons.PersonRemove, tint = MaterialTheme.colorScheme.error)
                    Text("Remove member?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove $displayName from the project?")
                    removeMemberError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeMember() }, enabled = !isRemoving) {
                    if (isRemoving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRemove() }, enabled = !isRemoving) { Text("Cancel") }
            }
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
        val loaded = state as? ManageProjectState.Loaded
        val memberIds = loaded?.members?.mapNotNull { it.uniqueId }?.toSet() ?: emptySet()
        AddMemberSheet(
            viewModel = viewModel,
            memberIds = memberIds,
            assignableRoles = loaded?.assignableRoles.orEmpty(),
            onDismiss = { viewModel.hideAddMemberSheet() }
        )
    }

    LaunchedEffect(leaveError) {
        leaveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissLeaveError()
        }
    }

    LaunchedEffect(projectActionError) {
        projectActionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissProjectActionError()
        }
    }

    LaunchedEffect(leadershipTransferState) {
        val success = leadershipTransferState as? LeadershipTransferState.Success ?: return@LaunchedEffect
        viewModel.consumeLeadershipTransferSuccess()
        snackbarHostState.showSnackbar("Leadership transferred to ${userDisplayName(success.newOwner)}")
    }

    LaunchedEffect(projectRenameState) {
        val success = projectRenameState as? ProjectRenameState.Success ?: return@LaunchedEffect
        viewModel.consumeProjectRenameSuccess()
        snackbarHostState.showSnackbar("Project ID changed to ${success.projectSlug}")
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Manage Project",
                onBack = {
                    if (isEditingProject) {
                        if (editState !is ProjectEditState.Saving) viewModel.cancelEdit()
                    } else {
                        onBack()
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    val loaded = state as? ManageProjectState.Loaded
                    if (loaded != null && editState is ProjectEditState.Idle && !isEditingMemberRoles) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                AppIcon(AppIcons.MoreVert)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                if (loaded.canManageAccess && loaded.assignableRoles.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Add member") },
                                        leadingIcon = { AppIcon(AppIcons.PersonAdd) },
                                        enabled = loaded.membersError == null,
                                        onClick = { menuExpanded = false; viewModel.showAddMemberSheet() }
                                    )
                                }
                                if (loaded.canEdit) {
                                    DropdownMenuItem(
                                        text = { Text("Edit project") },
                                        leadingIcon = { AppIcon(AppIcons.Edit) },
                                        onClick = { menuExpanded = false; viewModel.startEdit() }
                                    )
                                }
                                if (loaded.canRename) {
                                    DropdownMenuItem(
                                        text = { Text("Rename project ID") },
                                        leadingIcon = { AppIcon(AppIcons.Tag) },
                                        onClick = { menuExpanded = false; viewModel.showProjectRename() }
                                    )
                                }
                                if (loaded.canTransfer) {
                                    DropdownMenuItem(
                                        text = { Text("Transfer leadership") },
                                        leadingIcon = { AppIcon(AppIcons.ManageMembers) },
                                        onClick = { menuExpanded = false; viewModel.showLeadershipTransfer() }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Leave project") },
                                    leadingIcon = { AppIcon(AppIcons.SignOut) },
                                    enabled = loaded.canLeave,
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
                                onSave = { viewModel.saveProject() },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        }
                    }
                    if (s.canReviewJoinRequests) {
                        if (s.joinRequestsError != null) {
                            ErrorCard(
                                title = "Could not load pending requests",
                                message = s.joinRequestsError,
                                onRetry = { viewModel.retryJoinRequests() }
                            )
                        } else if (s.joinRequests.isNotEmpty()) {
                            PendingRequestsCard(
                                requests = s.joinRequests,
                                requesterInfo = s.requesterInfo,
                                reviewingRequestId = reviewingJoinRequestId,
                                onApprove = { viewModel.approveJoinRequest(it) },
                                onReject = { viewModel.rejectJoinRequest(it) },
                                onUserClick = onUserClick
                            )
                        }
                    }
                    if (s.membersError != null) {
                        ErrorCard(
                            title = "Could not load members",
                            message = s.membersError,
                            onRetry = { viewModel.retryMembers() }
                        )
                    } else {
                        MembersCard(
                            members = s.members,
                            canRemoveMembers = s.canTransfer,
                            canManageAccess = s.canManageAccess && s.assignableRoles.isNotEmpty(),
                            canEditMemberRole = s::canChangeMemberRole,
                            assignableRolesFor = s::assignableRolesFor,
                            memberRoleState = memberRoleState,
                            isEditingRoles = isEditingMemberRoles,
                            leadOrcid = s.project.projectLeadOrcid,
                            onAddMember = { viewModel.showAddMemberSheet() },
                            onEditingRolesChange = { editing ->
                                isEditingMemberRoles = editing
                                if (!editing) viewModel.clearMemberRoleError()
                            },
                            onMemberRoleChange = viewModel::changeMemberRole,
                            onClearRoleError = viewModel::clearMemberRoleError,
                            onRemoveMember = { viewModel.confirmRemove(it) },
                            onUserClick = onUserClick
                        )
                    }
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
            InfoRow(icon = AppIcons.Category, label = "Status", value = project.status?.replaceFirstChar { it.uppercase() } ?: "Not available")
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
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Project", style = MaterialTheme.typography.titleMedium)

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
    reviewingRequestId: Int?,
    onApprove: (JoinRequest) -> Unit,
    onReject: (JoinRequest) -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pending Requests (${requests.size})", style = MaterialTheme.typography.titleMedium)
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
                    if (reviewingRequestId == request.id) {
                        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(
                            onClick = { onApprove(request) },
                            enabled = reviewingRequestId == null,
                            modifier = Modifier.size(32.dp).semantics { contentDescription = "Approve request" }
                        ) {
                            AppIcon(AppIcons.Check, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onReject(request) },
                            enabled = reviewingRequestId == null,
                            modifier = Modifier.size(32.dp).semantics { contentDescription = "Reject request" }
                        ) {
                            AppIcon(AppIcons.UsernameTaken, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersCard(
    members: List<User>,
    canRemoveMembers: Boolean,
    canManageAccess: Boolean,
    canEditMemberRole: (ProjectMemberRole?) -> Boolean,
    assignableRolesFor: (User) -> List<ProjectMemberRole>,
    memberRoleState: MemberRoleState,
    isEditingRoles: Boolean,
    leadOrcid: String?,
    onAddMember: () -> Unit,
    onEditingRolesChange: (Boolean) -> Unit,
    onMemberRoleChange: (User, ProjectMemberRole) -> Unit,
    onClearRoleError: () -> Unit,
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
                modifier = Modifier.fillMaxWidth().height(48.dp).clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (expanded && canManageAccess && members.any { canEditMemberRole(ProjectMemberRole.fromApi(it.role)) }) {
                    TextButton(onClick = { onEditingRolesChange(!isEditingRoles) }) {
                        AppIcon(if (isEditingRoles) AppIcons.Check else AppIcons.Edit, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditingRoles) "Done" else "Edit roles")
                    }
                }
                ExpandChevron(expanded = expanded)
            }
            if (expanded) {
                if (canManageAccess && !isEditingRoles) {
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
                }
                val sortedMembers = remember(members) { members.sortedWith(projectMemberComparator) }
                sortedMembers.forEach { member ->
                    val memberIdentifier = member.username ?: member.uniqueId
                    UserIdentityRow(
                        user = member,
                        avatarContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        avatarContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = if (!isEditingRoles && memberIdentifier != null) ({ onUserClick(memberIdentifier) }) else null
                    ) {
                        val memberRole = ProjectMemberRole.fromApi(member.role)
                        val canEditRole = canEditMemberRole(memberRole)
                        val memberId = member.uniqueId
                        val isSavingRole = memberRoleState is MemberRoleState.Saving && memberRoleState.userId == memberId
                        if (isEditingRoles && canEditRole && memberRole != null) {
                            if (isSavingRole) {
                                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                CompactRoleDropdown(
                                    selectedRole = memberRole,
                                    roles = assignableRolesFor(member),
                                    roleKey = { it.apiValue },
                                    roleLabel = { it.label },
                                    onRoleSelected = { onMemberRoleChange(member, it) },
                                    enabled = memberRoleState !is MemberRoleState.Saving
                                )
                            }
                        } else {
                            RoleBadge(
                                role = memberRole?.apiValue ?: member.role,
                                label = if (memberRole == ProjectMemberRole.Owner) "Lead" else null
                            )
                        }
                        if (!isEditingRoles) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                if (canRemoveMembers && memberId != null && memberId != leadOrcid) {
                                    IconButton(onClick = { onRemoveMember(member) }) {
                                        AppIcon(AppIcons.PersonRemove, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    val roleError = (memberRoleState as? MemberRoleState.Error)?.takeIf { it.userId == member.uniqueId }
                    if (roleError != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                roleError.message,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = onClearRoleError) {
                                AppIcon(AppIcons.ClearInput, modifier = Modifier.size(16.dp))
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
    assignableRoles: List<ProjectMemberRole>,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.memberSearchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isMemberSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.memberSearchError.collectAsStateWithLifecycle()
    val addError by viewModel.addMemberError.collectAsStateWithLifecycle()
    val addingMemberId by viewModel.addingMemberId.collectAsStateWithLifecycle()
    var selectedRole by remember(assignableRoles) {
        mutableStateOf(ProjectMemberRole.Contributor.takeIf { it in assignableRoles } ?: assignableRoles.firstOrNull() ?: ProjectMemberRole.Contributor)
    }

    SearchPickerSheet(
        title = "Add Member",
        query = query,
        onQueryChange = { query = it; viewModel.searchMembers(it) },
        isSearching = isSearching,
        results = searchResults,
        onDismiss = onDismiss,
        label = "Search user",
        key = { it.uniqueId ?: it.username ?: it.hashCode().toString() },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoleDropdownField(
                    selectedRole = selectedRole,
                    roles = assignableRoles,
                    roleKey = { it.apiValue },
                    roleLabel = { it.label },
                    onRoleSelected = { selectedRole = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Add as"
                )
                val message = addError ?: searchError
                if (message != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (searchError != null) {
                            TextButton(onClick = { viewModel.searchMembers(query) }) { Text("Retry") }
                        }
                    }
                }
            }
        },
        emptyContent = {
            if (query.length >= 3 && !isSearching && searchError == null) {
                Text("No users found for \"$query\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        itemContent = { user ->
            val alreadyMember = user.uniqueId != null && user.uniqueId in memberIds
            UserResultItem(
                user = user,
                trailingContent = {
                    if (user.uniqueId != null) {
                        AddOrAddedAction(
                            added = alreadyMember,
                            isAdding = user.uniqueId == addingMemberId,
                            enabled = addingMemberId == null && (!user.isServiceAccount || selectedRole <= ProjectMemberRole.Contributor),
                            onAdd = { viewModel.addMember(user, selectedRole) }
                        )
                    }
                }
            )
        }
    )
}
