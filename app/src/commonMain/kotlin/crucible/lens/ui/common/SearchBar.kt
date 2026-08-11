package crucible.lens.ui.common
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * The one search field look used everywhere in the app - Projects/Instruments list search,
 * Home's search entry point, and (via [ResourceControlsBar]) Project/Instrument detail's
 * search-within. [shape]/[contentPadding] only exist because [ResourceControlsBar] sits it next
 * to icon buttons in a tighter row than a standalone full-width search field; every other visual
 * property (color, icon, text, cursor) is fixed so callers can't drift from each other by hand-
 * rolling their own copy - that's exactly how this and `ResourceControlsBar`'s field ended up on
 * different colors before being unified here.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(AppIcons.Search,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    singleLine = true
                )
            }
            if (query.isNotEmpty()) {
                AppIcon(AppIcons.ClearInput,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable { onQueryChange("") }
                )
            }
        }
    }
}
