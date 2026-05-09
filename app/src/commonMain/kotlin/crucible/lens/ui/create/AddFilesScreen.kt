package crucible.lens.ui.create

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.serialization.json.jsonPrimitive
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.ui.create.FilesHolder
import crucible.lens.data.util.PlatformCrypto
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.platform.rememberGalleryPicker
import kotlin.time.Clock
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilesScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    // When non-null: upload mode — files are uploaded immediately on Done
    datasetUuid: String? = null
) {
    val isUploadMode = datasetUuid != null
    var files by remember { mutableStateOf(if (isUploadMode) emptyList() else FilesHolder.files) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPicker = rememberCameraPicker { bytes ->
        if (bytes != null) files = files + Pair(bytes, true)
    }
    val galleryPicker = rememberGalleryPicker { bytes ->
        if (bytes != null) files = files + Pair(bytes, true)
    }

    fun onDoneClicked() {
        if (isUploadMode) {
            if (files.isEmpty()) { onDone(); return }
            scope.launch {
                isUploading = true
                try {
                    files.forEachIndexed { index, (bytes, asThumbnail) ->
                        val filename = "file_${datasetUuid}_${Clock.System.now().toEpochMilliseconds()}_$index.jpg"
                        val sha256 = PlatformCrypto.sha256Hex(bytes)
                        val initiateResp = ApiClient.service.initiateUpload(datasetUuid!!, filename, bytes.size.toLong())
                        if (initiateResp is ApiResult.Success) {
                            val session = initiateResp.data
                            val chunkResp = ApiClient.service.uploadChunksToGCS(
                                resumableUri = session.resumableUri,
                                bytes = bytes,
                                chunkSizeHint = session.chunkSizeHint
                            )
                            if (chunkResp is ApiResult.Success) {
                                val completeResp = ApiClient.service.completeUpload(datasetUuid!!, session.uploadId, sha256)
                                if (completeResp is ApiResult.Success) {
                                    val afid = completeResp.data["id"]?.jsonPrimitive?.content?.toIntOrNull()
                                    if (afid != null) {
                                        ApiClient.service.requestIngestion(datasetUuid, afid)
                                    }
                                }
                            }
                        }
                        if (asThumbnail) {
                            ApiClient.service.addThumbnail(
                                datasetUuid!!,
                                ThumbnailCreateRequest(thumbnailName = filename, thumbnailB64str = PlatformBase64.encode(bytes))
                            )
                        }
                    }
                    snackbarHostState.showSnackbar(if (files.size == 1) "File uploaded" else "${files.size} files uploaded")
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar("Upload failed — check your connection")
                } finally {
                    isUploading = false
                }
                onDone()
            }
        } else {
            FilesHolder.files = files
            FilesHolder.isDirty = true
            onDone()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isUploadMode) "Add files" else "Attach files") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isUploading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = ::onDoneClicked, enabled = !isUploading) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Done", style = MaterialTheme.typography.labelLarge)
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
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No files added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                files.forEachIndexed { index, (bytes, asThumbnail) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = bytes,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("File ${index + 1}", style = MaterialTheme.typography.bodyMedium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Switch(
                                        checked = asThumbnail,
                                        onCheckedChange = { checked ->
                                            files = files.toMutableList().also { it[index] = it[index].copy(second = checked) }
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
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cameraPicker() }, modifier = Modifier.weight(1f), enabled = !isUploading) {
                    Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { galleryPicker() }, modifier = Modifier.weight(1f), enabled = !isUploading) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach file")
                }
            }
        }
    }
}
