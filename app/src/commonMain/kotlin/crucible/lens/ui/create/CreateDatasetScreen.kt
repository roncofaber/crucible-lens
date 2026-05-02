package crucible.lens.ui.create

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.DuplicateHolder
import crucible.lens.data.util.MetadataHolder
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.currentIsoDateTime
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.platform.rememberGalleryPicker
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.common.InstrumentPickerField
import crucible.lens.ui.common.MetadataEditor
import crucible.lens.ui.common.toMetadataMap
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDatasetScreen(
    initialProjectId: String?,
    onBack: () -> Unit,
    onCreated: (uuid: String) -> Unit,
    onOpenMetadataEditor: () -> Unit = {}
) {
    val prefill = remember { DuplicateHolder.takeDataset() }
    var name by rememberSaveable { mutableStateOf(prefill?.name?.let { "$it (copy)" } ?: "") }
    var measurement by rememberSaveable { mutableStateOf(prefill?.measurement ?: "") }
    var instrumentName by rememberSaveable { mutableStateOf(prefill?.instrumentName ?: "") }
    var sessionName by rememberSaveable { mutableStateOf(prefill?.sessionName ?: "") }
    var dataType by rememberSaveable { mutableStateOf("") }
    var metadataEntries by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var timestamp by rememberSaveable { mutableStateOf(prefill?.timestamp ?: currentIsoDateTime()) }
    var selectedProjectId by rememberSaveable { mutableStateOf(prefill?.projectId ?: initialProjectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val cameraPicker = rememberCameraPicker { bytes -> photoBytes = bytes }
    val galleryPicker = rememberGalleryPicker { bytes -> photoBytes = bytes }

    // Receive metadata back from MetadataEditorScreen
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty) {
            metadataEntries = MetadataHolder.take()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Dataset") },
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
            // Photo capture area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (photoBytes != null) {
                    AsyncImage(
                        model = photoBytes,
                        contentDescription = "Captured photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(onClick = { galleryPicker() }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Choose from gallery")
                        }
                        FilledTonalIconButton(onClick = { cameraPicker() }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Retake")
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { cameraPicker() }
                        ) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Camera", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { galleryPicker() }
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Gallery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Section: Basic Info
            Text("Basic Info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.Dataset, contentDescription = null) }
            )
            OutlinedTextField(
                value = measurement,
                onValueChange = { measurement = it },
                label = { Text("Measurement") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.Default.Science, contentDescription = null) }
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section: Scientific Details
            Text("Scientific Details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("Session") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
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
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section: Metadata
            Text("Metadata", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp))

            OutlinedCard(
                onClick = {
                    val ctx = listOfNotNull(
                        name.trim().ifBlank { null }?.let { "Dataset: $it" },
                        measurement.trim().ifBlank { null }?.let { "Measurement: $it" },
                        instrumentName.trim().ifBlank { null }?.let { "Instrument: $it" },
                        sessionName.trim().ifBlank { null }?.let { "Session: $it" },
                        selectedProject?.title?.let { "Project: $it" }
                    ).joinToString(", ")
                    MetadataHolder.put(metadataEntries, ctx)
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
                                if (metadataEntries.isEmpty()) "No fields added"
                                else "${metadataEntries.count { it.first.isNotBlank() }} field${if (metadataEntries.count { it.first.isNotBlank() } != 1) "s" else ""}",
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
                            val createResp = ApiClient.service.createDataset(
                                DatasetCreateRequest(
                                    datasetName = name.trim(),
                                    projectId = selectedProjectId,
                                    measurement = measurement.trim().ifBlank { null },
                                    instrumentName = instrumentName.trim().ifBlank { null },
                                    sessionName = sessionName.trim().ifBlank { null },
                                    timestamp = timestamp.trim().ifBlank { null },
                                    dataType = dataType.trim().ifBlank { null },
                                    scientificMetadata = metadataEntries.toMetadataMap()
                                )
                            )
                            if (createResp !is ApiResult.Success) {
                                val code = (createResp as? ApiResult.Error)?.code ?: -1
                                snackbarHostState.showSnackbar("Could not create dataset ($code)")
                                return@launch
                            }
                            val newDataset = createResp.data
                            val newUuid = newDataset.uniqueId
                            CacheManager.cacheResource(newUuid, newDataset)
                            selectedProjectId?.let { CacheManager.clearProjectDetail(it) }

                            val bytes = photoBytes
                            var thumbnailFailed = false
                            if (bytes != null) {
                                val b64 = PlatformBase64.encode(bytes)
                                val thumbResp = ApiClient.service.addThumbnail(
                                    newUuid,
                                    ThumbnailCreateRequest(
                                        thumbnailName = "photo.jpg",
                                        thumbnailB64str = b64
                                    )
                                )
                                if (thumbResp !is ApiResult.Success) thumbnailFailed = true
                            }

                            if (thumbnailFailed) {
                                snackbarHostState.showSnackbar("Dataset created, but thumbnail could not be attached")
                            }
                            onCreated(newUuid)
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
                    Text("Create Dataset")
                }
            }
        }
    }
}
