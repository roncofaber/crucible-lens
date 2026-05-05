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
import crucible.lens.data.util.FilesHolder
import crucible.lens.platform.rememberCameraPicker
import crucible.lens.platform.rememberGalleryPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilesScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var files by remember { mutableStateOf(FilesHolder.files) }

    val cameraPicker = rememberCameraPicker { bytes ->
        if (bytes != null) files = files + Pair(bytes, true)
    }
    val galleryPicker = rememberGalleryPicker { bytes ->
        if (bytes != null) files = files + Pair(bytes, true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attach files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        FilesHolder.files = files
                        FilesHolder.isDirty = true
                        onDone()
                    }) {
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
                        Text("No files attached yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { cameraPicker() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
                OutlinedButton(onClick = { galleryPicker() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach file")
                }
            }
        }
    }
}
