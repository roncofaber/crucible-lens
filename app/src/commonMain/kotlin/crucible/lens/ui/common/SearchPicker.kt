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
 *
 * [isError] is for the "typed something that doesn't resolve to a real record" case (see
 * [ResolutionState.NotFound]) — tints the outline/label via M3's own error styling and swaps the
 * trailing icon to [AppIcons.SearchOff], the same icon already used for "no results" empty states.
 */
@Composable
private fun SearchTextField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: AppIconToken = AppIcons.Search,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        leadingIcon = { AppIcon(leadingIcon, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                isError -> AppIcon(AppIcons.SearchOff, tint = MaterialTheme.colorScheme.error)
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                    AppIcon(AppIcons.ClearInput)
                }
            }
        }
    )
}

/**
 * Whether the current query in a [SearchPickerField] resolved to a real record — derived purely
 * from the same `(query, results, isSearching)` triple the field already receives, so no caller
 * needs a dedicated "resolved" field in its own state. [Resolved] fires either from tapping a
 * dropdown suggestion (callers keep the picked item as a singleton `results` list rather than
 * clearing it — see [ResolvedPicker]'s callers) or from typing the exact name/username and having
 * the debounced search confirm it, matching Gmail's recipient-resolution behavior.
 */
sealed class ResolutionState<out T> {
    data object Idle : ResolutionState<Nothing>()
    data object Resolving : ResolutionState<Nothing>()
    data class Resolved<T>(val item: T) : ResolutionState<T>()
    data object NotFound : ResolutionState<Nothing>()
}

private fun <T> resolveSearchMatch(
    query: String,
    results: List<T>,
    isSearching: Boolean,
    keyOf: (T) -> String?
): ResolutionState<T> {
    val exact = results.firstOrNull { keyOf(it)?.equals(query, ignoreCase = true) == true }
    return when {
        query.isBlank() -> ResolutionState.Idle
        isSearching -> ResolutionState.Resolving
        exact != null -> ResolutionState.Resolved(exact)
        query.length >= SEARCH_MIN_QUERY_LENGTH -> ResolutionState.NotFound
        else -> ResolutionState.Idle
    }
}

/**
 * Bundles everything a [SearchPickerField] needs to opt into resolve-to-field behavior: how to
 * find an exact match in its `results` ([keyOf]), how to label and render the resolved value
 * ([resolvedLabel]/[resolvedLeading]), and what happens when its clear "×" is tapped ([onClear] —
 * typically the same change handler the field already uses, called with an empty string).
 */
data class ResolvedPicker<T>(
    val keyOf: (T) -> String?,
    val resolvedLabel: (T) -> String,
    val onClear: () -> Unit,
    val resolvedLeading: @Composable (T) -> Unit
)

/**
 * Replaces an editable [SearchPickerField] once its query has resolved to a real record — a
 * *read-only* `OutlinedTextField`, not a colored pill: same [label], same transparent background
 * and outline as every other field on the form, avatar/icon as the leading content and the
 * resolved name as the value. Deliberately not a filled/tinted chip — an early version used a
 * solid `secondaryContainer` bar, which (a) dropped the field's label entirely, so a resolved
 * field carried no indication of what it was, and (b) reads as an error/warning banner rather
 * than a confirmed value on any accent whose `secondaryContainer` leans orange/red. Reusing
 * `OutlinedTextField` sidesteps both: the label float behavior is free, and there is no
 * theme-dependent fill color to get wrong.
 */
@Composable
private fun <T> ResolvedField(picker: ResolvedPicker<T>, item: T, label: String, modifier: Modifier, enabled: Boolean) {
    OutlinedTextField(
        value = picker.resolvedLabel(item),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        leadingIcon = { picker.resolvedLeading(item) },
        trailingIcon = {
            IconButton(onClick = picker.onClear, enabled = enabled) {
                AppIcon(AppIcons.ClearInput)
            }
        }
    )
}

/**
 * Inline "search and pick one" — a text field with a floating [DropdownMenu] of results, capped
 * at 240dp so a long result list scrolls within the popup instead of pushing the surrounding
 * layout around (the bug this replaces: a plain Column of results below a field shifts every
 * sibling below it as results appear/change). Selecting a result calls [onSelect] and closes the
 * dropdown; what the field displays afterward is the caller's concern, unless [resolution] is
 * supplied — then a confirmed match (see [ResolutionState]) renders as a [ResolvedField] instead
 * of the editable field, and an unresolved one tints the field red via [SearchTextField]'s
 * `isError`.
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
    resolution: ResolvedPicker<T>? = null,
    itemContent: @Composable (T) -> Unit
) {
    val resolutionState = resolution?.let { resolveSearchMatch(query, results, isSearching, it.keyOf) }
    if (resolution != null && resolutionState is ResolutionState.Resolved) {
        ResolvedField(picker = resolution, item = resolutionState.item, label = label, modifier = modifier, enabled = enabled)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SearchTextField(
            query = query,
            onQueryChange = { onQueryChange(it); expanded = true },
            isSearching = isSearching,
            label = label,
            leadingIcon = leadingIcon,
            enabled = enabled,
            isError = resolutionState is ResolutionState.NotFound,
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
