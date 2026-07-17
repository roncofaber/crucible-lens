package crucible.lens.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.UserAvatar
import crucible.lens.ui.common.UserResultItem
import crucible.lens.ui.common.UserSearchField
import crucible.lens.ui.detail.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProjectScreen(
    viewModel: ManageProjectViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val pendingRemove by viewModel.pendingRemove.collectAsState()
    val isAddMemberSheetVisible by viewModel.isAddMemberSheetVisible.collectAsState()

    if (pendingRemove != null) {
        val user = pendingRemove!!
        val displayName = user.username?.let { "@$it" } ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { "this member" }
        AlertDialog(
            onDismissRequest = { viewModel.cancelRemove() },
            icon = { AppIcon(AppIcons.PersonRemove, null) },
            title = { Text("Remove member?") },
            text = { Text("Remove $displayName from the project?") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeMember() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelRemove() }) { Text("Cancel") } }
        )
    }

    if (isAddMemberSheetVisible) {
        AddMemberSheet(viewModel = viewModel, onDismiss = { viewModel.hideAddMemberSheet() })
    }

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.Back)
                    }
                },
                actions = {
                    val loaded = state as? ManageProjectState.Loaded
                    if (loaded?.isLead == true && editState is ProjectEditState.Idle) {
                        IconButton(onClick = { viewModel.startEdit() }) {
                            AppIcon(AppIcons.Edit)
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
                        is ProjectEditState.Idle -> ProjectInfoCard(s.project)
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
                    MembersCard(
                        members = s.members,
                        isLead = s.isLead,
                        onAddMember = { viewModel.showAddMemberSheet() },
                        onRemoveMember = { viewModel.confirmRemove(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoCard(project: Project) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = AppIcons.Project, label = "Title", value = project.title ?: "—")
            InfoRow(icon = AppIcons.Business, label = "Organization", value = project.organization ?: "—")
            val leadDisplay = project.lead?.username?.let { "@$it" }
                ?: listOfNotNull(project.lead?.firstName, project.lead?.lastName).joinToString(" ").ifBlank { "—" }
            InfoRow(icon = AppIcons.Person, label = "Project lead", value = leadDisplay)
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

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
            UserSearchField(
                query = draft.leadUsername,
                onQueryChange = onLeadUsernameChanged,
                isSearching = draft.isLeadSearching,
                label = "Project lead username",
                enabled = !isSaving
            )
            if (draft.leadSearch.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    draft.leadSearch.take(5).forEach { user ->
                        UserResultItem(user = user, onClick = { onSelectLead(user) })
                    }
                }
            }

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
private fun MembersCard(
    members: List<User>,
    isLead: Boolean,
    onAddMember: () -> Unit,
    onRemoveMember: (User) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onAddMember, modifier = Modifier.size(32.dp)) {
                    AppIcon(AppIcons.PersonAdd, modifier = Modifier.size(20.dp))
                }
            }
            members.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(
                        firstName = member.firstName,
                        lastName = member.lastName,
                        size = 36.dp,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        val displayName = listOfNotNull(member.firstName, member.lastName).joinToString(" ").ifBlank { null }
                        if (displayName != null) Text(displayName, style = MaterialTheme.typography.bodyMedium)
                        if (!member.username.isNullOrBlank()) Text("@${member.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isLead && member.uniqueId != null) {
                        IconButton(onClick = { onRemoveMember(member) }, modifier = Modifier.size(32.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(viewModel: ManageProjectViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.memberSearchResults.collectAsState()
    val isSearching by viewModel.isMemberSearching.collectAsState()
    val isAdding by viewModel.isAddingMember.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add Member", style = MaterialTheme.typography.titleLarge)
            UserSearchField(
                query = query,
                onQueryChange = { query = it; viewModel.searchMembers(it) },
                isSearching = isSearching
            )
            searchResults.forEach { user ->
                UserResultItem(
                    user = user,
                    trailingContent = {
                        Button(
                            onClick = { viewModel.addMember(user) },
                            enabled = !isAdding,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("Add") }
                    }
                )
            }
            if (query.length >= 3 && !isSearching && searchResults.isEmpty()) {
                Text("No users found for \"$query\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
