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
import crucible.lens.platform.resolveDynamicColorScheme

private val onPrimaryDark  = Color(0xFF1C1B1F)
private val onPrimaryLight = Color.White

// Blue
private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF0D47A1), onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFBBDEFB), onPrimaryContainer = Color(0xFF002171),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Purple
private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCE93D8), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF4A148C), onPrimaryContainer = Color(0xFFF3E5F5),
    secondary = Color(0xFF90CAF9), tertiary = Color(0xFFA5D6A7)
)
private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF9C27B0), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFE1BEE7), onPrimaryContainer = Color(0xFF38006B),
    secondary = Color(0xFF1976D2), tertiary = Color(0xFF388E3C)
)

// Green
private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFF90CAF9), tertiary = Color(0xFFCE93D8)
)
private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1976D2), tertiary = Color(0xFF9C27B0)
)

// Orange
private val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFFE65100), onPrimaryContainer = Color(0xFFFFF3E0),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val OrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFFF57C00), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFFFE0B2), onPrimaryContainer = Color(0xFFE65100),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Red
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF5350), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFFB71C1C), onPrimaryContainer = Color(0xFFFFEBEE),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFFFCDD2), onPrimaryContainer = Color(0xFFB71C1C),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Teal
private val TealDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF004D40), onPrimaryContainer = Color(0xFFE0F2F1),
    secondary = Color(0xFF90CAF9), tertiary = Color(0xFFA5D6A7)
)
private val TealLightColorScheme = lightColorScheme(
    primary = Color(0xFF00796B), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFB2DFDB), onPrimaryContainer = Color(0xFF004D40),
    secondary = Color(0xFF1976D2), tertiary = Color(0xFF388E3C)
)

// Pink
private val PinkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF880E4F), onPrimaryContainer = Color(0xFFFCE4EC),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val PinkLightColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFFCE4EC), onPrimaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Indigo
private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7986CB), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF1A237E), onPrimaryContainer = Color(0xFFE8EAF6),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFC5CAE9), onPrimaryContainer = Color(0xFF1A237E),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Amber
private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD54F), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFFFF6F00), onPrimaryContainer = Color(0xFFFFF8E1),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFFFFA000), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFFFECB3), onPrimaryContainer = Color(0xFFFF6F00),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

// Brown
private val BrownDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA1887F), onPrimary = onPrimaryDark,
    primaryContainer = Color(0xFF3E2723), onPrimaryContainer = Color(0xFFEFEBE9),
    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
)
private val BrownLightColorScheme = lightColorScheme(
    primary = Color(0xFF5D4037), onPrimary = onPrimaryLight,
    primaryContainer = Color(0xFFD7CCC8), onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
)

/**
 * Rebuilds the five `surfaceContainer*` roles so they carry the accent.
 *
 * M3 expresses elevation as tonal colour, not shadow: pinned chrome is supposed to sit on a
 * container role that is the surface with a little of the primary blended in. The schemes above
 * only ever set primary/secondary/tertiary, so every surface role fell through to M3's baseline —
 * which is generated from a *purple* seed. A blue-accented app therefore rendered purple-grey
 * containers that belonged to no palette in the app.
 *
 * Blending a little primary into surface restores that relationship for all ten named palettes and
 * the custom-hex path in one place, so `surfaceContainer` becomes the correct thing to reach for
 * rather than something to work around. Dynamic colour is excluded at the call site: it already
 * derives a full tonal palette from the wallpaper.
 *
 * The ratios are deliberately small. M3's own container steps move *lightness* within a
 * near-neutral palette (roughly 4–6 chroma); a straight blend toward a full-chroma primary is a
 * much stronger effect at the same nominal percentage. The first version of this used 2–12% and
 * visibly washed the whole expanded `SearchBar` — which takes `surfaceContainerHigh` — in the
 * accent. These values give the hue a hint of the accent without turning large surfaces into
 * coloured panels.
 */
private fun withAccentSurfaces(scheme: ColorScheme): ColorScheme = with(scheme) {
    copy(
        surfaceContainerLowest = lerp(surface, primary, 0.010f),
        surfaceContainerLow = lerp(surface, primary, 0.020f),
        surfaceContainer = lerp(surface, primary, 0.035f),
        surfaceContainerHigh = lerp(surface, primary, 0.050f),
        surfaceContainerHighest = lerp(surface, primary, 0.065f)
    )
}

@Composable
fun CrucibleScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: String = "blue",
    content: @Composable () -> Unit
) {
    // Dynamic colour already derives a full tonal palette from the wallpaper, so it is used as-is.
    // Everything else goes through resolveAccentColorScheme() (withAccentSurfaces() internally) —
    // see that function's KDoc. Extracted to a standalone function (not inlined here) so the debug
    // Theme Preview screen (ui/settings/ThemePreviewScreen.kt) can resolve "today's real scheme"
    // for a given accent/theme without duplicating this logic.
    val colorScheme = resolveDynamicColorScheme(darkTheme).takeIf { dynamicColor }
        ?: resolveAccentColorScheme(accentColor, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * Resolves the non-dynamic-colour scheme for a given accent choice (named palette or `#hex`) and
 * theme mode — the exact logic [CrucibleScannerTheme] uses, extracted so [ThemePreviewScreen]
 * (`ui/settings/ThemePreviewScreen.kt`) can call the same resolution for its "Current" comparison
 * panel instead of re-deriving it and risking drift from production.
 */
internal fun resolveAccentColorScheme(accentColor: String, darkTheme: Boolean): ColorScheme =
    withAccentSurfaces(when {
        accentColor.startsWith("#") -> {
            val c = try {
                Color(parseHexColor(accentColor))
            } catch (_: Exception) {
                Color(0xFF1976D2)
            }
            if (darkTheme) {
                darkColorScheme(
                    primary = c, onPrimary = onPrimaryDark,
                    primaryContainer = Color(c.red * 0.35f, c.green * 0.35f, c.blue * 0.35f),
                    onPrimaryContainer = Color(c.red * 0.1f + 0.9f, c.green * 0.1f + 0.9f, c.blue * 0.1f + 0.9f),
                    secondary = Color(0xFF80DEEA), tertiary = Color(0xFFA5D6A7)
                )
            } else {
                lightColorScheme(
                    primary = c, onPrimary = onPrimaryLight,
                    primaryContainer = Color(c.red * 0.15f + 0.85f, c.green * 0.15f + 0.85f, c.blue * 0.15f + 0.85f),
                    onPrimaryContainer = Color(c.red * 0.45f, c.green * 0.45f, c.blue * 0.45f),
                    secondary = Color(0xFF0097A7), tertiary = Color(0xFF388E3C)
                )
            }
        }
        else -> when (accentColor.lowercase()) {
            "purple" -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
            "green"  -> if (darkTheme) GreenDarkColorScheme  else GreenLightColorScheme
            "orange" -> if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
            "red"    -> if (darkTheme) RedDarkColorScheme    else RedLightColorScheme
            "teal"   -> if (darkTheme) TealDarkColorScheme   else TealLightColorScheme
            "pink"   -> if (darkTheme) PinkDarkColorScheme   else PinkLightColorScheme
            "indigo" -> if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
            "amber"  -> if (darkTheme) AmberDarkColorScheme  else AmberLightColorScheme
            "brown"  -> if (darkTheme) BrownDarkColorScheme  else BrownLightColorScheme
            else     -> if (darkTheme) BlueDarkColorScheme   else BlueLightColorScheme
        }
    })

private fun parseHexColor(hex: String): Long {
    val cleanHex = hex.removePrefix("#")
    return cleanHex.toLong(16)
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
