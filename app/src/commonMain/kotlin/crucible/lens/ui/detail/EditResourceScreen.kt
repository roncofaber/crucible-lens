@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.DiscardChangesDialog
import crucible.lens.ui.common.ConfirmationDialog
import crucible.lens.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.repository.CrucibleRepository
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.MetadataWrite
import crucible.lens.ui.common.diffMetadataWrite
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.toPrettyString
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.ui.create.EditResourceViewModel
import crucible.lens.ui.create.ProjectMoveState
import crucible.lens.ui.create.SaveState
import crucible.lens.ui.metadata.MetadataHolder
import kotlinx.serialization.json.JsonObject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun EditProjectDropdown(
    projects: List<Project>,
    selectedProjectId: String?,
    enabled: Boolean,
    onProjectSelected: (String) -> Unit
) {
    if (projects.isEmpty()) return
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selectedProject?.title ?: selectedProjectId ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Project") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { AppIcon(AppIcons.Project) },
            supportingText = { Text("Changing the project requires confirmation") },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.title ?: project.projectId) },
                    onClick = { onProjectSelected(project.projectId); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EditVisibilityToggle(isPublic: Boolean, onToggle: (Boolean) -> Unit, resourceName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Public", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isPublic) "$resourceName is visible to all users" else "$resourceName is private",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = isPublic, onCheckedChange = onToggle)
    }
}

@Composable
private fun EditMetadataSection(
    metadata: JsonObject?,
    onOpenMetadataEditor: () -> Unit
) {
    Text("Metadata", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    val fieldCount = metadata?.size ?: 0
    OutlinedCard(onClick = onOpenMetadataEditor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.FileJson, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text("Scientific metadata", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (fieldCount == 0) "No fields" else "$fieldCount field${if (fieldCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Full-page equivalent of the old `EditResourceSheet` bottom sheet — converted to a real nav
 * destination (`Screen.EditResource`) so it survives navigating to `MetadataEditorScreen` and
 * back the same way `CreateSampleScreen`/`CreateDatasetScreen` already do: field state is
 * `rememberSaveable` directly (no singleton draft relay needed, unlike the old sheet's
 * `EditDraftHolder`), and metadata comes back via the same `MetadataHolder` pattern Create
 * screens use. See dev/architecture.md's "Common gotchas" for why a screen-local bottom sheet
 * routing out to a full screen and back doesn't survive composition disposal.
 */
@Composable
fun EditResourceScreen(
    uuid: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenMetadataEditor: () -> Unit = {},
    onHome: () -> Unit = {}
) {
    val repository = koinInject<CrucibleRepository>()
    val resource: CrucibleResource? by repository.observeResource(uuid)
        .collectAsStateWithLifecycle(initialValue = repository.getCachedResource(uuid))

    val editViewModel: EditResourceViewModel = koinViewModel()
    val saveState by editViewModel.saveState.collectAsStateWithLifecycle()
    val projectMoveState by editViewModel.projectMoveState.collectAsStateWithLifecycle()
    val isSaving = saveState is SaveState.Saving
    val isMovingProject = projectMoveState is ProjectMoveState.Previewing || projectMoveState is ProjectMoveState.Moving
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = getPlatformContext()

    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is SaveState.Success -> { editViewModel.resetState(); showToast(ctx, "Saved"); onSaved() }
            is SaveState.Error   -> { snackbarHostState.showSnackbar(s.message); editViewModel.resetState() }
            else -> {}
        }
    }

    LaunchedEffect(projectMoveState) {
        when (val state = projectMoveState) {
            is ProjectMoveState.Success -> {
                showToast(ctx, "Moved to ${state.result.newProjectId}")
                editViewModel.resetProjectMoveState()
            }
            is ProjectMoveState.Error -> {
                editViewModel.resetProjectMoveState()
                snackbarHostState.showSnackbar(state.message)
            }
            else -> Unit
        }
    }

    val movePreview = when (val state = projectMoveState) {
        is ProjectMoveState.PreviewReady -> state.preview
        is ProjectMoveState.Moving -> state.preview
        else -> null
    }
    if (movePreview != null) {
        val resourceName = if (resource is Dataset) "dataset" else "sample"
        ConfirmationDialog(
            title = "Move this $resourceName?",
            text = "Move from ${movePreview.previousProjectId ?: "no project"} to ${movePreview.newProjectId}? Project access will change with it.",
            confirmLabel = "Move",
            onConfirm = { editViewModel.confirmProjectMove(uuid) },
            onDismiss = editViewModel::resetProjectMoveState,
            icon = AppIcons.Project,
            confirmEnabled = projectMoveState is ProjectMoveState.PreviewReady
        )
    }

    // Reported by SampleEditFields/DatasetEditFields (whichever is showing) by comparing their
    // current field values against the original resource - unlike Create screens, every field
    // here starts pre-filled, so "dirty" has to mean "differs from the original," not "non-blank."
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    pendingNavigation?.let { action ->
        DiscardChangesDialog(
            onConfirm = { pendingNavigation = null; action() },
            onDismiss = { pendingNavigation = null }
        )
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Edit ${if (resource is Dataset) "Dataset" else "Sample"}",
                onBack = { if (hasUnsavedChanges) pendingNavigation = onBack else onBack() },
                actions = {
                    IconButton(onClick = { if (hasUnsavedChanges) pendingNavigation = onHome else onHome() }) {
                        AppIcon(AppIcons.Home)
                    }
                }
            )
        }
    ) { padding ->
        when (val r = resource) {
            null -> LoadingContent(title = "Loading", modifier = Modifier.padding(padding))
            is Sample -> SampleEditFields(r, isSaving, isMovingProject, editViewModel, onOpenMetadataEditor, padding, onDirtyChanged = { hasUnsavedChanges = it })
            is Dataset -> DatasetEditFields(r, isSaving, isMovingProject, editViewModel, onOpenMetadataEditor, padding, onDirtyChanged = { hasUnsavedChanges = it })
        }
    }
}

// ── Field groups ──────────────────────────────────────────────────────────────

@Composable
private fun SampleEditFields(
    resource: Sample,
    isSaving: Boolean,
    isMovingProject: Boolean,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: () -> Unit,
    padding: PaddingValues,
    onDirtyChanged: (Boolean) -> Unit = {}
) {
    val repository = koinInject<CrucibleRepository>()
    val projects = remember { repository.getCachedProjects() ?: emptyList() }
    var name by rememberSaveable { mutableStateOf(resource.name) }
    var type by rememberSaveable { mutableStateOf(resource.sampleType ?: "") }
    var description by rememberSaveable { mutableStateOf(resource.description ?: "") }
    var timestamp by rememberSaveable { mutableStateOf(resource.timestamp ?: "") }
    var isPublic by rememberSaveable { mutableStateOf(resource.isPublic ?: false) }
    val originalMetadata = remember { resource.scientificMetadata ?: JsonObject(emptyMap()) }
    val initialMetadata = remember { if (originalMetadata.isEmpty()) null else originalMetadata }
    // Not rememberSaveable — JsonObject has no default Saver — but this is safe: MetadataHolder
    // itself (a plain singleton, same pattern CreateSampleScreen already uses) is what survives
    // navigating to MetadataEditorScreen and back, not this local var.
    var metadata by remember { mutableStateOf<JsonObject?>(initialMetadata) }

    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty) metadata = MetadataHolder.take()
    }

    SideEffect {
        onDirtyChanged(
            name != resource.name ||
                type != (resource.sampleType ?: "") ||
                description != (resource.description ?: "") ||
                timestamp != (resource.timestamp ?: "") ||
                isPublic != (resource.isPublic ?: false) ||
                metadata != initialMetadata
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Basic Info", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Sample) })
        OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Category) })
        EditProjectDropdown(projects, resource.projectId, enabled = !isSaving && !isMovingProject) { projectId ->
            if (projectId != resource.projectId) viewModel.previewProjectMove(resource.uniqueId, projectId)
        }
        DateTimePickerField(value = timestamp, onValueChange = { timestamp = it }, modifier = Modifier.fillMaxWidth())
        EditVisibilityToggle(isPublic, { isPublic = it }, "Sample")

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, leadingIcon = { AppIcon(AppIcons.Notes) })

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        EditMetadataSection(metadata) {
            MetadataHolder.put(metadata)
            onOpenMetadataEditor()
        }

        SaveButton(enabled = name.isNotBlank() && !isSaving && !isMovingProject, isSaving = isSaving) {
            val metadataWrite = diffMetadataWrite(originalMetadata, metadata ?: JsonObject(emptyMap()))
            viewModel.updateSample(
                resource.uniqueId,
                SampleUpdateRequest(
                    sampleName = name.trim(),
                    sampleType = type.trim().ifBlank { null },
                    description = description.trim().ifBlank { null },
                    timestamp = timestamp.trim().ifBlank { null },
                    public = isPublic
                ),
                metadataWrite = metadataWrite
            )
        }
    }
}

@Composable
private fun DatasetEditFields(
    resource: Dataset,
    isSaving: Boolean,
    isMovingProject: Boolean,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: () -> Unit,
    padding: PaddingValues,
    onDirtyChanged: (Boolean) -> Unit = {}
) {
    val repository = koinInject<CrucibleRepository>()
    val projects = remember { repository.getCachedProjects() ?: emptyList() }
    var name by rememberSaveable { mutableStateOf(resource.name) }
    var measurement by rememberSaveable { mutableStateOf(resource.measurement ?: "") }
    var sessionName by rememberSaveable { mutableStateOf(resource.sessionName ?: "") }
    var dataType by rememberSaveable { mutableStateOf(resource.dataType ?: "") }
    var dataFormat by rememberSaveable { mutableStateOf(resource.dataFormat ?: "") }
    var isPublic by rememberSaveable { mutableStateOf(resource.isPublic ?: false) }
    var timestamp by rememberSaveable { mutableStateOf(resource.timestamp ?: "") }
    val originalMetadata = remember { resource.scientificMetadata ?: JsonObject(emptyMap()) }
    val initialMetadata = remember { if (originalMetadata.isEmpty()) null else originalMetadata }
    var metadata by remember { mutableStateOf<JsonObject?>(initialMetadata) }

    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty) metadata = MetadataHolder.take()
    }

    SideEffect {
        onDirtyChanged(
            name != resource.name ||
                measurement != (resource.measurement ?: "") ||
                sessionName != (resource.sessionName ?: "") ||
                dataType != (resource.dataType ?: "") ||
                dataFormat != (resource.dataFormat ?: "") ||
                isPublic != (resource.isPublic ?: false) ||
                timestamp != (resource.timestamp ?: "") ||
                metadata != initialMetadata
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Basic Info", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Dataset) })
        OutlinedTextField(value = measurement, onValueChange = { measurement = it }, label = { Text("Measurement") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Sample) })
        EditProjectDropdown(projects, resource.projectId, enabled = !isSaving && !isMovingProject) { projectId ->
            if (projectId != resource.projectId) viewModel.previewProjectMove(resource.uniqueId, projectId)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Scientific Details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(value = sessionName, onValueChange = { sessionName = it }, label = { Text("Session") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Tag) })
        OutlinedTextField(
            value = resource.instrumentName ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Instrument") },
            leadingIcon = { AppIcon(AppIcons.Instrument) },
            supportingText = { Text("Instrument reassignment is not currently supported") },
            modifier = Modifier.fillMaxWidth()
        )
        DateTimePickerField(value = timestamp, onValueChange = { timestamp = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dataType, onValueChange = { dataType = it }, label = { Text("Data Type") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.DataType) })
        OutlinedTextField(value = dataFormat, onValueChange = { dataFormat = it }, label = { Text("Data Format") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.FileGeneric) })
        EditVisibilityToggle(isPublic, { isPublic = it }, "Dataset")

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        EditMetadataSection(metadata) {
            MetadataHolder.put(metadata)
            onOpenMetadataEditor()
        }

        SaveButton(enabled = name.isNotBlank() && !isSaving && !isMovingProject, isSaving = isSaving) {
            val metadataWrite = diffMetadataWrite(originalMetadata, metadata ?: JsonObject(emptyMap()))
            viewModel.updateDataset(
                resource.uniqueId,
                DatasetUpdateRequest(
                    datasetName = name.trim(),
                    measurement = measurement.trim().ifBlank { null },
                    sessionName = sessionName.trim().ifBlank { null },
                    timestamp = timestamp.trim().ifBlank { null },
                    dataType = dataType.trim().ifBlank { null },
                    dataFormat = dataFormat.trim().ifBlank { null },
                    public = isPublic
                ),
                metadataWrite = metadataWrite
            )
        }
    }
}

// ── Save button ───────────────────────────────────────────────────────────────

@Composable
private fun SaveButton(enabled: Boolean, isSaving: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            AppIcon(AppIcons.Save, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save")
        }
    }
}
