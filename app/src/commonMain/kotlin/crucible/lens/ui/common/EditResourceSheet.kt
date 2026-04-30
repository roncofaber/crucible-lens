package crucible.lens.ui.common
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
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult

import crucible.lens.data.cache.CacheManager
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.common.InstrumentPickerField
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.CrucibleResource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditResourceSheet(
    resource: CrucibleResource,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSaving by remember { mutableStateOf(false) }

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
                        onSaved = onSaved,
                        onSavingChange = { isSaving = it },
                        scope = scope
                    )
                    is Dataset -> DatasetEditFields(
                        resource = resource,
                        isSaving = isSaving,
                        snackbarHostState = snackbarHostState,
                        onSaved = onSaved,
                        onSavingChange = { isSaving = it },
                        scope = scope
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
    onSaved: () -> Unit,
    onSavingChange: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val ctx = getPlatformContext()
    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var type by remember { mutableStateOf(resource.sampleType ?: "") }
    var description by remember { mutableStateOf(resource.description ?: "") }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

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
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Description") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
    )

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        scope.launch {
            onSavingChange(true)
            try {
                when (val resp = ApiClient.service.updateSample(
                    resource.uniqueId,
                    SampleUpdateRequest(
                        sampleName = name.trim(),
                        sampleType = type.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        timestamp = timestamp.trim().ifBlank { null },
                        projectId = selectedProjectId
                    )
                )) {
                    is crucible.lens.data.api.ApiResult.Success -> {
                        showToast(ctx, "Saved")
                        onSaved()
                    }
                    is crucible.lens.data.api.ApiResult.Error -> {
                        snackbarHostState.showSnackbar("Save failed (${resp.code})")
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Connection error — check your network")
            } finally {
                onSavingChange(false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatasetEditFields(
    resource: Dataset,
    isSaving: Boolean,
    snackbarHostState: SnackbarHostState,
    onSaved: () -> Unit,
    onSavingChange: (Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val ctx = getPlatformContext()
    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var measurement by remember { mutableStateOf(resource.measurement ?: "") }
    var instrumentName by remember { mutableStateOf(resource.instrumentName ?: "") }
    var sessionName by remember { mutableStateOf(resource.sessionName ?: "") }
    var dataType by remember { mutableStateOf(resource.dataType ?: "") }
    var metadataText by remember {
        mutableStateOf(
            resource.scientificMetadata?.entries?.joinToString("\n") { (k, v) -> "$k: $v" } ?: ""
        )
    }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

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
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
    OutlinedTextField(
        value = sessionName,
        onValueChange = { sessionName = it },
        label = { Text("Session") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) }
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
    OutlinedTextField(
        value = metadataText,
        onValueChange = { metadataText = it },
        label = { Text("Metadata") },
        placeholder = { Text("key: value\nkey2: value2", style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 6,
        leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null) }
    )

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        scope.launch {
            onSavingChange(true)
            try {
                when (val resp = ApiClient.service.updateDataset(
                    resource.uniqueId,
                    DatasetUpdateRequest(
                        datasetName = name.trim(),
                        measurement = measurement.trim().ifBlank { null },
                        instrumentName = instrumentName.trim().ifBlank { null },
                        sessionName = sessionName.trim().ifBlank { null },
                        timestamp = timestamp.trim().ifBlank { null },
                        projectId = selectedProjectId,
                        dataType = dataType.trim().ifBlank { null },
                        scientificMetadata = parseMetadataText(metadataText)
                    )
                )) {
                    is crucible.lens.data.api.ApiResult.Success -> {
                        showToast(ctx, "Saved")
                        onSaved()
                    }
                    is crucible.lens.data.api.ApiResult.Error -> {
                        snackbarHostState.showSnackbar("Save failed (${resp.code})")
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Connection error — check your network")
            } finally {
                onSavingChange(false)
            }
        }
    }
}

private fun parseMetadataText(text: String): Map<String, String>? {
    if (text.isBlank()) return null
    return text.trim().lines()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx < 0) return@mapNotNull null
            val key = line.substring(0, idx).trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to line.substring(idx + 1).trim()
        }
        .toMap()
        .ifEmpty { null }
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
