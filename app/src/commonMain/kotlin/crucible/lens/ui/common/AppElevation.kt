package crucible.lens.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 6 canonical M3 elevation levels (dp values only - M3 assigns no color or shadow to a level,
 * each platform decides that). Pass these to `tonalElevation`/`shadowElevation`/
 * `CardDefaults.cardElevation()`/`FloatingActionButtonDefaults.elevation()` instead of inventing a
 * one-off dp value - every arbitrary override this app had before this token set existed (2dp,
 * 4dp, 6dp-on-a-banner, 8dp-on-a-resting-bar) was a level that doesn't exist in the M3 scale, a
 * canonical level borrowed from the wrong component tier, or a level M3 reserves for
 * hover/focus/drag rather than a resting state.
 *
 * Resting-level mapping per M3's elevation spec (`https://m3.material.io/styles/elevation`):
 * - [Level0] (0dp) - app bar (not scrolled), buttons, cards (filled/outlined), chips, full-screen
 *   dialog, icon buttons, list, navigation rail, segmented button, side sheet (docked), tabs
 * - [Level1] (1dp) - banner, bottom sheet (modal), button/card/chip (elevated variant),
 *   navigation drawer (modal), side sheet (modal)
 * - [Level2] (3dp) - app bar (scrolled), menu, navigation bar, rich tooltip, toolbar
 * - [Level3] (6dp) - date/time pickers, dialogs (modal), extended FAB, FAB, search
 * - [Level4] (8dp) and [Level5] (12dp) - **not resting levels.** M3 reserves these for a
 *   component's hover/focus/dragged state only (e.g. a FAB raises from [Level3] to [Level4] on
 *   hover) - don't reach for them as a component's default/resting elevation.
 */
object AppElevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 3.dp
    val Level3: Dp = 6.dp
    val Level4: Dp = 8.dp
    val Level5: Dp = 12.dp
}
