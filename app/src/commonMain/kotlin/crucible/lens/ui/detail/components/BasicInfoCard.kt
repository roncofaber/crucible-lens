package crucible.lens.ui.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.CrucibleResource
import crucible.lens.ui.common.fadeEndEdge

@Composable
internal fun BasicInfoCard(
    resource: CrucibleResource,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    currentIndex: Int = -1,
    totalCount: Int = 0,
    siblingsResolved: Boolean = true
) {
    Card(border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Name — full card width, not squeezed by chevrons
            AnimatedContent(
                targetState = resource.name,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                },
                label = "resource_name"
            ) { name ->
                val nameScrollState = rememberScrollState()
                var nameOverflows by remember(name) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadeEndEdge(nameOverflows && nameScrollState.canScrollForward)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = if (nameOverflows) TextAlign.Start else TextAlign.Center,
                        onTextLayout = { if (it.hasVisualOverflow) nameOverflows = true },
                        modifier = if (nameOverflows) Modifier.horizontalScroll(nameScrollState)
                                   else Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Navigation row — chevrons + counter only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onPrev?.invoke() },
                    enabled = onPrev != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Previous sample",
                        tint = if (onPrev != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
                // Animated counter — placeholder while siblings are loading
                val counterText = when {
                    !siblingsResolved || currentIndex < 0 || totalCount == 0 -> "-- / --"
                    else -> "${currentIndex + 1} / $totalCount"
                }
                AnimatedContent(
                    targetState = counterText,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 200))
                    },
                    label = "sibling_counter"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onNext?.invoke() },
                    enabled = onNext != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next sample",
                        tint = if (onNext != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}
