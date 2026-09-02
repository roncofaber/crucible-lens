package crucible.lens.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun RoleBadge(
    role: String?,
    modifier: Modifier = Modifier,
    label: String? = null,
    trailingIcon: AppIconToken? = null
) {
    val normalizedRole = role?.lowercase()
    val colors = MaterialTheme.colorScheme
    val containerColor = when (normalizedRole) {
        "owner" -> colors.tertiaryContainer
        "admin" -> colors.errorContainer
        "editor" -> colors.primaryContainer
        "contributor" -> colors.secondaryContainer
        "viewer" -> colors.surfaceContainerHighest
        else -> colors.surfaceContainerLow
    }
    val contentColor = when (normalizedRole) {
        "owner" -> colors.onTertiaryContainer
        "admin" -> colors.onErrorContainer
        "editor" -> colors.onPrimaryContainer
        "contributor" -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    val displayLabel = label ?: normalizedRole?.replaceFirstChar { it.uppercase() } ?: "Unknown"

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = displayLabel, style = MaterialTheme.typography.labelMedium)
            if (trailingIcon != null) AppIcon(trailingIcon, modifier = Modifier.size(16.dp))
        }
    }
}
