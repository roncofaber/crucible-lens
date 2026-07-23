package crucible.lens.ui.detail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.ResourceLink
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.StandardSizeAnim

@Composable
private fun LinkedResourceCard(
    title: String,
    headerIcon: AppIconToken,
    rowIcon: AppIconToken,
    links: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)?,
    initialExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(StandardSizeAnim)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpandChevron(expanded = expanded, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                AppIcon(headerIcon, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$title (${links.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (link in links) {
                        ResourceRow(
                            icon = rowIcon,
                            name = link.name ?: "",
                            onClick = { onNavigateToResource(link.uniqueId) },
                            onLongClick = onUnlink?.let { { it(link.uniqueId, link.name ?: "") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LinkedSamplesCard(
    samples: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Linked Samples", AppIcons.Sample, AppIcons.Sample, samples, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ParentSamplesCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Parent Samples", AppIcons.ParentResource, AppIcons.Sample, parents, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ChildSamplesCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Child Samples", AppIcons.ChildResource, AppIcons.Sample, children, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun LinkedDatasetsCard(
    datasets: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Linked Datasets", AppIcons.Dataset, AppIcons.Dataset, datasets, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ParentDatasetsCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Parent Datasets", AppIcons.ParentResource, AppIcons.Dataset, parents, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ChildDatasetsCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Child Datasets", AppIcons.ChildResource, AppIcons.Dataset, children, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ResourceRow(
    icon: AppIconToken,
    name: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        if (subtitle != null) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIcon(AppIcons.NavigateNext, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}
