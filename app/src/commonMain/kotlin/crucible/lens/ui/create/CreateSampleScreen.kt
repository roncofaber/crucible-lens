@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.create
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Project
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.ui.create.DuplicateHolder
import crucible.lens.ui.metadata.MetadataHolder
import crucible.lens.platform.currentIsoDateTime
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.create.CreateSampleViewModel
import crucible.lens.ui.create.SaveState
import kotlinx.serialization.json.JsonObject

@Composable
fun CreateSampleScreen(
    initialProjectId: String?,
    onBack: () -> Unit,
    onCreated: (uuid: String) -> Unit,
    onOpenMetadataEditor: () -> Unit = {}
) {
    val prefill = remember { DuplicateHolder.takeSample() }
    var name by rememberSaveable { mutableStateOf(prefill?.name?.let { "$it (copy)" } ?: "") }
    var type by rememberSaveable { mutableStateOf(prefill?.type ?: "") }
    var description by rememberSaveable { mutableStateOf(prefill?.description ?: "") }
    var timestamp by rememberSaveable { mutableStateOf(prefill?.timestamp ?: currentIsoDateTime()) }
    var selectedProjectId by rememberSaveable { mutableStateOf(prefill?.projectId ?: initialProjectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<JsonObject?>(null) }
    var isPublic by rememberSaveable { mutableStateOf(false) }

    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    val createViewModel: CreateSampleViewModel = viewModel()
    val saveState by createViewModel.saveState.collectAsState()
    val isSaving = saveState is SaveState.Saving
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is SaveState.Success -> { createViewModel.resetState(); onCreated(s.uuid) }
            is SaveState.Error   -> { snackbarHostState.showSnackbar(s.message); createViewModel.resetState() }
            else -> {}
        }
    }

    // Receive metadata back from MetadataEditorScreen
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty) {
            metadata = MetadataHolder.take()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "New Sample",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Section: Basic Info
            Text("Basic Info", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Sample) }
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Category) }
            )

            if (initialProjectId == null) {
                ExposedDropdownMenuBox(
                    expanded = projectDropdownExpanded,
                    onExpandedChange = { projectDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProject?.title ?: selectedProjectId ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                        leadingIcon = { AppIcon(AppIcons.Project) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = projectDropdownExpanded,
                        onDismissRequest = { projectDropdownExpanded = false }
                    ) {
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.title ?: project.projectId) },
                                onClick = {
                                    selectedProjectId = project.projectId
                                    projectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = selectedProject?.title ?: initialProjectId,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Project *") },
                    leadingIcon = { AppIcon(AppIcons.Project) },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                leadingIcon = { AppIcon(AppIcons.Notes) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section: Metadata
            Text("Metadata", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            OutlinedCard(
                onClick = {
                    MetadataHolder.put(metadata)
                    onOpenMetadataEditor()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
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
                                if ((metadata?.size ?: 0) == 0) "No fields added"
                                else "${metadata!!.size} field${if (metadata!!.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    createViewModel.create(
                        SampleCreateRequest(
                            sampleName = name.trim(),
                            sampleType = type.trim().ifBlank { null },
                            description = description.trim().ifBlank { null },
                            projectId = selectedProjectId,
                            timestamp = timestamp.trim().ifBlank { null },
                            public = isPublic
                        ),
                        projectId = selectedProjectId,
                        metadata = metadata
                    )
                },
                enabled = name.isNotBlank() && !selectedProjectId.isNullOrBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    AppIcon(AppIcons.Add, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Sample")
                }
            }
        }
    }
}
