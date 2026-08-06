@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiResult
import crucible.lens.data.util.SEARCH_DEBOUNCE_MS
import crucible.lens.data.util.SEARCH_MIN_QUERY_LENGTH
import kotlinx.coroutines.delay

/**
 * Debounced server-side search-as-you-type: waits [debounceMs] of no changes to [query] before
 * calling [search], and only once [query] reaches [minLength]. Callers below [minLength] see an
 * empty result list and no loading spinner. Mirrors the composable-hook shape already used by
 * `rememberOwnerNames` in ProjectDetailScreen.kt — for composable-local search state only; the
 * ViewModel-owned searches (ManageProjectViewModel's lead/member search) keep their own
 * cancellable-Job pattern since they need to be triggered from event handlers, not recomposition.
 */
@Composable
fun <T> rememberDebouncedSearchResults(
    query: String,
    minLength: Int = SEARCH_MIN_QUERY_LENGTH,
    debounceMs: Long = SEARCH_DEBOUNCE_MS,
    search: suspend (String) -> ApiResult<List<T>>
): Pair<List<T>, Boolean> {
    var results by remember { mutableStateOf<List<T>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.length < minLength) { results = emptyList(); isSearching = false; return@LaunchedEffect }
        delay(debounceMs)
        isSearching = true
        results = (search(query) as? ApiResult.Success)?.data ?: emptyList()
        isSearching = false
    }
    return results to isSearching
}

/**
 * Shared look for a search-as-you-type field: search icon, clear button, loading spinner.
 * Caller owns query state and debouncing (e.g. via [rememberDebouncedSearchResults]).
 */
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: AppIconToken = AppIcons.Search,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        leadingIcon = { AppIcon(leadingIcon, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                    AppIcon(AppIcons.ClearInput)
                }
            }
        }
    )
}

/**
 * Inline "search and pick one" — a text field with a floating [DropdownMenu] of results, capped
 * at 240dp so a long result list scrolls within the popup instead of pushing the surrounding
 * layout around (the bug this replaces: a plain Column of results below a field shifts every
 * sibling below it as results appear/change). Selecting a result calls [onSelect] and closes the
 * dropdown; what the field displays afterward is the caller's concern.
 */
@Composable
fun <T> SearchPickerField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<T>,
    onSelect: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: AppIconToken = AppIcons.Search,
    enabled: Boolean = true,
    reopenOnFocus: Boolean = false,
    itemContent: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SearchTextField(
            query = query,
            onQueryChange = { onQueryChange(it); expanded = true },
            isSearching = isSearching,
            label = label,
            leadingIcon = leadingIcon,
            enabled = enabled,
            modifier = if (reopenOnFocus) {
                Modifier.onFocusChanged { if (it.isFocused && results.isNotEmpty()) expanded = true }
            } else Modifier
        )
        DropdownMenu(
            expanded = expanded && results.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
        ) {
            results.forEach { item ->
                DropdownMenuItem(
                    text = { itemContent(item) },
                    onClick = { expanded = false; onSelect(item) }
                )
            }
        }
    }
}

/**
 * Full "search and pick" bottom sheet: the search field is pinned above a bounded, scrollable
 * [LazyColumn] of results — the field can never be pushed off-screen, and results scroll within
 * their own region instead of overflowing the sheet (the AddMemberSheet bug this replaces: a
 * plain Column.forEach with no scroll modifier at all). Unlike [SearchPickerField], selecting a
 * result does not have to dismiss the sheet — [itemContent] decides, e.g. a per-row "Add" button
 * so multiple items can be picked in one visit.
 */
@Composable
fun <T> SearchPickerSheet(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<T>,
    onDismiss: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: AppIconToken = AppIcons.Search,
    key: (T) -> Any,
    emptyContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            SearchTextField(
                query = query,
                onQueryChange = onQueryChange,
                isSearching = isSearching,
                label = label,
                leadingIcon = leadingIcon
            )
            if (results.isEmpty() && emptyContent != null) {
                emptyContent()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = key) { item -> itemContent(item) }
                }
            }
        }
    }
}
