package crucible.lens.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.User
import crucible.lens.data.util.userDisplayName
import crucible.lens.ui.theme.readableOn

/**
 * Avatar circle showing the user's initials. Falls back to "?" if neither name is set.
 * If [orcid] is non-null, the background is deterministically derived from it via
 * [orcidToColor] instead of [containerColor] — every avatar for the same person then looks
 * the same everywhere in the app, and different people get visually distinct colors instead
 * of everyone sharing one static primaryContainer swatch.
 */
@Composable
fun UserAvatar(
    firstName: String?,
    lastName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    orcid: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val initials = buildString {
        firstName?.firstOrNull()?.let { append(it.uppercaseChar()) }
        lastName?.firstOrNull()?.let { append(it.uppercaseChar()) }
    }.ifEmpty { "?" }

    val background = orcid?.let { orcidToColor(it) } ?: containerColor
    // The generated hue has no `on` role, so pick the readable foreground by luminance —
    // hardcoded white failed on the lighter end of the range.
    val foreground = if (orcid != null) readableOn(background) else contentColor

    Surface(shape = CircleShape, color = background, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                color = foreground
            )
        }
    }
}

/**
 * Deterministically maps an ORCID (or any string) to an HSL color: same input always produces
 * the same color, different inputs are spread across the hue wheel. Saturation/lightness are
 * kept in a narrow band (not the full 0-1 range) specifically so white avatar text stays
 * legible against every generated background — this is the one thing worth constraining;
 * unconstrained HSL sampling would occasionally land on near-white or near-black.
 *
 * Uses FNV-1a rather than a cryptographic hash — this only needs to be a stable, well-spread
 * bucket, not collision-resistant, so no platform MessageDigest/expect-actual is needed.
 */
private fun orcidToColor(input: String): Color {
    var hash = 0x811C9DC5.toInt()
    for (b in input.encodeToByteArray()) {
        hash = hash xor (b.toInt() and 0xFF)
        hash *= 0x01000193
    }
    val unsigned = hash.toLong() and 0xFFFFFFFFL
    val hue = (unsigned % 360L).toFloat()
    val saturation = 0.45f + ((unsigned / 360L) % 100L) / 100f * 0.35f  // 0.45–0.80
    val lightness = 0.40f + ((unsigned / 36000L) % 100L) / 100f * 0.15f // 0.40–0.55
    return Color.hsl(hue, saturation, lightness)
}

/**
 * Standard "name, tap for profile" row: avatar + name ([userDisplayName] — full name, no
 * username by default) + optional trailing content (e.g. an action `IconButton`). This is the
 * app-wide default for showing a person already in context (project members, join requesters) —
 * not for identity-lookup contexts like [UserResultItem], which stay username-primary.
 */
@Composable
fun UserIdentityRow(
    user: User?,
    fallbackId: String? = null,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 36.dp,
    avatarContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    avatarContentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserAvatar(
            firstName = user?.firstName, lastName = user?.lastName, size = avatarSize,
            orcid = user?.uniqueId ?: fallbackId,
            containerColor = avatarContainerColor, contentColor = avatarContentColor
        )
        Text(
            userDisplayName(user?.firstName, user?.lastName, user?.username, user?.uniqueId ?: fallbackId),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

/**
 * Full name (bold) with "@username" below, smaller — name-first since a search result is
 * usually already a name match (the server matches first/last name, not just username), and
 * this reads consistently with [UserIdentityRow]'s member rows one screen away in flows like
 * Add Member. Falls back to "@username" alone if no name is set. Shared by [UserResultItem] and
 * [UserPickerItemContent].
 */
@Composable
private fun UserNameBlock(user: User, modifier: Modifier = Modifier) {
    val fullName = listOfNotNull(user.firstName, user.lastName).joinToString(" ")
    Column(modifier = modifier) {
        if (fullName.isNotBlank()) {
            Text(fullName, style = MaterialTheme.typography.bodyMedium)
            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Single row for a user search result — avatar, name-first [UserNameBlock], optional
 * [trailingContent] slot for action buttons (e.g. "Add"). Used for full-sheet/standalone browsing
 * lists (e.g. Add Member); only renders if the user has a username.
 */
@Composable
fun UserResultItem(
    user: User,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (user.username == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(firstName = user.firstName, lastName = user.lastName, size = 36.dp, orcid = user.uniqueId)
            UserNameBlock(user, modifier = Modifier.weight(1f))
            trailingContent?.invoke()
        }
    }
}

/**
 * Bare, text-only name-first result for a [SearchPickerField]'s dropdown `itemContent` — a
 * `DropdownMenuItem`'s text slot shouldn't contain a nested clickable [Surface] (so this is
 * deliberately not [UserResultItem]), and a compact inline dropdown stays text-only like
 * [InstrumentPickerField]'s equivalent, rather than growing an avatar of its own.
 */
@Composable
fun UserPickerItemContent(user: User) {
    if (user.username == null) return
    UserNameBlock(user)
}

/**
 * Leading content for a [SearchPickerField]'s resolved-chip state ([ResolvedPicker.chipLeading])
 * once a search resolves to a real user — a small [UserAvatar], same per-ORCID colour as every
 * other avatar in the app rather than the picker's own one-off treatment.
 */
@Composable
fun UserChipLeading(user: User) {
    UserAvatar(firstName = user.firstName, lastName = user.lastName, size = 24.dp, orcid = user.uniqueId)
}
