@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package crucible.lens.ui.instruments

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.model.User
import crucible.lens.data.util.userDisplayName
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.OwnershipTransferConfirmationDialog
import crucible.lens.ui.common.OwnershipTransferPickerSheet
import crucible.lens.ui.common.OwnershipTransferProgressDialog
import crucible.lens.ui.common.OwnershipTransferState
import crucible.lens.ui.common.ResourceIdRenameDialog
import crucible.lens.ui.common.ResourceRenameState
import crucible.lens.ui.common.UserIdentityRow
import crucible.lens.ui.detail.components.ClickableInfoRow
import crucible.lens.ui.detail.components.InfoRow

@Composable
fun ManageInstrumentScreen(
    viewModel: ManageInstrumentViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit = {},
    onUserClick: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val renameState by viewModel.renameState.collectAsStateWithLifecycle()
    val ownershipTransferState by viewModel.ownershipTransferState.collectAsStateWithLifecycle()
    val statusState by viewModel.statusState.collectAsStateWithLifecycle()
    val serviceAccountsState by viewModel.serviceAccountsState.collectAsStateWithLifecycle()
    val serviceAccountAddState by viewModel.serviceAccountAddState.collectAsStateWithLifecycle()
    val serviceAccountRemovalState by viewModel.serviceAccountRemovalState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = editState !is InstrumentEditState.Idle

    BackHandler(enabled = isEditing) {
        if (editState !is InstrumentEditState.Saving) viewModel.cancelEdit()
    }

    when (val current = renameState) {
        is ResourceRenameState.Editing, is ResourceRenameState.Saving -> {
            val value = when (current) {
                is ResourceRenameState.Editing -> current.value
                is ResourceRenameState.Saving -> current.value
            }
            ResourceIdRenameDialog(
                resourceName = "instrument",
                value = value,
                error = (current as? ResourceRenameState.Editing)?.error,
                isSaving = current is ResourceRenameState.Saving,
                impactText = "Renaming changes the instrument’s web URLs. App navigation remains connected through the instrument MFID.",
                onValueChange = viewModel::updateRename,
                onRename = viewModel::rename,
                onDismiss = viewModel::dismissRename
            )
        }
        else -> Unit
    }

    when (val current = ownershipTransferState) {
        is OwnershipTransferState.Selecting -> OwnershipTransferPickerSheet(
            title = "Transfer Ownership",
            supportingText = "The selected account will become the instrument owner",
            draft = current.draft,
            onQueryChange = viewModel::searchOwnershipCandidates,
            onSelect = viewModel::previewOwnershipTransfer,
            onDismiss = viewModel::dismissOwnershipTransfer
        )
        is OwnershipTransferState.Previewing -> OwnershipTransferProgressDialog()
        is OwnershipTransferState.PreviewReady, is OwnershipTransferState.Transferring -> {
            val preview = (current as? OwnershipTransferState.PreviewReady)?.preview
                ?: (current as OwnershipTransferState.Transferring).preview
            OwnershipTransferConfirmationDialog(
                title = "Transfer instrument ownership?",
                description = "The previous owner will lose owner access unless it is granted through another group.",
                preview = preview,
                error = (current as? OwnershipTransferState.PreviewReady)?.error,
                isTransferring = current is OwnershipTransferState.Transferring,
                onConfirm = viewModel::confirmOwnershipTransfer,
                onDismiss = viewModel::dismissOwnershipTransfer
            )
        }
        else -> Unit
    }

    when (val current = statusState) {
        is InstrumentStatusState.Selecting -> InstrumentStatusDialog(
            current = current.current,
            selected = current.selected,
            error = current.error,
            isSaving = false,
            onSelect = viewModel::selectStatus,
            onConfirm = viewModel::requestStatusChange,
            onDismiss = viewModel::dismissStatusChange
        )
        is InstrumentStatusState.Saving -> InstrumentStatusDialog(
            current = InstrumentStatus.fromApi((state as? InstrumentManageState.Loaded)?.instrument?.status),
            selected = current.status,
            error = null,
            isSaving = true,
            onSelect = {},
            onConfirm = {},
            onDismiss = {}
        )
        is InstrumentStatusState.Confirming -> ConfirmationDialog(
            icon = AppIcons.Warning,
            title = "Decommission instrument?",
            text = "This retires the instrument and removes it from active lists and pickers. You can reactivate it later from a direct or pinned instrument link.",
            confirmLabel = "Decommission",
            isDestructive = true,
            onConfirm = viewModel::confirmStatusChange,
            onDismiss = viewModel::cancelStatusConfirmation
        )
        else -> Unit
    }

    when (val current = serviceAccountAddState) {
        is ServiceAccountAddState.Editing,
        is ServiceAccountAddState.LookingUp,
        is ServiceAccountAddState.Resolved,
        is ServiceAccountAddState.Adding -> AddServiceAccountDialog(
            state = current,
            onQueryChange = viewModel::updateServiceAccountQuery,
            onLookup = viewModel::lookupServiceAccount,
            onAdd = viewModel::addServiceAccount,
            onDismiss = viewModel::dismissAddServiceAccount
        )
        else -> Unit
    }

    when (val current = serviceAccountRemovalState) {
        is ServiceAccountRemovalState.Confirming,
        is ServiceAccountRemovalState.Removing -> RemoveServiceAccountDialog(
            state = current,
            onConfirm = viewModel::removeServiceAccount,
            onDismiss = viewModel::dismissRemoveServiceAccount
        )
        else -> Unit
    }

    LaunchedEffect(renameState) {
        val success = renameState as? ResourceRenameState.Success ?: return@LaunchedEffect
        viewModel.consumeRenameSuccess()
        snackbarHostState.showSnackbar("Instrument ID changed to ${success.resourceId}")
    }

    LaunchedEffect(ownershipTransferState) {
        val success = ownershipTransferState as? OwnershipTransferState.Success ?: return@LaunchedEffect
        viewModel.consumeOwnershipTransferSuccess()
        snackbarHostState.showSnackbar("Ownership transferred to ${userDisplayName(success.newOwner)}")
    }

    LaunchedEffect(statusState) {
        val success = statusState as? InstrumentStatusState.Success ?: return@LaunchedEffect
        viewModel.consumeStatusSuccess()
        snackbarHostState.showSnackbar("Instrument status changed to ${success.status.label}")
    }

    LaunchedEffect(serviceAccountAddState) {
        val success = serviceAccountAddState as? ServiceAccountAddState.Success ?: return@LaunchedEffect
        viewModel.consumeServiceAccountAddSuccess()
        snackbarHostState.showSnackbar("Added ${serviceAccountLabel(success.account)} as an operator")
    }

    LaunchedEffect(serviceAccountRemovalState) {
        val success = serviceAccountRemovalState as? ServiceAccountRemovalState.Success ?: return@LaunchedEffect
        viewModel.consumeServiceAccountRemovalSuccess()
        snackbarHostState.showSnackbar("Removed ${serviceAccountLabel(success.account)}")
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Manage Instrument",
                onBack = {
                    if (isEditing) {
                        if (editState !is InstrumentEditState.Saving) viewModel.cancelEdit()
                    } else {
                        onBack()
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    if (state is InstrumentManageState.Loaded && editState is InstrumentEditState.Idle) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                AppIcon(AppIcons.MoreVert)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                val loaded = state as InstrumentManageState.Loaded
                                if (loaded.canEdit) {
                                    DropdownMenuItem(
                                        text = { Text("Edit instrument") },
                                        leadingIcon = { AppIcon(AppIcons.Edit) },
                                        onClick = { menuExpanded = false; viewModel.startEdit() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename instrument ID") },
                                        leadingIcon = { AppIcon(AppIcons.Tag) },
                                        onClick = { menuExpanded = false; viewModel.showRename() }
                                    )
                                }
                                if (loaded.canChangeStatus) {
                                    DropdownMenuItem(
                                        text = { Text("Change status") },
                                        leadingIcon = { AppIcon(AppIcons.Category) },
                                        onClick = { menuExpanded = false; viewModel.showStatusSelector() }
                                    )
                                }
                                if (loaded.canTransfer) {
                                    DropdownMenuItem(
                                        text = { Text("Transfer ownership") },
                                        leadingIcon = { AppIcon(AppIcons.ManageMembers) },
                                        onClick = { menuExpanded = false; viewModel.showOwnershipTransfer() }
                                    )
                                }
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
                is InstrumentManageState.Loading -> LoadingContent(title = "Loading instrument")
                is InstrumentManageState.Error -> ErrorCard(
                    title = "Could not load instrument",
                    message = s.message,
                    onRetry = { viewModel.load() }
                )
                is InstrumentManageState.Loaded -> {
                    when (val es = editState) {
                        is InstrumentEditState.Idle -> InstrumentInfoCard(s.instrument, onUserClick)
                        is InstrumentEditState.Editing,
                        is InstrumentEditState.Saving,
                        is InstrumentEditState.SaveError -> {
                            val draft = when (es) {
                                is InstrumentEditState.Editing -> es
                                is InstrumentEditState.SaveError -> es.draft
                                else -> return@Column
                            }
                            InstrumentEditCard(
                                draft = draft,
                                isSaving = es is InstrumentEditState.Saving,
                                saveError = (es as? InstrumentEditState.SaveError)?.message,
                                onNameChanged = viewModel::onNameChanged,
                                onTypeChanged = viewModel::onTypeChanged,
                                onManufacturerChanged = viewModel::onManufacturerChanged,
                                onModelChanged = viewModel::onModelChanged,
                                onLocationChanged = viewModel::onLocationChanged,
                                onDescriptionChanged = viewModel::onDescriptionChanged,
                                onOtherIdChanged = viewModel::onOtherIdChanged,
                                onOtherIdSourceChanged = viewModel::onOtherIdSourceChanged,
                                onSave = { viewModel.save() },
                                onCancel = { viewModel.cancelEdit() }
                            )
                        }
                    }
                    if (s.canManageAccess && editState is InstrumentEditState.Idle) {
                        ServiceAccountsCard(
                            state = serviceAccountsState,
                            onRetry = viewModel::loadServiceAccounts,
                            onAdd = viewModel::showAddServiceAccount,
                            onRemove = viewModel::confirmRemoveServiceAccount
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddServiceAccountDialog(
    state: ServiceAccountAddState,
    onQueryChange: (String) -> Unit,
    onLookup: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val query = when (state) {
        is ServiceAccountAddState.Editing -> state.query
        is ServiceAccountAddState.LookingUp -> state.query
        is ServiceAccountAddState.Resolved -> state.query
        is ServiceAccountAddState.Adding -> state.query
        else -> ""
    }
    val account = when (state) {
        is ServiceAccountAddState.Resolved -> state.account
        is ServiceAccountAddState.Adding -> state.account
        else -> null
    }
    val error = when (state) {
        is ServiceAccountAddState.Editing -> state.error
        is ServiceAccountAddState.Resolved -> state.error
        else -> null
    }
    val isLookingUp = state is ServiceAccountAddState.LookingUp
    val isAdding = state is ServiceAccountAddState.Adding
    val isBusy = isLookingUp || isAdding

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Add service account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter an exact service-account username or MFID.")
                if (account == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("Username or MFID") },
                        singleLine = true,
                        enabled = !isBusy,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    UserIdentityRow(user = account)
                    Text("Operator access allows this account to edit the instrument record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!isAdding) {
                        TextButton(onClick = { onQueryChange(query) }) { Text("Choose another") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (account == null) onLookup else onAdd,
                enabled = !isBusy && query.isNotBlank() && (state !is ServiceAccountAddState.Resolved || state.error == null)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (account == null) "Look up" else "Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") }
        }
    )
}

@Composable
private fun RemoveServiceAccountDialog(
    state: ServiceAccountRemovalState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val account = when (state) {
        is ServiceAccountRemovalState.Confirming -> state.account
        is ServiceAccountRemovalState.Removing -> state.account
        else -> return
    }
    val error = (state as? ServiceAccountRemovalState.Confirming)?.error
    val isRemoving = state is ServiceAccountRemovalState.Removing
    AlertDialog(
        onDismissRequest = { if (!isRemoving) onDismiss() },
        title = { Text("Remove instrument operator?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UserIdentityRow(user = account)
                Text("This service account will lose operator access to the instrument.")
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isRemoving) {
                if (isRemoving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRemoving) { Text("Cancel") }
        }
    )
}

@Composable
private fun ServiceAccountsCard(
    state: LoadState<List<User>>,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (User) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Service accounts", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onAdd) {
                    AppIcon(AppIcons.PersonAdd, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
            when (state) {
                is LoadState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Loading operators", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is LoadState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                is LoadState.Success -> {
                    if (state.data.isEmpty()) {
                        Text("No service accounts are bound", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.data.sortedBy { it.username ?: it.uniqueId }.forEach { account ->
                            UserIdentityRow(user = account) {
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        "Operator",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Box(modifier = Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    IconButton(onClick = { onRemove(account) }) {
                                        AppIcon(AppIcons.PersonRemove, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun serviceAccountLabel(account: User): String = account.username?.let { "@$it" } ?: account.uniqueId ?: "service account"

@Composable
private fun InstrumentStatusDialog(
    current: InstrumentStatus?,
    selected: InstrumentStatus?,
    error: String?,
    isSaving: Boolean,
    onSelect: (InstrumentStatus) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Change instrument status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only active instruments appear in standard lists and instrument pickers.")
                InstrumentStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSaving) { onSelect(status) },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == status,
                            onClick = { onSelect(status) },
                            enabled = !isSaving
                        )
                        Column {
                            Text(status.label)
                            Text(
                                instrumentStatusDescription(status),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSaving && selected != null && selected != current
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Update")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

private fun instrumentStatusDescription(status: InstrumentStatus): String = when (status) {
    InstrumentStatus.Active -> "Available for new datasets and shown in standard lists"
    InstrumentStatus.Maintenance -> "Temporarily unavailable in standard active-instrument lists"
    InstrumentStatus.Decommissioned -> "Retired and removed from active-instrument lists"
}

@Composable
private fun InstrumentInfoCard(instrument: Instrument, onUserClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = AppIcons.Instrument, label = "Name", value = instrument.instrumentName ?: "—")
            InfoRow(icon = AppIcons.Category, label = "Type", value = instrument.instrumentType ?: "—")
            InfoRow(icon = AppIcons.Factory, label = "Manufacturer", value = instrument.manufacturer ?: "—")
            InfoRow(icon = AppIcons.Straighten, label = "Model", value = instrument.model ?: "—")
            InfoRow(icon = AppIcons.Place, label = "Location", value = instrument.location ?: "—")
            val owner = instrument.owner
            val ownerIdentifier = owner?.username ?: owner?.uniqueId ?: instrument.ownerOrcid
            val ownerDisplay = owner?.let { userDisplayName(it) } ?: instrument.ownerOrcid ?: "Not available"
            if (ownerIdentifier != null) {
                ClickableInfoRow(icon = AppIcons.Person, label = "Owner", value = ownerDisplay, onClick = { onUserClick(ownerIdentifier) })
            } else {
                InfoRow(icon = AppIcons.Person, label = "Owner", value = ownerDisplay)
            }
            if (!instrument.description.isNullOrBlank()) {
                InfoRow(icon = AppIcons.Notes, label = "Description", value = instrument.description)
            }
            if (!instrument.otherId.isNullOrBlank()) {
                InfoRow(icon = AppIcons.Tag, label = "External ID", value = instrument.otherId + (instrument.otherIdSource?.let { " ($it)" } ?: ""))
            }
            InfoRow(icon = AppIcons.Tag, label = "Instrument ID", value = instrument.instrumentId ?: "Not available")
            InfoRow(icon = AppIcons.Tag, label = "MFID", value = instrument.uniqueId)
            InfoRow(icon = AppIcons.Category, label = "Status", value = instrument.status?.replaceFirstChar { it.uppercase() } ?: "Not available")
        }
    }
}

@Composable
private fun InstrumentEditCard(
    draft: InstrumentEditState.Editing,
    isSaving: Boolean,
    saveError: String?,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onManufacturerChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onOtherIdChanged: (String) -> Unit,
    onOtherIdSourceChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Instrument", style = MaterialTheme.typography.titleMedium)

            if (saveError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(saveError, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(draft.name, onNameChanged, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.type, onTypeChanged, label = { Text("Type") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.manufacturer, onManufacturerChanged, label = { Text("Manufacturer") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.model, onModelChanged, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.location, onLocationChanged, label = { Text("Location *") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.description, onDescriptionChanged, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, minLines = 2, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            OutlinedTextField(draft.otherId, onOtherIdChanged, label = { Text("External ID") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.otherIdSource, onOtherIdSourceChanged, label = { Text("External ID source") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) { Text("Cancel") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isSaving && draft.name.isNotBlank() && draft.location.isNotBlank()) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}
