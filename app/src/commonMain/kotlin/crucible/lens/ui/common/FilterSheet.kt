package crucible.lens.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SearchFilters(
    val projectId: String = "",
    val ownerOrcid: String = "",
    val createdAfter: String = "",
    val createdBefore: String = "",
    // Dataset-specific
    val measurement: String = "",
    val instrumentName: String = "",
    val dataFormat: String = "",
    val sessionName: String = "",
    // Sample-specific
    val sampleType: String = ""
) {
    val isActive: Boolean get() = activeCount > 0
    val activeCount: Int get() = listOf(
        projectId, ownerOrcid, createdAfter, createdBefore,
        measurement, instrumentName, dataFormat, sessionName, sampleType
    ).count { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filters: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var local by remember(filters) { mutableStateOf(filters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                if (local.isActive) {
                    TextButton(onClick = { local = SearchFilters() }) { Text("Clear all") }
                }
            }

            // ── Common ────────────────────────────────────────────────────────
            Text("Common", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)

            FilterTextField(
                value = local.projectId,
                onValueChange = { local = local.copy(projectId = it) },
                label = "Project ID",
                icon = Icons.Default.Folder
            )
            FilterTextField(
                value = local.ownerOrcid,
                onValueChange = { local = local.copy(ownerOrcid = it) },
                label = "Owner ORCID",
                icon = Icons.Default.Person
            )
            DateTimePickerField(
                value = local.createdAfter,
                onValueChange = { local = local.copy(createdAfter = it) },
                label = "Created after",
                modifier = Modifier.fillMaxWidth()
            )
            if (local.createdAfter.isNotBlank()) {
                TextButton(
                    onClick = { local = local.copy(createdAfter = "") },
                    modifier = Modifier.align(Alignment.End).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
            }
            DateTimePickerField(
                value = local.createdBefore,
                onValueChange = { local = local.copy(createdBefore = it) },
                label = "Created before",
                modifier = Modifier.fillMaxWidth()
            )
            if (local.createdBefore.isNotBlank()) {
                TextButton(
                    onClick = { local = local.copy(createdBefore = "") },
                    modifier = Modifier.align(Alignment.End).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Clear", style = MaterialTheme.typography.labelSmall) }
            }

            HorizontalDivider()

            // ── Datasets ──────────────────────────────────────────────────────
            Text("Datasets", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)

            FilterTextField(
                value = local.measurement,
                onValueChange = { local = local.copy(measurement = it) },
                label = "Measurement",
                icon = Icons.Default.Science
            )
            InstrumentPickerField(
                value = local.instrumentName,
                onValueChange = { local = local.copy(instrumentName = it) },
                modifier = Modifier.fillMaxWidth()
            )
            FilterTextField(
                value = local.dataFormat,
                onValueChange = { local = local.copy(dataFormat = it) },
                label = "Data format",
                icon = Icons.Default.FilePresent
            )
            FilterTextField(
                value = local.sessionName,
                onValueChange = { local = local.copy(sessionName = it) },
                label = "Session name",
                icon = Icons.Default.Tag
            )

            HorizontalDivider()

            // ── Samples ───────────────────────────────────────────────────────
            Text("Samples", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)

            FilterTextField(
                value = local.sampleType,
                onValueChange = { local = local.copy(sampleType = it) },
                label = "Sample type",
                icon = Icons.Default.Category
            )

            // ── Apply ─────────────────────────────────────────────────────────
            Button(
                onClick = { onApply(local); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (local.isActive) "Apply ${local.activeCount} filter${if (local.activeCount > 1) "s" else ""}" else "Apply")
            }
        }
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (value.isNotBlank()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } }
        } else null
    )
}
