package crucible.lens.ui.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Project
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.util.DuplicateHolder
import crucible.lens.data.util.MetadataHolder
import crucible.lens.platform.currentIsoDateTime
import crucible.lens.ui.common.DateTimePickerField
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Receive metadata back from MetadataEditorScreen
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty) {
            metadata = MetadataHolder.take()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Sample") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(12.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section: Basic Info
            Text("Basic Info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) }
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) }
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
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        textStyle = MaterialTheme.typography.bodyMedium,
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
                    label = { Text("Project") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }

            DateTimePickerField(
                value = timestamp,
                onValueChange = { timestamp = it },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section: Description
            Text("Description", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section: Metadata
            Text("Metadata", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedCard(
                onClick = {
                    val ctx = listOfNotNull(
                        name.trim().ifBlank { null }?.let { "Sample: $it" },
                        type.trim().ifBlank { null }?.let { "Type: $it" },
                        description.trim().ifBlank { null }?.let { "Description: $it" },
                        selectedProject?.title?.let { "Project: $it" }
                    ).joinToString(", ")
                    MetadataHolder.put(metadata, ctx)
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
                        Icon(Icons.Default.DataObject, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            when (val resp = ApiClient.service.createSample(
                                SampleCreateRequest(
                                    sampleName = name.trim(),
                                    sampleType = type.trim().ifBlank { null },
                                    description = description.trim().ifBlank { null },
                                    projectId = selectedProjectId,
                                    timestamp = timestamp.trim().ifBlank { null },
                                    scientificMetadata = metadata
                                )
                            )) {
                                is ApiResult.Success -> {
                                    val sample = resp.data
                                    val uuid = sample.uniqueId
                                    CacheManager.cacheResource(uuid, sample)
                                    // Invalidate project list so the new sample appears immediately
                                    selectedProjectId?.let { CacheManager.clearProjectDetail(it) }
                                    onCreated(uuid)
                                }
                                is ApiResult.Error -> {
                                    snackbarHostState.showSnackbar("Save failed (${resp.code})")
                                }
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Connection error — check your network")
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Sample")
                }
            }
        }
    }
}
