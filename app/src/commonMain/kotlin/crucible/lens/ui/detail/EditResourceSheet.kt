package crucible.lens.ui.detail
import crucible.lens.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import crucible.lens.data.cache.CacheManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.common.InstrumentPickerField
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.toPrettyString
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.CrucibleResource
import crucible.lens.ui.create.EditResourceViewModel
import crucible.lens.ui.create.SaveState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditResourceSheet(
    resource: CrucibleResource,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    // When provided, the metadata section shows a card that navigates to MetadataEditorScreen.
    // The callback receives the current JSON string so it can seed MetadataHolder before navigating.
    onOpenMetadataEditor: ((currentJson: String) -> Unit)? = null,
    // Overrides the resource's scientificMetadata (used when returning from MetadataEditorScreen).
    overrideMetadata: kotlinx.serialization.json.JsonObject? = null
) {
    val editViewModel: EditResourceViewModel = viewModel()
    val saveState by editViewModel.saveState.collectAsState()
    val isSaving = saveState is SaveState.Saving
    val snackbarHostState = remember { SnackbarHostState() }
    val ctx = getPlatformContext()

    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is SaveState.Success -> { editViewModel.resetState(); showToast(ctx, "Saved"); onSaved() }
            is SaveState.Error   -> { snackbarHostState.showSnackbar(s.message); editViewModel.resetState() }
            else -> {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit ${if (resource is Sample) "Sample" else "Dataset"}",
                    style = MaterialTheme.typography.titleLarge
                )

                when (resource) {
                    is Sample -> SampleEditFields(
                        resource = resource,
                        isSaving = isSaving,
                        snackbarHostState = snackbarHostState,
                        viewModel = editViewModel,
                        onOpenMetadataEditor = onOpenMetadataEditor,
                        overrideMetadata = overrideMetadata
                    )
                    is Dataset -> DatasetEditFields(
                        resource = resource,
                        isSaving = isSaving,
                        snackbarHostState = snackbarHostState,
                        viewModel = editViewModel,
                        onOpenMetadataEditor = onOpenMetadataEditor,
                        overrideMetadata = overrideMetadata
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleEditFields(
    resource: Sample,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: ((currentJson: String) -> Unit)? = null,
    overrideMetadata: kotlinx.serialization.json.JsonObject? = null
) {
    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var type by remember { mutableStateOf(resource.sampleType ?: "") }
    var description by remember { mutableStateOf(resource.description ?: "") }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var isPublic by remember { mutableStateOf(resource.isPublic ?: false) }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var metadataJson by remember {
        mutableStateOf(overrideMetadata?.toPrettyString() ?: resource.scientificMetadata?.toPrettyString() ?: "")
    }
    var metadataJsonError by remember { mutableStateOf<String?>(null) }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    // Section: Basic Info
    Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) }
    )
    OutlinedTextField(
        value = type,
        onValueChange = { type = it },
        label = { Text("Type") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) }
    )
    if (projects.isNotEmpty()) {
        ExposedDropdownMenuBox(
            expanded = projectDropdownExpanded,
            onExpandedChange = { projectDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedProject?.title ?: selectedProjectId ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Project") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = projectDropdownExpanded,
                onDismissRequest = { projectDropdownExpanded = false }
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.title ?: project.projectId) },
                        onClick = { selectedProjectId = project.projectId; projectDropdownExpanded = false }
                    )
                }
            }
        }
    }
    DateTimePickerField(
        value = timestamp,
        onValueChange = { timestamp = it },
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Public", style = MaterialTheme.typography.bodyLarge)
            Text("Visible to all users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Section: Description
    Text("Description", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Section: Metadata
    Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    if (onOpenMetadataEditor != null) {
        val fieldCount = runCatching { metadataJson.trim().ifBlank { null }?.parseAsJsonObject()?.size }.getOrNull() ?: 0
        OutlinedCard(onClick = { onOpenMetadataEditor(metadataJson) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DataObject, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Scientific metadata", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (fieldCount == 0) "No fields" else "$fieldCount field${if (fieldCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        OutlinedTextField(
            value = metadataJson,
            onValueChange = { metadataJson = it; metadataJsonError = null },
            label = { Text("Scientific Metadata (JSON)") },
            placeholder = { Text("{\n  \"key\": \"value\"\n}") },
            modifier = Modifier.fillMaxWidth(),
            isError = metadataJsonError != null,
            minLines = 4,
            maxLines = 12,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        if (metadataJsonError != null) {
            Text(metadataJsonError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        val text = metadataJson.trim()
        val parsedMetadata = if (text.isBlank()) {
            kotlinx.serialization.json.JsonObject(emptyMap())
        } else {
            try { text.parseAsJsonObject() } catch (e: Exception) {
                metadataJsonError = e.message?.substringAfter(":")?.trim() ?: "Invalid JSON"
                return@SaveButton
            }
        }
        viewModel.updateSample(
            resource.uniqueId,
            SampleUpdateRequest(
                sampleName = name.trim(),
                sampleType = type.trim().ifBlank { null },
                description = description.trim().ifBlank { null },
                timestamp = timestamp.trim().ifBlank { null },
                projectId = selectedProjectId,
                public = isPublic,
                scientificMetadata = parsedMetadata
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatasetEditFields(
    resource: Dataset,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: ((currentJson: String) -> Unit)? = null,
    overrideMetadata: kotlinx.serialization.json.JsonObject? = null
) {
    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var measurement by remember { mutableStateOf(resource.measurement ?: "") }
    var instrumentName by remember { mutableStateOf(resource.instrumentName ?: "") }
    var sessionName by remember { mutableStateOf(resource.sessionName ?: "") }
    var dataType by remember { mutableStateOf(resource.dataType ?: "") }
    var isPublic by remember { mutableStateOf(resource.isPublic ?: false) }
    var metadataJson by remember {
        mutableStateOf(overrideMetadata?.toPrettyString() ?: resource.scientificMetadata?.toPrettyString() ?: "")
    }
    var metadataJsonError by remember { mutableStateOf<String?>(null) }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    // Section: Basic Info
    Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Dataset, contentDescription = null) }
    )
    OutlinedTextField(
        value = measurement,
        onValueChange = { measurement = it },
        label = { Text("Measurement") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) }
    )
    if (projects.isNotEmpty()) {
        ExposedDropdownMenuBox(
            expanded = projectDropdownExpanded,
            onExpandedChange = { projectDropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedProject?.title ?: selectedProjectId ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Project") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = projectDropdownExpanded,
                onDismissRequest = { projectDropdownExpanded = false }
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.title ?: project.projectId) },
                        onClick = { selectedProjectId = project.projectId; projectDropdownExpanded = false }
                    )
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Section: Scientific Details
    Text("Scientific Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    OutlinedTextField(
        value = sessionName,
        onValueChange = { sessionName = it },
        label = { Text("Session") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) }
    )
    InstrumentPickerField(
        value = instrumentName,
        onValueChange = { instrumentName = it },
        modifier = Modifier.fillMaxWidth()
    )
    DateTimePickerField(
        value = timestamp,
        onValueChange = { timestamp = it },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = dataType,
        onValueChange = { dataType = it },
        label = { Text("Data Type") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Public", style = MaterialTheme.typography.bodyLarge)
            Text("Visible to all users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // Section: Metadata
    Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

    if (onOpenMetadataEditor != null) {
        val fieldCount = runCatching { metadataJson.trim().ifBlank { null }?.parseAsJsonObject()?.size }.getOrNull() ?: 0
        OutlinedCard(onClick = { onOpenMetadataEditor(metadataJson) }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DataObject, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Scientific metadata", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (fieldCount == 0) "No fields" else "$fieldCount field${if (fieldCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        OutlinedTextField(
            value = metadataJson,
            onValueChange = { metadataJson = it; metadataJsonError = null },
            label = { Text("Scientific Metadata (JSON)") },
            placeholder = { Text("{\n  \"key\": \"value\"\n}") },
            modifier = Modifier.fillMaxWidth(),
            isError = metadataJsonError != null,
            minLines = 4,
            maxLines = 12,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        if (metadataJsonError != null) {
            Text(metadataJsonError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        val text = metadataJson.trim()
        val parsedMetadata = if (text.isBlank()) {
            kotlinx.serialization.json.JsonObject(emptyMap())
        } else {
            try { text.parseAsJsonObject() } catch (e: Exception) {
                metadataJsonError = e.message?.substringAfter(":")?.trim() ?: "Invalid JSON"
                return@SaveButton
            }
        }
        viewModel.updateDataset(
            resource.uniqueId,
            DatasetUpdateRequest(
                datasetName = name.trim(),
                measurement = measurement.trim().ifBlank { null },
                instrumentName = instrumentName.trim().ifBlank { null },
                sessionName = sessionName.trim().ifBlank { null },
                timestamp = timestamp.trim().ifBlank { null },
                projectId = selectedProjectId,
                dataType = dataType.trim().ifBlank { null },
                public = isPublic,
                scientificMetadata = parsedMetadata
            )
        )
    }
}


@Composable
private fun SaveButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save")
        }
    }
}
