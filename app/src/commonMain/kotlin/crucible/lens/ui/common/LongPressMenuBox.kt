package crucible.lens.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Shared shell for a row's long-press context menu: owns the `expanded` state, the anchoring
 * `Box`, and the `DropdownMenu`, so callers only supply the row itself and its menu items.
 *
 * [content] receives the `onLongClick` lambda to wire into its own `combinedClickable` — the
 * click target varies by caller (a bare `Row` in `ResourceCard`, a `ListItem` in `ProjectCard`),
 * so this doesn't own the row's modifier chain, just the menu plumbing around it.
 */
@Composable
fun LongPressMenuBox(
    menu: @Composable (dismiss: () -> Unit) -> Unit,
    content: @Composable (onLongClick: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        content { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}
