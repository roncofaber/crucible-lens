package crucible.lens.ui.common

import androidx.compose.ui.unit.dp

/**
 * Start inset for a [androidx.compose.material3.HorizontalDivider] following a [androidx.compose.material3.ListItem]
 * that has a leading icon — aligns the divider with the row's headline text rather than running
 * full-bleed under the icon. Matches the classic Material inset-divider convention: 16dp content
 * start padding + 24dp icon + 16dp icon-to-text gap = 56dp is the minimum; 72dp is the standard
 * value M3 list specs converge on when the leading element is treated as occupying a 40dp slot.
 */
val ResourceListDividerInset = 72.dp
