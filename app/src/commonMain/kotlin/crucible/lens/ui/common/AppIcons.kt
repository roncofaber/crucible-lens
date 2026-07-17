package crucible.lens.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Descriptor for a single icon. Carries the drawable resource, an optional
 * filled variant for stateful icons (active/inactive), and the accessibility
 * content description (null = decorative, surrounding label provides context).
 */
data class AppIconToken(
    val resource: DrawableResource,
    val filledResource: DrawableResource? = null,
    val contentDescription: String? = null
)

/**
 * Single composable for all icons in the app. Use [AppIcons] to look up
 * the right token by semantic name rather than icon name.
 *
 * [filled] switches to [AppIconToken.filledResource] when true and a filled
 * variant exists — used for active/inactive states (e.g. pinned bookmark).
 */
@Composable
fun AppIcon(
    icon: AppIconToken,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current
) {
    val resource = if (filled && icon.filledResource != null) icon.filledResource else icon.resource
    Icon(
        painter = painterResource(resource),
        contentDescription = icon.contentDescription,
        modifier = modifier,
        tint = tint
    )
}

// ---------------------------------------------------------------------------
// AppIcons — semantic mapping from concept to token.
// Populated once XML files are placed in commonMain/composeResources/drawable/.
// See docs/icon-downloads.md for the full download list.
// ---------------------------------------------------------------------------
// object AppIcons { ... }
