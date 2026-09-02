package crucible.lens.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.platform.copyToClipboard
import crucible.lens.platform.buildCrucibleWebUrl
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openInBrowser
import crucible.lens.platform.shareText

/**
 * Standard resource-list row: title + one subtitle line + a "navigate" chevron, with a
 * long-press context menu (Copy ID, plus Open in Web/Share once a project + Crucible Web URL are
 * known). Shared by `ProjectDetailScreen` (samples/datasets, subtitle = the resource's own mfid),
 * `InstrumentDetailScreen` (datasets, subtitle = the owning project's ID) and `SearchScreen` —
 * same row, same chrome, different subtitle content depending on what's most useful to
 * disambiguate in context.
 *
 * No leading icon on the row itself: every caller's list is type-homogeneous within its section,
 * so the icon repeats the section header exactly and carries no information. The 56dp text inset
 * is preserved so rows stay aligned vertically and the 72dp divider inset relationship is unchanged.
 *
 * [snippet] adds a second supporting line above the subtitle; Search uses it for a scientific
 * metadata preview. [muted] tints the title text `onSurfaceVariant` instead of `onSurface`,
 * signalling "not fully accessible" — the same treatment hidden projects and instruments get, and
 * what Search uses for a project the current user isn't a member of. Both default off, so existing
 * callers are unaffected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourceCard(
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
    val platformCtx = getPlatformContext()

    val webUrl = if (projectId != null && graphExplorerUrl.isNotBlank()) {
        val resourcePath = if (resourceType == "dataset") "datasets" else "samples"
        buildCrucibleWebUrl(graphExplorerUrl, projectId, resourcePath, uniqueId)
    } else null

    LongPressMenuBox(
        menu = { dismiss ->
            CopyIdMenuItem { dismiss(); copyToClipboard(platformCtx, uniqueId) }
            if (webUrl != null) {
                OpenInWebMenuItem { dismiss(); openInBrowser(platformCtx, webUrl) }
                ShareMenuItem { dismiss(); shareText(platformCtx, webUrl, "") }
            }
        }
    ) { onLongClick ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // bodyMedium: 14sp row title, undershooting M3's 16sp ListItem spec deliberately for
                // density. Separation from SectionHeader's titleMedium (16sp Medium) comes from the
                // header's size, weight, container, and tinted icon — not from the row's weight.
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snippet != null) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IdText(text = subtitle, monospace = subtitleMonospace)
            }
            AppIcon(AppIcons.NavigateNext, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
