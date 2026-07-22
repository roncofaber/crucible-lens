@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.metadata
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import crucible.lens.ui.metadata.MetadataHolder
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.toPrettyString
import kotlinx.serialization.json.JsonObject

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

    // Derived field count — recomputed only when jsonText changes.
    val fieldCount by remember {
        derivedStateOf {
            if (jsonText.isBlank()) 0
            else runCatching { jsonText.trim().parseAsJsonObject().size }.getOrElse { -1 }
        }
    }

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

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Metadata",
                onBack = onBack,
                actions = {
                    TextButton(onClick = ::formatJson) {
                        Text("{ }", style = MaterialTheme.typography.labelLarge)
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
            // JSON text editor — takes all remaining space.
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it; parseError = null },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("Scientific Metadata (JSON)") },
                placeholder = { Text("{\n  \"key\": \"value\"\n}") },
                isError = parseError != null,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                supportingText = {
                    when {
                        parseError != null -> Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(AppIcons.ErrorOutline, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                            Text(parseError!!, color = MaterialTheme.colorScheme.error)
                        }
                        jsonText.isBlank() -> Text("Empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        fieldCount < 0    -> Text("Invalid JSON", color = MaterialTheme.colorScheme.error)
                        else              -> Text("$fieldCount key${if (fieldCount != 1) "s" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}
