package crucible.lens.ui.common
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun RefreshMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Refresh") },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        onClick = onClick
    )
}

@Composable
fun ShareMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Share") },
        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
        onClick = onClick
    )
}

@Composable
fun CopyIdMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Copy ID") },
        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        onClick = onClick
    )
}

@Composable
fun OpenInWebMenuItem(onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Open in web") },
        leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
        onClick = onClick
    )
}

@Composable
fun ToggleHiddenMenuItem(expanded: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (expanded) "Collapse hidden" else "Show hidden") },
        leadingIcon = {
            Icon(
                if (expanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null
            )
        },
        onClick = onClick
    )
}
