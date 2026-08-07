package crucible.lens.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.ui.theme.emphasizedTitleLarge
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    navIcon: AppIconToken = AppIcons.Back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    AppIcon(navIcon)
                }
            }
        },
        actions = actions
    )
}

// The two titles hand over rather than cross-dissolve: the expanded block is gone by 60% collapsed,
// the compact title only starts appearing after that. Overlapping them would briefly show the same
// name twice at two sizes.
private const val TITLE_HANDOVER = 0.6f

// The hero icon badge's footprint - expandedContent is indented by exactly this much so its left
// edge lines up with the title's, rather than the two reading as separately-aligned blocks.
private val HeroIconBadgeSize = 44.dp
private val HeroIconBadgeSpacing = 12.dp

private fun expandedContentAlpha(collapsedFraction: Float): Float =
    (1f - collapsedFraction / TITLE_HANDOVER).coerceIn(0f, 1f)

private fun collapsedTitleAlpha(collapsedFraction: Float): Float =
    ((collapsedFraction - TITLE_HANDOVER) / (1f - TITLE_HANDOVER)).coerceIn(0f, 1f)

/**
 * Collapsing variant of [AppTopBar] for detail screens whose title is the actual entity name
 * (project/instrument) rather than a static label. [name] shows in both states — full-size with
 * up to 3 lines while expanded, single-line-ellipsized once collapsed; [icon] and
 * [expandedContent] (secondary metadata — lead/org/member-count for a project, type/location for
 * an instrument) render only while expanded.
 *
 * [expandedContainerColor]/[expandedContentColor] default to `secondaryContainer`/
 * `onSecondaryContainer` — the shared expanded-state identity both `ProjectDetailScreen` and
 * `InstrumentDetailScreen` use, so neither passes them explicitly. Both are still overridable
 * together for a future screen that needs a different identity; [expandedContent]'s own text/icon
 * colours would then need updating to pair with the new container too, since they aren't derived
 * from it automatically — changing only the container leaves content paired with a container that
 * never guaranteed contrast against it, only against whatever role the content colour actually is.
 *
 * **A custom implementation, not `MediumTopAppBar`/`TwoRowsTopAppBar` — deliberately.**
 * `MediumTopAppBar`'s public API exposes a single `title: @Composable () -> Unit` slot, but
 * Material3 renders that *same* composable **twice** internally — once sized for the large
 * expanded row, once for the compact collapsed row — crossfading opacity between the two based on
 * scroll. Both instances necessarily share whatever content decision lives inside that lambda, so
 * anything that changes the title's height (different line counts, extra rows) risks a window
 * where the compact row's fade-in alpha is already non-zero before the content has caught up to
 * "collapsed," visibly rendering the oversized expanded content in the tiny compact strip. The
 * real fix for "different content per row" — `TwoRowsTopAppBar`'s
 * `title: @Composable (expanded: Boolean) -> Unit`, which tells each row instance which one it is
 * — exists in this M3 version but compiles `internal` in this project's CMP artifact, so it isn't
 * usable. Rather than keep tuning thresholds around that limitation, this composable **doesn't
 * use Material3's row-duplicating title mechanism at all**: it's one ordinary `Column` with a
 * single render pass, reading `scrollBehavior.state.collapsedFraction` directly (the exact same
 * public, well-tested state class the standard component would use) to decide what to show. Since
 * there's no second hidden instance to desync from, content decisions here - including ones that
 * change height - are unconditionally safe, with no thresholds to tune and no residual risk.
 * Reuses `TopAppBarDefaults` for insets/height tokens and the same `scrollBehavior` (and its
 * `nestedScrollConnection`) the caller already wires up, so nothing about the surrounding
 * screens' scroll plumbing needs to change.
 *
 * **The collapse is continuous, driven only by the finger.** Every scroll-varying value here
 * (bar height, both title alphas, container colour) is a pure function of `collapsedFraction`,
 * read inside a `layout`/`graphicsLayer`/`drawBehind` lambda so it resolves in the layout or draw
 * phase and never recomposes while scrolling. There is deliberately no `AnimatedVisibility` and no
 * animation spec: an earlier version flipped content at a `collapsedFraction > 0.5f` threshold and
 * let `AnimatedVisibility` run its own spring, which (a) made the bar ignore the finger until it
 * suddenly jumped at the halfway point, (b) ran a second animation on top of the snap
 * `exitUntilCollapsedScrollBehavior` already performs on release, and (c) fed the *animating*
 * height back into `heightOffsetLimit` via `onGloballyPositioned`, so the limit moved mid-animation
 * and could re-cross its own threshold. The expanded block is now measured unconstrained by the
 * fraction, so that measurement is stable and the feedback loop is gone.
 * Pair with `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` and attach
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` — on a child *inside* whatever
 * scroll-gesture-consuming container (e.g. `PullToRefreshBox`) wraps the scrollable content, not
 * on that container's own `modifier`, so this connection sits closer to the list and gets first
 * refusal on the gesture (see `dev/style.md`'s "Nested scroll ordering" note).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingAppTopBar(
    name: String,
    scrollBehavior: TopAppBarScrollBehavior,
    icon: AppIconToken? = null,
    onBack: (() -> Unit)? = null,
    navIcon: AppIconToken = AppIcons.Back,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    expandedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    expandedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    expandedContent: @Composable ColumnScope.() -> Unit = {},
) {
    // MediumTopAppBar/TwoRowsTopAppBar normally set TopAppBarState.heightOffsetLimit themselves
    // during their own measure pass - since this composable never calls either, the limit is left
    // at its default (-Float.MAX_VALUE), which makes ExitUntilCollapsedScrollBehavior's
    // nestedScrollConnection treat the bar as infinitely collapsible and consume every upward
    // scroll delta itself, starving the list below of any scroll input. Measuring the collapsible
    // block's real height and feeding it back in as the limit restores normal scroll handoff.
    // Measured in the layout modifier below rather than via onGloballyPositioned, so the value is
    // the block's natural height and not whatever partial height the current fraction is showing.
    // The limit is published from the layout pass below, NOT from a SideEffect. Every read of
    // collapsedFraction here is deferred into a layout/draw lambda, so scrolling never recomposes
    // this composable - which means a SideEffect would run once, at first composition, when the
    // block has not been measured yet, and the limit would stay at -Float.MAX_VALUE forever. The
    // bar would then look infinitely collapsible and the connection would swallow every scroll
    // delta, leaving the list unscrollable.
    //
    // Plain (non-snapshot) box holding the last published height: the layout lambda compares
    // against this instead of reading heightOffsetLimit back, so it never reads the same snapshot
    // state it writes and cannot invalidate itself in a loop.
    val publishedHeightPx = remember { intArrayOf(-1) }

    // Expanded = [expandedContainerColor] (default: `secondaryContainer`, shared by both
    // ProjectDetailScreen and InstrumentDetailScreen), collapsed = plain `surface`, matching the
    // page background exactly so the compact bar never reads as a differently-coloured panel once
    // it settles. A future screen needing a different expanded identity can override this and
    // [expandedContentColor] together so text/icons stay correctly paired. Read via `drawBehind`
    // below - a deferred draw-phase read, not a composable-time one, so scrolling never recomposes
    // this composable (same reasoning as every other collapsedFraction read in this function).
    val collapsedContainerColor = MaterialTheme.colorScheme.surface
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(lerp(expandedContainerColor, collapsedContainerColor, scrollBehavior.state.collapsedFraction))
            }
    ) {
        Column(modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
            // Nav icon + actions always occupy a fixed-height row, same whether expanded or
            // collapsed (matching Material3's own Medium/Large bar convention) — only the title
            // area's content and the space below it change with scroll.
            Row(
                modifier = Modifier.fillMaxWidth().height(TopAppBarDefaults.MediumAppBarCollapsedHeight).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        AppIcon(navIcon)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
                Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer { alpha = collapsedTitleAlpha(scrollBehavior.state.collapsedFraction) }
                            .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                            .semantics { heading() }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            // Outer Box clips to the reported (shrinking) height; the inner layout modifier
            // measures the content at its natural height but reports a fraction of it, placing the
            // content bottom-anchored so it slides up behind the action row as the bar collapses.
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            if (placeable.height != publishedHeightPx[0]) {
                                publishedHeightPx[0] = placeable.height
                                scrollBehavior.state.heightOffsetLimit = -placeable.height.toFloat()
                            }
                            val fraction = scrollBehavior.state.collapsedFraction
                            val visibleHeight = (placeable.height * (1f - fraction)).roundToInt()
                            // Skipping placement once the content has faded out is what keeps it
                            // from staying tappable while invisible. alpha alone does not affect
                            // hit testing, and the block's bottom edge (the lead/organization row)
                            // is the last thing still inside the shrinking bounds, so with only an
                            // alpha fade a tap on a collapsed bar landed on the lead's profile.
                            val placed = expandedContentAlpha(fraction) > 0f
                            layout(placeable.width, visibleHeight) {
                                if (placed) placeable.place(0, visibleHeight - placeable.height)
                            }
                        }
                        .graphicsLayer { alpha = expandedContentAlpha(scrollBehavior.state.collapsedFraction) }
                        // 16dp horizontal matches M3's app-bar inset. The vertical split is
                        // deliberately lopsided: M3's medium bar bottom-aligns its expanded title
                        // with MediumTitleBottomPadding (24dp) beneath it and nothing above, so the
                        // breathing room belongs below the block, not shared evenly with the top.
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // The icon badge anchors the whole block the way an avatar would in a
                    // GitHub/Linear-style header - a real focal point rather than another line of
                    // text - while staying a static token today. A future per-project custom icon
                    // would slot into the same badge without changing this layout.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HeroIconBadgeSpacing),
                        modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier
                    ) {
                        if (icon != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(HeroIconBadgeSize)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIcon(icon, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.emphasizedTitleLarge,
                            color = expandedContentColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    // Indented to align with the title, not the badge - the badge anchors the
                    // title specifically, while this metadata reads as one continuous left-aligned
                    // column beneath it (matching M3's own LargeTopAppBar, which start-aligns its
                    // expanded title rather than centering it).
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (icon != null) HeroIconBadgeSize + HeroIconBadgeSpacing else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        expandedContent()
                    }
                }
            }
        }
    }
}
