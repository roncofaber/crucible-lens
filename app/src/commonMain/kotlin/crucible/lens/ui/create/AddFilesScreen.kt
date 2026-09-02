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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.upload.DatasetFileUploadCheckpoint
import crucible.lens.data.upload.DatasetFileAttachment
import crucible.lens.data.upload.DatasetFileUploadRequest
import crucible.lens.data.upload.DatasetFileUploadResult
import crucible.lens.data.upload.DatasetFileUploadStage
import crucible.lens.data.upload.DatasetFileUploader
import crucible.lens.data.upload.summarizeDatasetFileUploads
import crucible.lens.ui.create.FilesHolder
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.platform.CameraPickerResult
import crucible.lens.platform.ImagePickerResult
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openAppSettings
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.platform.rememberImagePicker
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class PendingFileUpload(
    val bytes: ByteArray,
    val asThumbnail: Boolean,
    val filename: String,
    val checkpoint: DatasetFileUploadCheckpoint = DatasetFileUploadCheckpoint()
)

@Composable
fun AddFilesScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    // When non-null: upload mode — files are uploaded immediately on Done
    datasetUuid: String? = null
) {
    val isUploadMode = datasetUuid != null
    var files by remember {
        mutableStateOf<List<PendingFileUpload>>(
            if (isUploadMode) {
                emptyList()
            } else {
                FilesHolder.files.map { file ->
                    PendingFileUpload(file.bytes, file.asThumbnail, file.filename)
                }
            }
        )
    }
    var isUploading by remember { mutableStateOf(false) }
    var cameraIssue by remember { mutableStateOf<CameraPickerResult?>(null) }
    var imageIssue by remember { mutableStateOf<ImagePickerResult.Failure?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val platformContext = getPlatformContext()
    val apiClient = koinInject<ApiClient>()
    val fileUploader = remember(apiClient) { DatasetFileUploader(apiClient) }

    fun pendingCameraFile(bytes: ByteArray): PendingFileUpload {
        val filename = "camera_${Clock.System.now().toEpochMilliseconds()}.jpg"
        return PendingFileUpload(bytes, asThumbnail = true, filename = filename)
    }

    val cameraPicker = rememberCameraPicker { result ->
        when (result) {
            is CameraPickerResult.Success -> files = files + pendingCameraFile(result.bytes)
            CameraPickerResult.Cancelled -> Unit
            else -> cameraIssue = result
        }
    }
    val imagePicker = rememberImagePicker { result ->
        when (result) {
            is ImagePickerResult.Success -> files = files + PendingFileUpload(
                bytes = result.bytes,
                asThumbnail = true,
                filename = result.filename
            )
            ImagePickerResult.Cancelled -> Unit
            is ImagePickerResult.Failure -> imageIssue = result
        }
    }

    fun onDoneClicked() {
        if (isUploadMode) {
            if (files.isEmpty()) { onDone(); return }
            scope.launch {
                isUploading = true
                try {
                    val results = mutableListOf<DatasetFileUploadResult>()
                    val failedFiles = mutableListOf<PendingFileUpload>()
                    files.forEach { file ->
                        val result = try {
                            fileUploader.upload(
                                DatasetFileUploadRequest(
                                    datasetUuid = datasetUuid,
                                    filename = file.filename,
                                    bytes = file.bytes,
                                    sha256 = PlatformCrypto.sha256Hex(file.bytes),
                                    asThumbnail = file.asThumbnail,
                                    thumbnailBase64 = if (file.asThumbnail) PlatformBase64.encode(file.bytes) else "",
                                    checkpoint = file.checkpoint
                                )
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            DatasetFileUploadResult.Failure(
                                stage = DatasetFileUploadStage.Transfer,
                                message = e.message ?: "Unexpected upload error",
                                checkpoint = file.checkpoint
                            )
                        }
                        results += result
                        if (result is DatasetFileUploadResult.Failure) {
                            failedFiles += file.copy(checkpoint = result.checkpoint)
                        }
                    }
                    val summary = summarizeDatasetFileUploads(results)
                    files = failedFiles
                    snackbarHostState.showSnackbar(summary.userMessage())
                    if (summary.allSucceeded) onDone()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("Upload failed - check your connection")
                } finally {
                    isUploading = false
                }
            }
        } else {
            FilesHolder.files = files.map {
                DatasetFileAttachment(bytes = it.bytes, filename = it.filename, asThumbnail = it.asThumbnail)
            }
            FilesHolder.isDirty = true
            onDone()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = if (isUploadMode) "Add files" else "Attach files",
                onBack = onBack,
                actions = {
                    TextButton(onClick = ::onDoneClicked, enabled = !isUploading) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Done")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(AppIcons.AttachFile, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No files added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                files.forEachIndexed { index, file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = file.bytes,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.small)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(file.filename, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = file.asThumbnail,
                                        onCheckedChange = { checked ->
                                            files = files.toMutableList().also { it[index] = file.copy(asThumbnail = checked) }
                                        },
                                        modifier = Modifier.height(24.dp)
                                    )
                                    Column {
                                        Text("Set as thumbnail", style = MaterialTheme.typography.bodySmall)
                                        Text("Show as preview in app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            IconButton(
                                onClick = { files = files.toMutableList().also { it.removeAt(index) } },
                                modifier = Modifier.size(36.dp),
                                enabled = !isUploading
                            ) {
                                AppIcon(AppIcons.ClearInput, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cameraPicker() }, modifier = Modifier.weight(1f), enabled = !isUploading) {
                    AppIcon(AppIcons.TakePhoto, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { imagePicker() }, modifier = Modifier.weight(1f), enabled = !isUploading) {
                    AppIcon(AppIcons.AttachFile, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose image")
                }
            }
        }
    }

    cameraIssue?.let { issue ->
        val title = when (issue) {
            is CameraPickerResult.PermissionDenied -> "Camera access needed"
            CameraPickerResult.Unavailable -> "Camera unavailable"
            is CameraPickerResult.Failure -> "Camera failed"
            else -> "Camera"
        }
        val message = when (issue) {
            is CameraPickerResult.PermissionDenied -> if (issue.requiresSettings) {
                "Camera access is disabled. Enable it in system settings to take photos."
            } else {
                "Allow camera access to take photos for this dataset."
            }
            CameraPickerResult.Unavailable -> "No camera is available on this device. You can attach an existing image instead."
            is CameraPickerResult.Failure -> issue.message
            else -> "The camera could not be used."
        }
        val actionLabel = when (issue) {
            is CameraPickerResult.PermissionDenied -> if (issue.requiresSettings) "Open settings" else "Allow camera"
            CameraPickerResult.Unavailable -> "OK"
            is CameraPickerResult.Failure -> "Retry"
            else -> "OK"
        }
        AlertDialog(
            onDismissRequest = { cameraIssue = null },
            icon = { AppIcon(AppIcons.TakePhoto) },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cameraIssue = null
                        when (issue) {
                            is CameraPickerResult.PermissionDenied -> {
                                if (issue.requiresSettings) openAppSettings(platformContext) else cameraPicker()
                            }
                            is CameraPickerResult.Failure -> cameraPicker()
                            else -> Unit
                        }
                    }
                ) {
                    Text(actionLabel)
                }
            },
            dismissButton = if (issue is CameraPickerResult.PermissionDenied || issue is CameraPickerResult.Failure) {
                { TextButton(onClick = { cameraIssue = null }) { Text("Cancel") } }
            } else {
                null
            }
        )
    }

    imageIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { imageIssue = null },
            icon = { AppIcon(AppIcons.AttachFile) },
            title = { Text("Image unavailable") },
            text = { Text(issue.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        imageIssue = null
                        imagePicker()
                    }
                ) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { imageIssue = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
