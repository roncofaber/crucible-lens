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
import org.koin.compose.koinInject

@Composable
fun InstrumentPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val apiClient = koinInject<ApiClient>()
    val (liveResults, liveIsSearching) = rememberDebouncedSearchResults<Instrument>(query = value) { q ->
        apiClient.service.searchInstruments(q)
    }
    // Selecting an instrument changes `value` to its own exact name, which would otherwise
    // retrigger rememberDebouncedSearchResults' LaunchedEffect(query) and briefly flip the field
    // back to searching before the redundant re-search confirms the same match again - visible as
    // a flash between the resolved and editable renderings. Pinning the pick locally skips that
    // pointless re-search entirely instead of just animating over the flash.
    var pinned by remember { mutableStateOf<Instrument?>(null) }
    val isPinned = pinned?.instrumentName == value
    val results = if (isPinned) listOf(pinned!!) else liveResults
    val isSearching = if (isPinned) false else liveIsSearching

    SearchPickerField(
        query = value,
        onQueryChange = { pinned = null; onValueChange(it) },
        isSearching = isSearching,
        results = results,
        onSelect = { instrument -> pinned = instrument; onValueChange(instrument.instrumentName ?: "") },
        label = "Instrument",
        leadingIcon = AppIcons.Instrument,
        modifier = modifier,
        reopenOnFocus = true,
        resolution = ResolvedPicker(
            keyOf = { it.instrumentName },
            resolvedLabel = { it.instrumentName ?: it.uniqueId },
            onClear = { onValueChange("") },
            resolvedLeading = { AppIcon(AppIcons.Instrument, tint = MaterialTheme.colorScheme.primary) }
        ),
        itemContent = { instrument -> Text(instrument.instrumentName ?: instrument.uniqueId, style = MaterialTheme.typography.bodyMedium) }
    )
}
