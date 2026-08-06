package crucible.lens.ui.common
import crucible.lens.ui.common.AppIcons

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val (results, isSearching) = rememberDebouncedSearchResults<Instrument>(query = value) { q ->
        apiClient.service.searchInstruments(q)
    }

    SearchPickerField(
        query = value,
        onQueryChange = onValueChange,
        isSearching = isSearching,
        results = results,
        onSelect = { instrument -> onValueChange(instrument.instrumentName ?: "") },
        label = "Instrument",
        leadingIcon = AppIcons.Instrument,
        modifier = modifier,
        reopenOnFocus = true,
        itemContent = { instrument -> Text(instrument.instrumentName ?: instrument.uniqueId, style = MaterialTheme.typography.bodyMedium) }
    )
}
