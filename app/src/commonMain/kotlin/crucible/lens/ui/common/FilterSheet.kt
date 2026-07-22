@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.common
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class SearchFilters(
    val projectId: String = "",
    val ownerOrcid: String = "",
    val ownerUsername: String = "",  // display only — ownerOrcid is sent to API
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
                icon = AppIcons.Project
            )
            OwnerPickerField(
                ownerOrcid = local.ownerOrcid,
                ownerUsername = local.ownerUsername,
                onOwnerSelected = { user ->
                    local = local.copy(
                        ownerOrcid = user.uniqueId ?: "",
                        ownerUsername = user.username ?: user.uniqueId ?: ""
                    )
                },
                onOwnerCleared = { local = local.copy(ownerOrcid = "", ownerUsername = "") }
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
                icon = AppIcons.Sample
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
                icon = AppIcons.DataFormat
            )
            FilterTextField(
                value = local.sessionName,
                onValueChange = { local = local.copy(sessionName = it) },
                label = "Session name",
                icon = AppIcons.Tag
            )

            HorizontalDivider()

            // ── Samples ───────────────────────────────────────────────────────
            Text("Samples", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)

            FilterTextField(
                value = local.sampleType,
                onValueChange = { local = local.copy(sampleType = it) },
                label = "Sample type",
                icon = AppIcons.Category
            )

            // ── Apply ─────────────────────────────────────────────────────────
            Button(
                onClick = { onApply(local); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                AppIcon(AppIcons.Filter, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (local.isActive) "Apply ${local.activeCount} filter${if (local.activeCount > 1) "s" else ""}" else "Apply")
            }
        }
    }
}

@Composable
private fun OwnerPickerField(
    ownerOrcid: String,
    ownerUsername: String,
    onOwnerSelected: (User) -> Unit,
    onOwnerCleared: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val apiClient = koinInject<ApiClient>()

    if (ownerOrcid.isNotBlank()) {
        OutlinedTextField(
            value = if (ownerUsername.isNotBlank()) "@$ownerUsername" else ownerOrcid,
            onValueChange = {},
            readOnly = true,
            label = { Text("Owner") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { AppIcon(AppIcons.User) },
            trailingIcon = {
                IconButton(onClick = { query = ""; results = emptyList(); onOwnerCleared() }) {
                    AppIcon(AppIcons.ClearInput)
                }
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        UserSearchField(
            query = query,
            onQueryChange = { q ->
                query = q
                expanded = true
                scope.launch {
                    if (q.length < 3) { results = emptyList(); isSearching = false; return@launch }
                    delay(350)
                    isSearching = true
                    results = (apiClient.service.searchUsers(q) as? ApiResult.Success)?.data ?: emptyList()
                    isSearching = false
                }
            },
            isSearching = isSearching,
            label = "Owner"
        )
        DropdownMenu(
            expanded = expanded && results.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth()
        ) {
            results.take(6).forEach { user ->
                DropdownMenuItem(
                    text = {
                        val name = listOfNotNull(user.firstName, user.lastName).joinToString(" ")
                        Column {
                            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium)
                            if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { expanded = false; query = ""; onOwnerSelected(user) }
                )
            }
        }
    }
}

@Composable
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: AppIconToken
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { AppIcon(icon) },
        trailingIcon = if (value.isNotBlank()) {
            { IconButton(onClick = { onValueChange("") }) { AppIcon(AppIcons.ClearInput) } }
        } else null
    )
}
