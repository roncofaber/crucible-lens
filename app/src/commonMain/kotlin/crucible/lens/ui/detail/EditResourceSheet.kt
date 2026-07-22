@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.platform.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.serialization.json.JsonObject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Parses the metadata JSON for saving. Returns:
 *  - null if the text is blank (no metadata → skip the API call)
 *  - null if the text is unchanged from the original (no-op → skip the API call)
 *  - a parsed JsonObject if changed and valid
 * Calls [onError] with a user-facing message if the JSON is malformed (caller should abort save).
 */
private fun resolveMetadata(
    text: String,
    originalJson: String,
    onError: (String) -> Unit
): JsonObject? {
    val trimmed = text.trim()
    return when {
        trimmed.isBlank() -> null
        trimmed == originalJson.trim() -> null
        else -> try {
            trimmed.parseAsJsonObject()
        } catch (e: Exception) {
            onError(e.message?.substringAfter(":")?.trim() ?: "Invalid JSON")
            null
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun EditProjectDropdown(
    projects: List<Project>,
    selectedProjectId: String?,
    onProjectSelected: (String) -> Unit
) {
    if (projects.isEmpty()) return
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedProject?.title ?: selectedProjectId ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Project") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { AppIcon(AppIcons.Project) },
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
            Text("Public", style = MaterialTheme.typography.bodyLarge)
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
    metadataJson: String,
    onMetadataChange: (String) -> Unit,
    metadataJsonError: String?,
    onOpenMetadataEditor: ((String) -> Unit)?
) {
    Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    if (onOpenMetadataEditor != null) {
        val fieldCount = runCatching {
            metadataJson.trim().ifBlank { null }?.parseAsJsonObject()?.size
        }.getOrNull() ?: 0
        OutlinedCard(onClick = { onOpenMetadataEditor(metadataJson) }, modifier = Modifier.fillMaxWidth()) {
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
    } else {
        OutlinedTextField(
            value = metadataJson,
            onValueChange = onMetadataChange,
            label = { Text("Scientific Metadata (JSON)") },
            placeholder = { Text("{\n  \"key\": \"value\"\n}") },
            modifier = Modifier.fillMaxWidth(),
            isError = metadataJsonError != null,
            minLines = 4,
            maxLines = 12,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
        if (metadataJsonError != null) {
            Text(metadataJsonError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ── Sheet ─────────────────────────────────────────────────────────────────────

@Composable
fun EditResourceSheet(
    resource: CrucibleResource,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onOpenMetadataEditor: ((currentJson: String) -> Unit)? = null,
    overrideMetadata: JsonObject? = null
) {
    val editViewModel: EditResourceViewModel = koinViewModel()
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
                    is Sample -> SampleEditFields(resource, isSaving, editViewModel, onOpenMetadataEditor, overrideMetadata)
                    is Dataset -> DatasetEditFields(resource, isSaving, editViewModel, onOpenMetadataEditor, overrideMetadata)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

// ── Field groups ──────────────────────────────────────────────────────────────

@Composable
private fun SampleEditFields(
    resource: Sample,
    isSaving: Boolean,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: ((String) -> Unit)? = null,
    overrideMetadata: JsonObject? = null
) {
    val cacheManager = koinInject<CacheManager>()
    val projects = remember { cacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var type by remember { mutableStateOf(resource.sampleType ?: "") }
    var description by remember { mutableStateOf(resource.description ?: "") }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var isPublic by remember { mutableStateOf(resource.isPublic ?: false) }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    val originalMetadataJson = remember { resource.scientificMetadata?.toPrettyString() ?: "" }
    var metadataJson by remember { mutableStateOf(overrideMetadata?.toPrettyString() ?: originalMetadataJson) }
    var metadataJsonError by remember { mutableStateOf<String?>(null) }

    Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Sample) })
    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Category) })
    EditProjectDropdown(projects, selectedProjectId) { selectedProjectId = it }
    DateTimePickerField(value = timestamp, onValueChange = { timestamp = it }, modifier = Modifier.fillMaxWidth())
    EditVisibilityToggle(isPublic, { isPublic = it }, "Sample")

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("Description", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, leadingIcon = { AppIcon(AppIcons.Notes) })

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    EditMetadataSection(metadataJson, { metadataJson = it; metadataJsonError = null }, metadataJsonError, onOpenMetadataEditor)

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        var parseError = false
        val parsedMetadata = resolveMetadata(metadataJson, originalMetadataJson) {
            metadataJsonError = it; parseError = true
        }
        if (parseError) return@SaveButton
        viewModel.updateSample(
            resource.uniqueId,
            SampleUpdateRequest(
                sampleName = name.trim(),
                sampleType = type.trim().ifBlank { null },
                description = description.trim().ifBlank { null },
                timestamp = timestamp.trim().ifBlank { null },
                projectId = selectedProjectId,
                public = isPublic
            ),
            metadata = parsedMetadata
        )
    }
}

@Composable
private fun DatasetEditFields(
    resource: Dataset,
    isSaving: Boolean,
    viewModel: EditResourceViewModel,
    onOpenMetadataEditor: ((String) -> Unit)? = null,
    overrideMetadata: JsonObject? = null
) {
    val cacheManager = koinInject<CacheManager>()
    val projects = remember { cacheManager.getProjects() ?: emptyList() }
    var name by remember { mutableStateOf(resource.name) }
    var measurement by remember { mutableStateOf(resource.measurement ?: "") }
    var instrumentName by remember { mutableStateOf(resource.instrumentName ?: "") }
    var sessionName by remember { mutableStateOf(resource.sessionName ?: "") }
    var dataType by remember { mutableStateOf(resource.dataType ?: "") }
    var dataFormat by remember { mutableStateOf(resource.dataFormat ?: "") }
    var isPublic by remember { mutableStateOf(resource.isPublic ?: false) }
    var timestamp by remember { mutableStateOf(resource.timestamp ?: "") }
    var selectedProjectId by remember { mutableStateOf(resource.projectId) }
    val originalMetadataJson = remember { resource.scientificMetadata?.toPrettyString() ?: "" }
    var metadataJson by remember { mutableStateOf(overrideMetadata?.toPrettyString() ?: originalMetadataJson) }
    var metadataJsonError by remember { mutableStateOf<String?>(null) }

    Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Dataset) })
    OutlinedTextField(value = measurement, onValueChange = { measurement = it }, label = { Text("Measurement") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Sample) })
    EditProjectDropdown(projects, selectedProjectId) { selectedProjectId = it }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    Text("Scientific Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(value = sessionName, onValueChange = { sessionName = it }, label = { Text("Session") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.Tag) })
    InstrumentPickerField(value = instrumentName, onValueChange = { instrumentName = it }, modifier = Modifier.fillMaxWidth())
    DateTimePickerField(value = timestamp, onValueChange = { timestamp = it }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = dataType, onValueChange = { dataType = it }, label = { Text("Data Type") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.DataType) })
    OutlinedTextField(value = dataFormat, onValueChange = { dataFormat = it }, label = { Text("Data Format") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { AppIcon(AppIcons.FileGeneric) })
    EditVisibilityToggle(isPublic, { isPublic = it }, "Dataset")

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    EditMetadataSection(metadataJson, { metadataJson = it; metadataJsonError = null }, metadataJsonError, onOpenMetadataEditor)

    SaveButton(enabled = name.isNotBlank() && !isSaving, isSaving = isSaving) {
        var parseError = false
        val parsedMetadata = resolveMetadata(metadataJson, originalMetadataJson) {
            metadataJsonError = it; parseError = true
        }
        if (parseError) return@SaveButton
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
                dataFormat = dataFormat.trim().ifBlank { null },
                public = isPublic
            ),
            metadata = parsedMetadata
        )
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
