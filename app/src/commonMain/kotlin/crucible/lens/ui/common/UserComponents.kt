package crucible.lens.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.User

/**
 * Avatar circle showing the user's initials. Falls back to "?" if neither name is set.
 * Container color defaults to [MaterialTheme.colorScheme.primaryContainer].
 */
@Composable
fun UserAvatar(
    firstName: String?,
    lastName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val initials = buildString {
        firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifEmpty { "?" }

    Surface(shape = CircleShape, color = containerColor, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Username search text field. Handles @ prefix, clear button, and loading indicator.
 * Caller owns the query state and debouncing — this is purely presentational.
 */
@Composable
fun UserSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Search by username",
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = query,
        onValueChange = { onQueryChange(it.lowercase()) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            when {
                isSearching -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                query.isNotBlank() -> IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }
    )
}

/**
 * Single row for a user search result. Shows @username (bold) with full name below.
 * Optional [trailingContent] slot for action buttons (e.g. "Add").
 * Only renders if the user has a username.
 */
@Composable
fun UserResultItem(
    user: User,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (user.username == null) return
    val fullName = listOfNotNull(user.firstName, user.lastName).joinToString(" ")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (fullName.isNotBlank()) {
                    Text(fullName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailingContent?.invoke()
        }
    }
}
