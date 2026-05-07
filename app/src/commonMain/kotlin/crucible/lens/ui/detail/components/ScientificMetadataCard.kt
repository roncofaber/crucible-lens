package crucible.lens.ui.detail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import crucible.lens.data.util.formatDecimal

@Composable
internal fun ScientificMetadataCard(
    metadata: Map<String, Any?>,
    initialExpanded: Boolean = false,
    initialExpandAll: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
    onExpandAllChange: (Boolean) -> Unit = {}
) {
    // The API wraps actual data inside a "scientific_metadata" key — unwrap it if present.
    @Suppress("UNCHECKED_CAST")
    val displayMetadata = (metadata["scientific_metadata"] as? Map<String, Any?>)
        ?.takeIf { it.isNotEmpty() }
        ?: metadata

    var expanded by remember { mutableStateOf(initialExpanded) }
    var expandAll by remember { mutableStateOf(initialExpandAll) }

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scientific Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (expanded) {
                    TextButton(
                        onClick = { val new = !expandAll; expandAll = new; onExpandAllChange(new) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (expandAll) "Collapse All" else "Expand All",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTree(displayMetadata, indentLevel = 0, expandAll = expandAll)
            }
        }
    }
}

@Composable
internal fun MetadataTree(data: Map<String, Any?>, indentLevel: Int, expandAll: Boolean = false) {
    val entries = data.entries.sortedBy { it.key }
    for ((index, entry) in entries.withIndex()) {
        val (entryKey, entryValue) = entry
        key(entryKey) {
            when (entryValue) {
                is Map<*, *> -> {
                    var expanded by remember(expandAll) { mutableStateOf(expandAll) }
                    val nodeRotation by animateFloatAsState(
                        targetValue = if (expanded) 0f else -90f,
                        animationSpec = tween(150),
                        label = "node_expand_icon"
                    )

                    Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(150))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 0.dp, vertical = 4.dp)
                                .padding(start = (indentLevel * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).rotate(nodeRotation),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatKey(entryKey),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (expanded) {
                            @Suppress("UNCHECKED_CAST")
                            val nestedData = entryValue as Map<String, Any?>
                            MetadataTree(nestedData, indentLevel + 1, expandAll)
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 2.dp)
                            .padding(start = (indentLevel * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = formatKey(entryKey),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.35f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatValue(entryValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
            }

            if (index < entries.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .padding(start = (indentLevel * 16).dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

internal fun formatKey(key: String): String = key

internal fun formatValue(value: Any?): String {
    return when (value) {
        null -> "—"
        is Number -> {
            val num = value.toDouble()
            if (num % 1.0 == 0.0) {
                num.toLong().toString()
            } else {
                formatDecimal(num, 4)
            }
        }
        is Boolean -> if (value) "Yes" else "No"
        is List<*> -> {
            if (value.isEmpty()) {
                "[]"
            } else if (value.all { it is Number || it is String || it is Boolean }) {
                value.joinToString(", ")
            } else {
                value.joinToString("\n") { formatValue(it) }
            }
        }
        is Map<*, *> -> {
            // This shouldn't be called for nested maps since we handle them separately
            // But just in case, format as JSON-like
            "{...}"
        }
        else -> value.toString()
    }
}
