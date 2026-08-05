package crucible.lens.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One swipe direction's visual treatment — icon, label, and colors shown past the threshold. */
data class SwipeAction(
    val icon: AppIconToken,
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * Wraps [content] in a [SwipeToDismissBox] configured for a single swipe direction (hide or
 * unhide), using the current M3 [SwipeToDismissBox.onDismiss] callback rather than the
 * deprecated `confirmValueChange` constructor. `confirmValueChange` is invoked as part of
 * [androidx.compose.foundation.gestures.AnchoredDraggableState]'s settle logic and can veto or
 * commit a state change without a clean way to distinguish "user released past the threshold"
 * from other internal settling calls. `onDismiss` fires exactly once, only when the box has
 * fully committed to [direction], matching the platform-recommended pattern:
 * https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
 *
 * The caller must give this composable a `key()` that changes whenever the item should be able
 * to swipe again after a prior dismissal was undone (e.g. bump a per-item generation counter on
 * undo) — `SwipeToDismissBoxState` has no supported way to reset itself back to `Settled` after
 * commit without fighting the drag gesture, so a fresh key (and thus a fresh state) is the
 * correct way to make the item swipeable again, not calling `snapTo(Settled)` from a
 * `currentValue`-keyed effect (that fires mid-drag, before release, and yanks the item back).
 *
 * Uses [SwipeToDismissBoxDefaults.positionalThreshold] (a fixed 56dp) rather than a fraction of
 * the row width — this is Material 3's own tuned default, chosen so the same physical thumb
 * travel commits the swipe regardless of screen width, and it composes correctly with the
 * default velocity threshold (125dp/s) so a fast flick still commits well short of 56dp.
 *
 * The reveal background is a flat, edge-to-edge rectangle (not rounded) to match [content]
 * being a full-bleed [androidx.compose.material3.ListItem] rather than an inset [androidx.compose.material3.Card] —
 * rounding it would show a rounded tonal box behind a square-edged row.
 *
 * **Feedback during the live drag is direct, not sprung.** Background alpha is a plain, undamped
 * function of `dismissState.progress`, matching Material's "progressive reveal" guidance for swipe
 * actions (the hidden content should track the finger 1:1) and read inside `drawBehind` - the draw
 * phase - rather than at composition time, so a continuous drag never recomposes this row.
 *
 * **Icon and label render at a fixed size throughout the drag** — deliberately not scaled by
 * `dismissState.progress`. An earlier version grew both from 75% to 125% of nominal size over the
 * drag (and sprang the scale via `animateFloatAsState`, which also recomposed on every drag frame
 * since the read happened at composition time). That made the label visibly larger than its own
 * `labelMedium` role would suggest - a `graphicsLayer` scale transform stretches already-laid-out
 * glyphs rather than relaying text out at a bigger font size, so it read as blown-up text, not
 * "bigger and still crisp." Reserving growth for the icon alone was considered and rejected too:
 * icon and label share one `graphicsLayer` on their common `Column`, and decoupling them would
 * mean re-introducing per-drag-frame composition-time reads to size them independently - the exact
 * cost this component now avoids everywhere else.
 *
 * **The one animated moment is the commit-threshold crossing**, mirroring Gmail's swipe-to-archive
 * pattern: a haptic tick (`HapticFeedbackType.GestureThresholdActivate`, whose own docs describe
 * exactly this "eligible past a threshold, cancellable by moving back past it" gesture) plus a
 * discrete scale "pop" (`commitPulse`, snapped to 1.15 then sprung back to 1.0) fire once, exactly
 * when `dismissState.targetValue` changes - not continuously through the rest of the drag. That's
 * a genuinely discrete event (it only changes at the threshold boundary), unlike `progress`, so
 * springing it doesn't introduce the drag-lag a continuous spring would.
 */
@Composable
fun LazyItemScope.SwipeToHideItem(
    direction: SwipeToDismissBoxValue,
    action: SwipeAction,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    val edgePadding = if (direction == SwipeToDismissBoxValue.StartToEnd)
        Modifier.padding(start = 20.dp) else Modifier.padding(end = 20.dp)

    val hapticFeedback = LocalHapticFeedback.current
    val commitPulse = remember { Animatable(1f) }
    var previousTarget by remember { mutableStateOf(dismissState.targetValue) }
    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue != previousTarget) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            commitPulse.snapTo(1.15f)
            commitPulse.animateTo(1f, animationSpec = SpatialDefaultSpring)
        }
        previousTarget = dismissState.targetValue
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd,
        enableDismissFromEndToStart = direction == SwipeToDismissBoxValue.EndToStart,
        onDismiss = { settledDirection -> if (settledDirection == direction) onDismiss() },
        modifier = modifier.animateItem(EffectsSlowSpring),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(action.containerColor.copy(alpha = 0.4f + 0.6f * dismissState.progress))
                    }
                    .then(edgePadding),
                contentAlignment = alignment
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        scaleX = commitPulse.value
                        scaleY = commitPulse.value
                    }
                ) {
                    AppIcon(action.icon, tint = action.contentColor, modifier = Modifier.size(24.dp))
                    Text(action.label, style = MaterialTheme.typography.labelMedium, color = action.contentColor)
                }
            }
        }
    ) {
        content()
    }
}

/**
 * Standard "hide with undo" flow: marks the item pending (so callers can exclude it from the
 * active list immediately, matching the SwipeToDismissBox's own dismiss animation), persists the
 * hide immediately via [onConfirmedHide], and shows an Undo snackbar.
 *
 * [onConfirmedHide] runs synchronously, before the snackbar is shown — NOT after the snackbar's
 * suspending [SnackbarHostState.showSnackbar] call returns. That call lives in [scope], which is
 * typically `rememberCoroutineScope()` scoped to the list screen's own composition: navigating
 * away (e.g. swipe-to-hide immediately followed by tapping Home) cancels it well before the
 * ~4s snackbar duration elapses, silently dropping a deferred persist and making the hide look
 * like it reverted the next time the list screen composes. Persisting up front means the hide
 * survives navigation regardless of whether the snackbar coroutine ever completes.
 *
 * [onUndone] fires only if the user taps Undo before the snackbar times out — callers should use
 * it to both reverse the persisted hide and bump whatever generation key backs this item's
 * [SwipeToHideItem], so the item gets a fresh, swipeable-again state (see [SwipeToHideItem]'s doc
 * for why that's necessary instead of resetting the existing state).
 */
fun hideWithUndo(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    itemLabel: String,
    onPending: (Boolean) -> Unit,
    onConfirmedHide: () -> Unit,
    onUndone: () -> Unit = {},
    message: String = "\"$itemLabel\" hidden"
) {
    onPending(true)
    onConfirmedHide()
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        onPending(false)
        if (result == SnackbarResult.ActionPerformed) {
            onUndone()
        }
    }
}
