package crucible.lens.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import crucible.lens.data.util.SortField
import crucible.lens.data.util.SortState
import crucible.lens.ui.theme.emphasizedTitleMedium

/**
 * Left inset for the divider under an icon-leading list row, aligning it with the row's text
 * rather than its leading icon. Shared by [ResourceRow] and the hand-rolled rows in Search and
 * History, which use different row composables but must line up with the same grid.
 */
val ListRowDividerInset = 72.dp

/** One selectable entry in [ResourceControlsBar]'s group-by menu. */
data class GroupByOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * A [ResourceCard] plus its trailing divider — the pair that makes up one row in every
 * sample/dataset list. Repeating the two separately is how the 72dp inset ended up copy-pasted
 * eight times.
 */
@Composable
fun ResourceRow(
    title: String,
    subtitle: String,
    uniqueId: String,
    subtitleMonospace: Boolean = true,
    snippet: String? = null,
    muted: Boolean = false,
    graphExplorerUrl: String = "",
    projectId: String? = null,
    resourceType: String = "sample",
    onClick: () -> Unit
) {
    ResourceCard(
        title = title,
        subtitle = subtitle,
        uniqueId = uniqueId,
        subtitleMonospace = subtitleMonospace,
        snippet = snippet,
        muted = muted,
        graphExplorerUrl = graphExplorerUrl,
        projectId = projectId,
        resourceType = resourceType,
        onClick = onClick
    )
    HorizontalDivider(modifier = Modifier.padding(start = ListRowDividerInset))
}

/**
 * Search field + group-by menu + sort menu, shared by ProjectDetailScreen and
 * InstrumentDetailScreen. The caller supplies the group options because each screen groups by a
 * different enum (and Project's set depends on which tab is showing), but everything else —
 * layout, the tinted search surface, the sort menu — is identical between them.
 *
 * [containerColor] exists because Project renders this inside an already-`surface`-painted column
 * while Instrument renders it as a `stickyHeader` that has to be opaque against `background`.
 */
@Composable
fun ResourceControlsBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchPlaceholder: String,
    groupOptions: List<GroupByOption>,
    sortState: SortState,
    onSortStateChange: (SortState) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background
) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppIcon(AppIcons.Search, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = searchPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        AppIcon(
                            AppIcons.ClearInput,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp).clickable { onSearchChange("") }
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { groupMenuExpanded = true }) {
                    AppIcon(AppIcons.GroupBy, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                    MenuSectionLabel("Group by")
                    groupOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (opt.selected) AppIcon(AppIcons.SelectionDot, modifier = Modifier.size(6.dp))
                                    else Spacer(modifier = Modifier.size(6.dp))
                                    Text(opt.label)
                                }
                            },
                            onClick = { opt.onSelect(); groupMenuExpanded = false }
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    AppIcon(AppIcons.Sort, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    MenuSectionLabel("Sort by")
                    // Deliberately left open on click so the same entry can be tapped again to flip
                    // the direction without reopening the menu.
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (sortState.field == field) {
                                        AppIcon(
                                            if (sortState.ascending) AppIcons.ParentResource else AppIcons.ChildResource,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else Spacer(modifier = Modifier.size(14.dp))
                                    Text(field.label)
                                }
                            },
                            onClick = {
                                onSortStateChange(
                                    if (sortState.field == field) sortState.copy(ascending = !sortState.ascending)
                                    else SortState(field, true)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = {},
        enabled = false
    )
}

/**
 * Empty state for a resource list. [emptyMessage] is caller-supplied because the reason a list is
 * empty differs by screen ("this project has none" vs. "this instrument has none"), while the
 * filtered-to-nothing case reads the same everywhere.
 */
@Composable
fun EmptyListCard(
    resourceName: String,
    defaultIcon: AppIconToken,
    isFiltered: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(
                        if (isFiltered) AppIcons.SearchOff else defaultIcon,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isFiltered) "No Matching $resourceName" else "No $resourceName",
                        style = MaterialTheme.typography.emphasizedTitleMedium
                    )
                }
                Text(
                    text = if (isFiltered) "No ${resourceName.lowercase()} match your search." else emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
