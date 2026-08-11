package crucible.lens.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.AppElevation
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons

@Composable
internal fun dirtyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.tertiary
)

// Shared by every settings screen's single-select preference picker (a Row of FilterChips saving
// immediately on tap) - Appearance's theme/contrast/default-tab pickers and Search's result limit.
@Composable
internal fun settingsChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
internal fun settingsChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    borderColor = MaterialTheme.colorScheme.outline,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.5.dp,
    enabled = true,
    selected = selected
)

@Composable
internal fun SettingsSaveBar(
    hasChanges: Boolean,
    onDiscard: () -> Unit,
    onSave: () -> Unit
) {
    AnimatedVisibility(
        visible = hasChanges,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
    ) {
        // Persistent bottom action row - same "toolbar" tier as M3's own scrolled-app-bar/nav-bar/
        // menu chrome (AppElevation.Level2), expressed via tonal colour alone per M3's guidance
        // that plain surfaces use tonal difference for separation rather than stacking a shadow
        // on top too.
        Surface(tonalElevation = AppElevation.Level2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    AppIcon(AppIcons.ClearInput, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Discard")
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    AppIcon(AppIcons.Save, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save changes")
                }
            }
        }
    }
}
