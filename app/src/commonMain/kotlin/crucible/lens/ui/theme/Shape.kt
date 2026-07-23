package crucible.lens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Explicit M3 shape scale (values match Compose Material3 1.4.0's ShapeTokens defaults —
// see https://m3.material.io/styles/shape/overview). Declared in full, rather than relying on
// Shapes()'s internal defaults, so the app's shape scale is visible and intentional here rather
// than implicit — and so hardcoded RoundedCornerShape(N.dp) call sites elsewhere in the app can
// reference MaterialTheme.shapes.* instead of duplicating these values.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
