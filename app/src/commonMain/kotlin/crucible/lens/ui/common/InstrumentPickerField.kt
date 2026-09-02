package crucible.lens.ui.common
import crucible.lens.ui.common.AppIcons

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import crucible.lens.data.api.ApiClient
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import org.koin.compose.koinInject

@Composable
fun InstrumentPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    onInstrumentSelected: (Instrument?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val apiClient = koinInject<ApiClient>()
    val liveSearch = rememberDebouncedSearchState<Instrument>(query = value) { q ->
        apiClient.service.searchInstruments(q, status = InstrumentStatus.Active)
    }
    // Selecting an instrument changes `value` to its own exact name, which would otherwise
    // retrigger rememberDebouncedSearchState's LaunchedEffect(query) and briefly flip the field
    // back to searching before the redundant re-search confirms the same match again - visible as
    // a flash between the resolved and editable renderings. Pinning the pick locally skips that
    // pointless re-search entirely instead of just animating over the flash.
    var pinned by remember { mutableStateOf<Instrument?>(null) }
    val isPinned = pinned?.instrumentName == value
    val results = if (isPinned) listOf(pinned!!) else liveSearch.results
    val isSearching = if (isPinned) false else liveSearch.isSearching

    SearchPickerField(
        query = value,
        onQueryChange = { pinned = null; onInstrumentSelected(null); onValueChange(it) },
        isSearching = isSearching,
        results = results,
        onSelect = { instrument ->
            pinned = instrument
            onInstrumentSelected(instrument)
            onValueChange(instrument.instrumentName ?: "")
        },
        label = "Instrument",
        searchError = liveSearch.error.takeUnless { isPinned },
        onRetrySearch = liveSearch.retry,
        leadingIcon = AppIcons.Instrument,
        modifier = modifier,
        reopenOnFocus = true,
        resolution = ResolvedPicker(
            keyOf = { it.instrumentName },
            resolvedLabel = { it.instrumentName ?: it.uniqueId },
            onClear = { onInstrumentSelected(null); onValueChange("") },
            resolvedLeading = { AppIcon(AppIcons.Instrument, tint = MaterialTheme.colorScheme.primary) }
        ),
        itemContent = { instrument -> Text(instrument.instrumentName ?: instrument.uniqueId, style = MaterialTheme.typography.bodyMedium) }
    )
}
