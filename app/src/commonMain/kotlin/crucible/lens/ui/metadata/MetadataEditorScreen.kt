package crucible.lens.ui.metadata

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ExtractMetadataRequest
import crucible.lens.data.model.MetadataImageData
import crucible.lens.data.util.MetadataHolder
import crucible.lens.platform.PlatformBase64
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.toPrettyString
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.platform.rememberGalleryPicker
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val initial = MetadataHolder.metadata
    var jsonText by rememberSaveable {
        mutableStateOf(
            if (initial == null || initial.isEmpty()) "" else initial.toPrettyString()
        )
    }
    var parseError by remember { mutableStateOf<String?>(null) }
    var photoBytesList by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var extractionContext by rememberSaveable { mutableStateOf(MetadataHolder.resourceContext) }
    var isExtracting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cameraPicker = rememberCameraPicker { bytes -> bytes?.let { photoBytesList = photoBytesList + it } }
    val galleryPicker = rememberGalleryPicker { bytes -> bytes?.let { photoBytesList = photoBytesList + it } }

    fun tryDone() {
        val text = jsonText.trim()
        if (text.isEmpty()) {
            MetadataHolder.metadata = JsonObject(emptyMap())
            MetadataHolder.isDirty = true
            onDone()
            return
        }
        try {
            MetadataHolder.metadata = text.parseAsJsonObject()
            MetadataHolder.isDirty = true
            parseError = null
            onDone()
        } catch (e: Exception) {
            parseError = e.message?.substringAfter(":")?.trim()?.ifBlank { null }
                ?: "Invalid JSON — check for missing commas, quotes, or brackets"
        }
    }

    fun formatJson() {
        val text = jsonText.trim()
        if (text.isEmpty()) return
        try {
            jsonText = text.parseAsJsonObject().toPrettyString()
            parseError = null
        } catch (e: Exception) {
            parseError = e.message?.substringAfter(":")?.trim()?.ifBlank { null }
                ?: "Cannot format — fix syntax errors first"
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Metadata") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { galleryPicker() }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Add from gallery")
                    }
                    IconButton(onClick = { cameraPicker() }) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Take photo")
                    }
                    IconButton(onClick = ::formatJson) {
                        Icon(Icons.Default.Code, contentDescription = "Format JSON")
                    }
                    TextButton(onClick = ::tryDone) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Photo extraction card
            if (photoBytesList.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Extract from photos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(photoBytesList, key = { index, _ -> index }) { index, bytes ->
                                Box(modifier = Modifier.size(96.dp)) {
                                    AsyncImage(
                                        model = bytes,
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = {
                                            photoBytesList = photoBytesList.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clickable { cameraPicker() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .clickable { galleryPicker() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = extractionContext,
                            onValueChange = { extractionContext = it },
                            label = { Text("Context hint (optional)") },
                            placeholder = { Text("e.g. XRD measurement notebook") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    isExtracting = true
                                    try {
                                        val images = photoBytesList.map { bytes ->
                                            MetadataImageData(
                                                data = PlatformBase64.encode(bytes).trim(),
                                                mediaType = "image/jpeg"
                                            )
                                        }
                                        val ctx = extractionContext.trim().ifBlank { null }
                                        val resp: ApiResult<JsonObject> =
                                            if (ApiClient.aiDirectMode && !ApiClient.aiApiKey.isNullOrBlank()) {
                                                ApiClient.service.extractMetadataDirect(
                                                    images = images,
                                                    context = ctx,
                                                    aiApiKey = ApiClient.aiApiKey!!,
                                                    aiApiUrl = ApiClient.aiApiUrl
                                                )
                                            } else {
                                                ApiClient.service.extractMetadata(
                                                    ExtractMetadataRequest(images = images, context = ctx)
                                                )
                                            }
                                        when (resp) {
                                            is ApiResult.Success -> {
                                                val extracted = resp.data
                                                val current = runCatching {
                                                    jsonText.trim().ifBlank { null }?.parseAsJsonObject()
                                                }.getOrNull() ?: JsonObject(emptyMap())
                                                val merged = buildJsonObject {
                                                    current.forEach { (k, v) -> put(k, v) }
                                                    extracted.forEach { (k, v) -> if (k !in current) put(k, v) }
                                                }
                                                jsonText = merged.toPrettyString()
                                                val newKeys = extracted.keys.count { it !in current }
                                                snackbarHostState.showSnackbar(
                                                    if (newKeys > 0) "Extracted $newKeys new field(s)"
                                                    else "No new fields — all keys already present"
                                                )
                                            }
                                            is ApiResult.Error -> snackbarHostState.showSnackbar(
                                                when (resp.code) {
                                                    422  -> "Could not extract metadata — try a clearer photo"
                                                    503  -> "Extraction service unavailable — try again later"
                                                    -1   -> "Extraction failed: ${resp.message}"
                                                    else -> "Extraction failed (${resp.code})"
                                                }
                                            )
                                        }
                                    } catch (_: Exception) {
                                        snackbarHostState.showSnackbar("Connection error — check your network")
                                    } finally {
                                        isExtracting = false
                                    }
                                }
                            },
                            enabled = !isExtracting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Extract metadata from photos")
                            }
                        }
                    }
                }
            }

            // JSON text editor — takes all remaining space
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it; parseError = null },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("Scientific Metadata (JSON)") },
                placeholder = { Text("{\n  \"key\": \"value\"\n}") },
                isError = parseError != null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            )

            if (parseError != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        parseError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
