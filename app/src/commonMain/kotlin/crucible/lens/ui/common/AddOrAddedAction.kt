package crucible.lens.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The trailing "Add" action on a picker row - shared by `AddMemberSheet` (`ManageProjectScreen.kt`)
 * and `AddToProjectSheet` (`UserProfileScreen.kt`), the two directions of the same action
 * (project→user and user→project). `added` also covers "already a member" - the caller doesn't
 * need to distinguish a pre-existing membership from one just confirmed in this session.
 *
 * Always the same `Button` container across all three states (idle "Add" / in-flight spinner /
 * checkmark) - only its internal content crossfades, inside a fixed-size [Box] so the content
 * transition never triggers `AnimatedContent`'s default size animation (that resize was the
 * "flashy" pop reported when the state changed). "Added" is a checkmark icon rather than text -
 * the word wrapped to two lines in the same fixed-width slot.
 */
@Composable
fun AddOrAddedAction(
    added: Boolean,
    isAdding: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onAdd,
        enabled = !added && !isAdding,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = if (added) MaterialTheme.colorScheme.surfaceContainerHigh
                else ButtonDefaults.buttonColors().disabledContainerColor,
            disabledContentColor = if (added) MaterialTheme.colorScheme.onSurfaceVariant
                else ButtonDefaults.buttonColors().disabledContentColor
        )
    ) {
        AnimatedContent(
            targetState = when {
                added -> AddActionContent.Added
                isAdding -> AddActionContent.Adding
                else -> AddActionContent.Idle
            },
            transitionSpec = {
                fadeIn(animationSpec = EffectsFastSpring) togetherWith fadeOut(animationSpec = EffectsDefaultSpring)
            },
            label = "add_action_content"
        ) { content ->
            Box(modifier = Modifier.size(DpSize(40.dp, 20.dp)), contentAlignment = Alignment.Center) {
                when (content) {
                    AddActionContent.Idle -> Text("Add")
                    AddActionContent.Adding -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    AddActionContent.Added -> AppIcon(AppIcons.Check, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private enum class AddActionContent { Idle, Adding, Added }
