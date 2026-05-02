package crucible.lens.ui.metadata

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ExtractMetadataRequest
import crucible.lens.data.model.MetadataImageData
import crucible.lens.data.util.MetadataHolder
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.ui.common.mergeMetadataEntries
import crucible.lens.ui.common.toMetadataEntries
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var entries by remember { mutableStateOf(MetadataHolder.entries) }
    var photoBytesList by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var extractionContext by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cameraPicker = rememberCameraPicker { bytes -> bytes?.let { photoBytesList = photoBytesList + it } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { cameraPicker() }) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Add photo for extraction")
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Metadata")
                        if (entries.isNotEmpty()) {
                            Text(
                                "${entries.count { it.first.isNotBlank() }} field${if (entries.count { it.first.isNotBlank() } != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        MetadataHolder.entries = entries.filter { it.first.isNotBlank() }
                        MetadataHolder.isDirty = true
                        onDone()
                    }) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Photo extraction card — shown as soon as the first photo is added
            if (photoBytesList.isNotEmpty()) {
                item {
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(photoBytesList) { index, bytes ->
                                    Box(modifier = Modifier.size(96.dp)) {
                                        AsyncImage(
                                            model = bytes,
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        IconButton(
                                            onClick = {
                                                photoBytesList = photoBytesList
                                                    .toMutableList()
                                                    .also { it.removeAt(index) }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .align(Alignment.TopEnd)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove page",
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
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { cameraPicker() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = "Add page",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                                }
                            )

                            Button(
                                onClick = {
                                    scope.launch {
                                        isExtracting = true
                                        try {
                                            val images = photoBytesList.map { bytes ->
                                                MetadataImageData(
                                                    data = PlatformBase64.encode(bytes),
                                                    mediaType = "image/jpeg"
                                                )
                                            }
                                            when (val resp = ApiClient.service.extractMetadata(
                                                ExtractMetadataRequest(
                                                    images = images,
                                                    context = extractionContext.trim().ifBlank { null }
                                                )
                                            )) {
                                                is ApiResult.Success -> {
                                                    val extracted = resp.data.toMetadataEntries()
                                                    entries = mergeMetadataEntries(entries, extracted)
                                                    snackbarHostState.showSnackbar("Extracted ${extracted.size} field(s)")
                                                }
                                                is ApiResult.Error ->
                                                    snackbarHostState.showSnackbar("Extraction failed (${resp.code})")
                                            }
                                        } catch (e: Exception) {
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
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Extract metadata from photos")
                                }
                            }
                        }
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DataObject,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                            Text(
                                "No metadata yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "Tap + to add a field, or use the camera button to extract from a notebook photo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            itemsIndexed(entries, key = { index, _ -> index }) { index, (key, value) ->
                MetadataEntryRow(
                    key = key,
                    value = value,
                    onKeyChange = { newKey ->
                        entries = entries.toMutableList().also { it[index] = newKey to value }
                    },
                    onValueChange = { newValue ->
                        entries = entries.toMutableList().also { it[index] = key to newValue }
                    },
                    onDelete = {
                        entries = entries.toMutableList().also { it.removeAt(index) }
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = {
                        entries = entries + ("" to "")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add field")
                }
            }
        }
    }
}

@Composable
private fun MetadataEntryRow(
    key: String,
    value: String,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Field",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            OutlinedTextField(
                value = key,
                onValueChange = onKeyChange,
                label = { Text("Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = {
                    Text(
                        "Use commas for multiple values (e.g. XRD, TEM, SEM)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            )
        }
    }
}
