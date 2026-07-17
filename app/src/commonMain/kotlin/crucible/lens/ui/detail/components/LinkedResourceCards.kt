package crucible.lens.ui.detail.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.ResourceLink

@Composable
private fun LinkedResourceCard(
    title: String,
    headerIcon: ImageVector,
    rowIcon: ImageVector,
    links: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)?,
    initialExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "expand_icon"
    )

    Card {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(tween(200))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { val new = !expanded; expanded = new; onExpandChange(new) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(iconRotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(headerIcon, contentDescription = null, modifier = Modifier.size(20.dp))
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
) = LinkedResourceCard("Linked Samples", Icons.Default.Science, Icons.Default.Science, samples, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ParentSamplesCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Parent Samples", Icons.Default.ArrowUpward, Icons.Default.BubbleChart, parents, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ChildSamplesCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Child Samples", Icons.Default.ArrowDownward, Icons.Default.BubbleChart, children, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun LinkedDatasetsCard(
    datasets: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Linked Datasets", Icons.Default.Dataset, Icons.Default.DataObject, datasets, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ParentDatasetsCard(
    parents: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Parent Datasets", Icons.Default.ArrowUpward, Icons.Default.DataObject, parents, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
internal fun ChildDatasetsCard(
    children: List<ResourceLink>,
    onNavigateToResource: (String) -> Unit,
    onUnlink: ((uuid: String, name: String) -> Unit)? = null,
    initialExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {}
) = LinkedResourceCard("Child Datasets", Icons.Default.ArrowDownward, Icons.Default.DataObject, children, onNavigateToResource, onUnlink, initialExpanded, onExpandChange)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ResourceRow(
    icon: ImageVector,
    name: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
