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
import crucible.lens.data.util.userDisplayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class SearchFilters(
    val projectId: String = "",
    val ownerId: String = "",
    val ownerUsername: String = "",
    val createdAfter: String = "",
    val createdBefore: String = "",
    val visibility: String = "all",
    val ownedByMe: Boolean = false,
    val projectMissing: Boolean = false,
    // Dataset-specific
    val measurement: String = "",
    val instrumentName: String = "",
    val dataFormat: String = "",
    val sessionName: String = "",
    val measurementMissing: Boolean = false,
    val instrumentMissing: Boolean = false,
    val dataFormatMissing: Boolean = false,
    val sessionNameMissing: Boolean = false,
    // Sample-specific
    val sampleType: String = "",
    val sampleTypeMissing: Boolean = false
) {
    val isActive: Boolean get() = activeCount > 0
    val activeCount: Int get() = listOf(
        projectId, ownerId, createdAfter, createdBefore,
        measurement, instrumentName, dataFormat, sessionName, sampleType
    ).count { it.isNotBlank() } + listOf(
        ownedByMe, projectMissing, measurementMissing, instrumentMissing,
        dataFormatMissing, sessionNameMissing, sampleTypeMissing
    ).count { it } + if (visibility == "all") 0 else 1
}

data class FacetSuggestions(
    val measurements: List<String> = emptyList(),
    val dataFormats: List<String> = emptyList(),
    val sessionNames: List<String> = emptyList(),
    val sampleTypes: List<String> = emptyList()
)

@Composable
fun FilterSheet(
    filters: SearchFilters,
    suggestions: FacetSuggestions = FacetSuggestions(),
    suggestionsError: String? = null,
    onRetrySuggestions: () -> Unit = {},
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
            Text("Common", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)

            FilterTextField(
                value = local.projectId,
                onValueChange = { local = local.copy(projectId = it, projectMissing = false) },
                label = "Project ID",
                icon = AppIcons.Project
            )
            MissingValueFilter(
                label = "No project assigned",
                checked = local.projectMissing,
                onCheckedChange = { local = local.copy(projectMissing = it, projectId = if (it) "" else local.projectId) }
            )
            OwnerPickerField(
                ownerId = local.ownerId,
                ownerUsername = local.ownerUsername,
                onOwnerSelected = { user ->
                    local = local.copy(
                        ownerId = user.uniqueId ?: "",
                        ownerUsername = user.username ?: user.uniqueId ?: "",
                        ownedByMe = false
                    )
                },
                onOwnerCleared = { local = local.copy(ownerId = "", ownerUsername = "") }
            )
            FilterChip(
                selected = local.ownedByMe,
                onClick = { local = local.copy(ownedByMe = !local.ownedByMe, ownerId = "", ownerUsername = "") },
                label = { Text("Owned by me") },
                leadingIcon = if (local.ownedByMe) {{ AppIcon(AppIcons.Check, modifier = Modifier.size(18.dp)) }} else null
            )
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "public" to "Public", "private" to "Private").forEach { (value, label) ->
                    FilterChip(
                        selected = local.visibility == value,
                        onClick = { local = local.copy(visibility = value) },
                        label = { Text(label) }
                    )
                }
            }
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
                ) { Text("Clear") }
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
                ) { Text("Clear") }
            }

            HorizontalDivider()

            // ── Datasets ──────────────────────────────────────────────────────
            Text("Datasets", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)

            FacetSuggestionField(
                value = local.measurement,
                onValueChange = { local = local.copy(measurement = it, measurementMissing = false) },
                suggestions = suggestions.measurements,
                label = "Measurement",
                icon = AppIcons.Sample
            )
            MissingValueFilter("No measurement", local.measurementMissing) {
                local = local.copy(measurementMissing = it, measurement = if (it) "" else local.measurement)
            }
            InstrumentPickerField(
                value = local.instrumentName,
                onValueChange = { local = local.copy(instrumentName = it, instrumentMissing = false) },
                modifier = Modifier.fillMaxWidth()
            )
            MissingValueFilter("No registered instrument", local.instrumentMissing) {
                local = local.copy(instrumentMissing = it, instrumentName = if (it) "" else local.instrumentName)
            }
            FacetSuggestionField(
                value = local.dataFormat,
                onValueChange = { local = local.copy(dataFormat = it, dataFormatMissing = false) },
                suggestions = suggestions.dataFormats,
                label = "Data format",
                icon = AppIcons.DataFormat
            )
            MissingValueFilter("No data format", local.dataFormatMissing) {
                local = local.copy(dataFormatMissing = it, dataFormat = if (it) "" else local.dataFormat)
            }
            FacetSuggestionField(
                value = local.sessionName,
                onValueChange = { local = local.copy(sessionName = it, sessionNameMissing = false) },
                suggestions = suggestions.sessionNames,
                label = "Session name",
                icon = AppIcons.Tag
            )
            MissingValueFilter("No session name", local.sessionNameMissing) {
                local = local.copy(sessionNameMissing = it, sessionName = if (it) "" else local.sessionName)
            }

            HorizontalDivider()

            // ── Samples ───────────────────────────────────────────────────────
            Text("Samples", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)

            FacetSuggestionField(
                value = local.sampleType,
                onValueChange = { local = local.copy(sampleType = it, sampleTypeMissing = false) },
                suggestions = suggestions.sampleTypes,
                label = "Sample type",
                icon = AppIcons.Category
            )
            MissingValueFilter("No sample type", local.sampleTypeMissing) {
                local = local.copy(sampleTypeMissing = it, sampleType = if (it) "" else local.sampleType)
            }

            if (suggestionsError != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        suggestionsError,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetrySuggestions) { Text("Retry") }
                }
            }

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
private fun MissingValueFilter(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OwnerPickerField(
    ownerId: String,
    ownerUsername: String,
    onOwnerSelected: (User) -> Unit,
    onOwnerCleared: () -> Unit
) {
    // Seeded from ownerUsername (not "") so reopening the sheet with a previous pick starts the
    // field already showing that value - the search this triggers on mount re-resolves it to the
    // resolved field within one debounce window, rather than needing a separate "already
    // resolved" branch.
    var query by remember { mutableStateOf(ownerUsername) }
    val apiClient = koinInject<ApiClient>()

    val liveSearch = rememberDebouncedSearchState<User>(query = query) { q ->
        apiClient.service.searchUsers(q)
    }
    // Selecting a user changes `query` to their own exact username, which would otherwise
    // retrigger rememberDebouncedSearchState's LaunchedEffect(query) and briefly flip the field
    // back to searching before the redundant re-search confirms the same match again - visible as
    // a flash between the resolved and editable renderings. Pinning the pick locally skips that
    // pointless re-search entirely instead of just animating over the flash.
    var pinned by remember { mutableStateOf<User?>(null) }
    val isPinned = pinned?.username == query
    val results = if (isPinned) listOf(pinned!!) else liveSearch.results
    val isSearching = if (isPinned) false else liveSearch.isSearching
    SearchPickerField(
        query = query,
        onQueryChange = {
            pinned = null
            query = it
            if (ownerId.isNotBlank()) onOwnerCleared()
        },
        isSearching = isSearching,
        results = results,
        onSelect = { user -> pinned = user; query = user.username ?: ""; onOwnerSelected(user) },
        label = "Owner",
        searchError = liveSearch.error.takeUnless { isPinned },
        onRetrySearch = liveSearch.retry,
        leadingIcon = AppIcons.Search,
        modifier = Modifier.fillMaxWidth(),
        resolution = ResolvedPicker(
            keyOf = { it.username },
            resolvedLabel = { userDisplayName(it) },
            onClear = { query = ""; onOwnerCleared() },
            resolvedLeading = { user -> UserChipLeading(user) }
        ),
        itemContent = { user -> UserPickerItemContent(user) }
    )
}

@Composable
private fun FacetSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    icon: AppIconToken
) {
    val matches = remember(value, suggestions) {
        suggestions.filter { value.isBlank() || it.contains(value, ignoreCase = true) }.take(20)
    }
    SearchPickerField(
        query = value,
        onQueryChange = onValueChange,
        isSearching = false,
        results = matches,
        onSelect = onValueChange,
        label = label,
        leadingIcon = icon,
        reopenOnFocus = true,
        modifier = Modifier.fillMaxWidth(),
        itemContent = { Text(it) }
    )
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
