package crucible.lens.ui.create
import crucible.lens.platform.*


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale




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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.util.DuplicateHolder
import crucible.lens.platform.currentIsoDateTime
import crucible.lens.ui.common.DateTimePickerField
import crucible.lens.ui.common.InstrumentPickerField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun CreateDatasetScreen(
    initialProjectId: String?,
    onBack: () -> Unit,
    onCreated: (uuid: String) -> Unit
) {
    val context = LocalContext.current

    val prefill = remember { DuplicateHolder.takeDataset() }
    var name by remember { mutableStateOf(prefill?.name?.let { "$it (copy)" } ?: "") }
    var measurement by remember { mutableStateOf(prefill?.measurement ?: "") }
    var instrumentName by remember { mutableStateOf(prefill?.instrumentName ?: "") }
    var sessionName by remember { mutableStateOf(prefill?.sessionName ?: "") }
    var dataType by remember { mutableStateOf("") }
    var metadataText by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(prefill?.timestamp ?: currentIsoDateTime()) }
    var selectedProjectId by remember { mutableStateOf(prefill?.projectId ?: initialProjectId) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var tempFileUri by remember { mutableStateOf<Uri?>(null) }

    val projects: List<Project> = remember { CacheManager.getProjects() ?: emptyList() }
    val selectedProject = projects.firstOrNull { it.projectId == selectedProjectId }

    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoUri = tempFileUri
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        tempFileUri = uri
        cameraLauncher.launch(uri)
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
                    )
                    .clickable { launchCamera() },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = "Captured photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Retake overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        FilledTonalIconButton(onClick = { launchCamera() }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Retake")
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap to take a photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                    modifier = Modifier.fillMaxWidth()
                )
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

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            // 1. Create dataset record
                            val createResp = ApiClient.service.createDataset(
                                DatasetCreateRequest(
                                    datasetName = name.trim(),
                                    projectId = selectedProjectId,
                                    measurement = measurement.trim().ifBlank { null },
                                    instrumentName = instrumentName.trim().ifBlank { null },
                                    sessionName = sessionName.trim().ifBlank { null },
                                    timestamp = timestamp.trim().ifBlank { null },
                                    dataType = dataType.trim().ifBlank { null },
                                    scientificMetadata = parseMetadataText(metadataText)
                                )
                            )
                            if (createResp !is ApiResult.Success) {
                                val code = (createResp as? ApiResult.Error)?.code ?: -1
                                snackbarHostState.showSnackbar("Could not create dataset ($code)")
                                return@launch
                            }
                            val newDataset = createResp.data
                            val newUuid = newDataset.uniqueId
                                ?: run {
                                    snackbarHostState.showSnackbar("Created — couldn't retrieve the ID")
                                    return@launch
                                }
                            CacheManager.cacheResource(newUuid, newDataset)
                            // Invalidate project list so the new dataset appears immediately
                            selectedProjectId?.let { CacheManager.clearProjectDetail(it) }

                            // 2. Upload photo as thumbnail if captured
                            val capturedUri = photoUri
                            var thumbnailFailed = false
                            if (capturedUri != null) {
                                val b64 = withContext(Dispatchers.IO) {
                                    encodePhotoToBase64(context, capturedUri)
                                }
                                if (b64 != null) {
                                    val thumbResp = withContext(Dispatchers.IO) {
                                        ApiClient.service.addThumbnail(
                                            newUuid,
                                            ThumbnailCreateRequest(
                                                thumbnailName = "photo.jpg",
                                                thumbnailB64str = b64
                                            )
                                        )
                                    }
                                    if (thumbResp !is ApiResult.Success) thumbnailFailed = true
                                } else {
                                    thumbnailFailed = true
                                }
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

private fun encodePhotoToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // Scale down so the longest edge is at most 1024 px
        val maxDim = 1024
        val scaled = if (original.width > maxDim || original.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
            original.scale(
                (original.width * ratio).toInt(),
                (original.height * ratio).toInt(),
                true
            )
        } else original

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
