package crucible.lens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import crucible.lens.platform.resolveDynamicColorScheme

internal val accentColorPalette: List<Pair<String, Color>> = listOf(
    "blue"   to Color(0xFF1976D2), "indigo" to Color(0xFF3F51B5),
    "purple" to Color(0xFF9C27B0), "pink"   to Color(0xFFE91E63),
    "red"    to Color(0xFFD32F2F), "orange" to Color(0xFFF57C00),
    "amber"  to Color(0xFFFFA000), "green"  to Color(0xFF388E3C),
    "teal"   to Color(0xFF00796B), "brown"  to Color(0xFF5D4037),
)

internal fun accentColorToColor(colorName: String): Color {
    if (colorName.startsWith("#") && colorName.length == 7) {
        return try {
            val hex = colorName.substring(1).toLong(16)
            Color((0xFF000000L or hex).toInt())
        } catch (_: Exception) { Color(0xFF1976D2) }
    }
    return accentColorPalette.firstOrNull { it.first == colorName.lowercase() }?.second
        ?: Color(0xFF1976D2)
}

private val onPrimaryDark  = Color(0xFF1C1B1F)
private val onPrimaryLight = Color.White

@Composable
fun CrucibleScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: String = "blue",
    accentStyle: String = "tonal_spot",
    content: @Composable () -> Unit
) {
    val colorScheme = resolveDynamicColorScheme(darkTheme).takeIf { dynamicColor }
        ?: resolveAccentColorScheme(accentColor, accentStyle, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

private fun String.toPaletteStyle(): PaletteStyle = when (this) {
    "neutral" -> PaletteStyle.Neutral
    "vibrant" -> PaletteStyle.Vibrant
    "expressive" -> PaletteStyle.Expressive
    else -> PaletteStyle.TonalSpot
}

/**
 * Resolves the non-dynamic-colour scheme for a given accent choice (named palette or `#hex`),
 * style, and theme mode — the exact logic [CrucibleScannerTheme] uses.
 */
internal fun resolveAccentColorScheme(
    accentColor: String,
    accentStyle: String,
    darkTheme: Boolean
): ColorScheme {
    val seed = accentColorToColor(accentColor)
    return dynamicColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        style = accentStyle.toPaletteStyle(),
        specVersion = ColorSpec.SpecVersion.SPEC_2025
    )
}

/**
 * Readable foreground for a colour that isn't a scheme role.
 *
 * Everything themed should pair a scheme role with its `onX` counterpart — that is what guarantees
 * contrast. A handful of surfaces can't: the avatar circle takes a hue generated from an ORCID, and
 * the accent picker paints raw seed swatches. There is no `onX` for those, and hardcoding white
 * fails on the light end of the range — white on the amber seed is roughly 2:1, well under the 4.5:1
 * M3 asks for on small text.
 *
 * Choosing by relative luminance keeps both readable across the whole palette. Use this *only* for
 * genuinely non-scheme backgrounds; for anything themed, use the matching `on` role instead.
 */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
