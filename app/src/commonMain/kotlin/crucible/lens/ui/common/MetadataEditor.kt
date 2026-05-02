package crucible.lens.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Structured key-value metadata editor.
 * Each field is a separate row with a key input, value input, and delete button.
 * Converts to/from Map<String, String> for API submission.
 */
@Composable
fun MetadataEditor(
    entries: List<Pair<String, String>>,
    onEntriesChange: (List<Pair<String, String>>) -> Unit,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DataObject,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Metadata",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (entries.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "${entries.count { it.first.isNotBlank() }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entries.isEmpty()) {
                        Text(
                            "No metadata fields. Tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    entries.forEachIndexed { index, (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = { newKey ->
                                    onEntriesChange(entries.toMutableList().also { it[index] = newKey to value })
                                },
                                label = { Text("Key") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    onEntriesChange(entries.toMutableList().also { it[index] = key to newValue })
                                },
                                label = { Text("Value") },
                                modifier = Modifier.weight(1.4f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )
                            IconButton(
                                onClick = {
                                    onEntriesChange(entries.toMutableList().also { it.removeAt(index) })
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove field",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Add field button
                    OutlinedButton(
                        onClick = { onEntriesChange(entries + ("" to "")) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add field", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** Convert a metadata map to an editable list of pairs. */
fun Map<String, *>.toMetadataEntries(): List<Pair<String, String>> =
    entries.map { (k, v) -> k to v.toString() }

/** Convert the editable list back to a submission map, dropping blank keys. */
fun List<Pair<String, String>>.toMetadataMap(): Map<String, String>? =
    filter { it.first.isNotBlank() }
        .associate { it.first.trim() to it.second.trim() }
        .ifEmpty { null }

/** Convert an AI-extracted JsonObject to editable pairs, unquoting string primitives. */
fun JsonObject.toMetadataEntries(): List<Pair<String, String>> =
    entries.map { (k, v) ->
        k to runCatching { v.jsonPrimitive.content }.getOrElse { v.toString() }
    }

/** Merge extracted entries into existing ones, skipping keys that already exist. */
fun mergeMetadataEntries(
    existing: List<Pair<String, String>>,
    extracted: List<Pair<String, String>>
): List<Pair<String, String>> {
    val existingKeys = existing.map { it.first.trim().lowercase() }.toSet()
    return existing + extracted.filter { it.first.trim().lowercase() !in existingKeys }
}
