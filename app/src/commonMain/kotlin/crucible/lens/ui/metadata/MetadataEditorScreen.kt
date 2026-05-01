package crucible.lens.ui.metadata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.util.MetadataHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var entries by remember { mutableStateOf(MetadataHolder.entries) }
    val listState = rememberLazyListState()

    Scaffold(
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                                "Tap + to add a field",
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
