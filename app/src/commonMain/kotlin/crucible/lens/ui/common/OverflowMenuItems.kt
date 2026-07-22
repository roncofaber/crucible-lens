package crucible.lens.ui.common
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RefreshMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Refresh") },
        leadingIcon = { AppIcon(AppIcons.Refresh) },
        onClick = onClick
    )
}

@Composable
fun ShareMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Share") },
        leadingIcon = { AppIcon(AppIcons.Share) },
        onClick = onClick
    )
}

@Composable
fun CopyIdMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Copy ID") },
        leadingIcon = { AppIcon(AppIcons.CopyToClipboard) },
        onClick = onClick
    )
}

@Composable
fun OpenInWebMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Open in web") },
        leadingIcon = { AppIcon(AppIcons.Public) },
        onClick = onClick
    )
}

@Composable
fun ToggleHiddenMenuItem(expanded: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (expanded) "Collapse hidden" else "Show hidden") },
        leadingIcon = {
            AppIcon(if (expanded) AppIcons.HideContent else AppIcons.ShowContent)
        },
        onClick = onClick
    )
}
