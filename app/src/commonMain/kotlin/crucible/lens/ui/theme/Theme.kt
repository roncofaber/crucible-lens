package crucible.lens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import crucible.lens.platform.resolveDynamicColorScheme
import crucible.lens.ui.theme.accents.CarmineDarkHigh
import crucible.lens.ui.theme.accents.CarmineDarkMedium
import crucible.lens.ui.theme.accents.CarmineDarkStandard
import crucible.lens.ui.theme.accents.CarmineLightHigh
import crucible.lens.ui.theme.accents.CarmineLightMedium
import crucible.lens.ui.theme.accents.CarmineLightStandard
import crucible.lens.ui.theme.accents.CeruleanDarkHigh
import crucible.lens.ui.theme.accents.CeruleanDarkMedium
import crucible.lens.ui.theme.accents.CeruleanDarkStandard
import crucible.lens.ui.theme.accents.CeruleanLightHigh
import crucible.lens.ui.theme.accents.CeruleanLightMedium
import crucible.lens.ui.theme.accents.CeruleanLightStandard
import crucible.lens.ui.theme.accents.EmeraldDarkHigh
import crucible.lens.ui.theme.accents.EmeraldDarkMedium
import crucible.lens.ui.theme.accents.EmeraldDarkStandard
import crucible.lens.ui.theme.accents.EmeraldLightHigh
import crucible.lens.ui.theme.accents.EmeraldLightMedium
import crucible.lens.ui.theme.accents.EmeraldLightStandard
import crucible.lens.ui.theme.accents.EvergreenDarkHigh
import crucible.lens.ui.theme.accents.EvergreenDarkMedium
import crucible.lens.ui.theme.accents.EvergreenDarkStandard
import crucible.lens.ui.theme.accents.EvergreenLightHigh
import crucible.lens.ui.theme.accents.EvergreenLightMedium
import crucible.lens.ui.theme.accents.EvergreenLightStandard
import crucible.lens.ui.theme.accents.FlamingoDarkHigh
import crucible.lens.ui.theme.accents.FlamingoDarkMedium
import crucible.lens.ui.theme.accents.FlamingoDarkStandard
import crucible.lens.ui.theme.accents.FlamingoLightHigh
import crucible.lens.ui.theme.accents.FlamingoLightMedium
import crucible.lens.ui.theme.accents.FlamingoLightStandard
import crucible.lens.ui.theme.accents.MidnightDarkHigh
import crucible.lens.ui.theme.accents.MidnightDarkMedium
import crucible.lens.ui.theme.accents.MidnightDarkStandard
import crucible.lens.ui.theme.accents.MidnightLightHigh
import crucible.lens.ui.theme.accents.MidnightLightMedium
import crucible.lens.ui.theme.accents.MidnightLightStandard
import crucible.lens.ui.theme.accents.MimosaDarkHigh
import crucible.lens.ui.theme.accents.MimosaDarkMedium
import crucible.lens.ui.theme.accents.MimosaDarkStandard
import crucible.lens.ui.theme.accents.MimosaLightHigh
import crucible.lens.ui.theme.accents.MimosaLightMedium
import crucible.lens.ui.theme.accents.MimosaLightStandard
import crucible.lens.ui.theme.accents.MochaDarkHigh
import crucible.lens.ui.theme.accents.MochaDarkMedium
import crucible.lens.ui.theme.accents.MochaDarkStandard
import crucible.lens.ui.theme.accents.MochaLightHigh
import crucible.lens.ui.theme.accents.MochaLightMedium
import crucible.lens.ui.theme.accents.MochaLightStandard
import crucible.lens.ui.theme.accents.OnyxDarkHigh
import crucible.lens.ui.theme.accents.OnyxDarkMedium
import crucible.lens.ui.theme.accents.OnyxDarkStandard
import crucible.lens.ui.theme.accents.OnyxLightHigh
import crucible.lens.ui.theme.accents.OnyxLightMedium
import crucible.lens.ui.theme.accents.OnyxLightStandard
import crucible.lens.ui.theme.accents.PlumDarkHigh
import crucible.lens.ui.theme.accents.PlumDarkMedium
import crucible.lens.ui.theme.accents.PlumDarkStandard
import crucible.lens.ui.theme.accents.PlumLightHigh
import crucible.lens.ui.theme.accents.PlumLightMedium
import crucible.lens.ui.theme.accents.PlumLightStandard
import crucible.lens.ui.theme.accents.PumpkinDarkHigh
import crucible.lens.ui.theme.accents.PumpkinDarkMedium
import crucible.lens.ui.theme.accents.PumpkinDarkStandard
import crucible.lens.ui.theme.accents.PumpkinLightHigh
import crucible.lens.ui.theme.accents.PumpkinLightMedium
import crucible.lens.ui.theme.accents.PumpkinLightStandard
import crucible.lens.ui.theme.accents.VermillonDarkHigh
import crucible.lens.ui.theme.accents.VermillonDarkMedium
import crucible.lens.ui.theme.accents.VermillonDarkStandard
import crucible.lens.ui.theme.accents.VermillonLightHigh
import crucible.lens.ui.theme.accents.VermillonLightMedium
import crucible.lens.ui.theme.accents.VermillonLightStandard

/**
 * Picker swatch per accent — each named accent's own `primary` (light, standard contrast) value,
 * shown as the colored square in Settings → Appearance. Not a generation seed: every role for
 * every accent/contrast/theme-mode combination is a fully hand-curated static [ColorScheme] in
 * `ui/theme/accents/`, exported from the Material Theme Builder
 * (material-foundation.github.io/material-theme-builder), not generated at runtime.
 *
 * Ordered around the hue wheel (red → orange → yellow → green → cyan → blue → purple → pink, then
 * carmine as the deep red-magenta bridge back toward the start) rather than alphabetically by
 * codename, so the picker grid reads as a logical spectrum instead of a random assortment.
 */
internal val accentColorPalette: List<Pair<String, Color>> = listOf(
    "vermillon" to Color(0xFFBB0603),
    "pumpkin"   to Color(0xFF9E4300),
    "mocha"     to Color(0xFF4E2700),
    "mimosa"    to Color(0xFF7C5800),
    "emerald"   to Color(0xFF006D36),
    "evergreen" to Color(0xFF002F19),
    "onyx"      to Color(0xFF202324),
    "cerulean"  to Color(0xFF006184),
    "midnight"  to Color(0xFF121142),
    "plum"      to Color(0xFF722D6C),
    "flamingo"  to Color(0xFF9A3F5C),
    "carmine"   to Color(0xFF6B000E),
)

internal fun accentColorToColor(colorName: String): Color =
    accentColorPalette.firstOrNull { it.first == colorName.lowercase() }?.second
        ?: accentColorPalette.first().second

@Composable
fun CrucibleScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: String = "carmine",
    accentContrast: String = "standard",
    content: @Composable () -> Unit
) {
    val colorScheme = resolveDynamicColorScheme(darkTheme).takeIf { dynamicColor }
        ?: resolveAccentColorScheme(accentColor, accentContrast, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * Resolves the non-dynamic-colour scheme for a given accent, contrast level, and theme mode — the
 * exact logic [CrucibleScannerTheme] uses. Every combination is a static, hand-curated
 * [ColorScheme] (see `ui/theme/accents/`) — there is no runtime color generation in this app.
 */
internal fun resolveAccentColorScheme(
    accentColor: String,
    accentContrast: String,
    darkTheme: Boolean
): ColorScheme = when (accentColor.lowercase()) {
    "vermillon" -> when (accentContrast) {
        "medium" -> if (darkTheme) VermillonDarkMedium else VermillonLightMedium
        "high" -> if (darkTheme) VermillonDarkHigh else VermillonLightHigh
        else -> if (darkTheme) VermillonDarkStandard else VermillonLightStandard
    }
    "pumpkin" -> when (accentContrast) {
        "medium" -> if (darkTheme) PumpkinDarkMedium else PumpkinLightMedium
        "high" -> if (darkTheme) PumpkinDarkHigh else PumpkinLightHigh
        else -> if (darkTheme) PumpkinDarkStandard else PumpkinLightStandard
    }
    "mocha" -> when (accentContrast) {
        "medium" -> if (darkTheme) MochaDarkMedium else MochaLightMedium
        "high" -> if (darkTheme) MochaDarkHigh else MochaLightHigh
        else -> if (darkTheme) MochaDarkStandard else MochaLightStandard
    }
    "mimosa" -> when (accentContrast) {
        "medium" -> if (darkTheme) MimosaDarkMedium else MimosaLightMedium
        "high" -> if (darkTheme) MimosaDarkHigh else MimosaLightHigh
        else -> if (darkTheme) MimosaDarkStandard else MimosaLightStandard
    }
    "emerald" -> when (accentContrast) {
        "medium" -> if (darkTheme) EmeraldDarkMedium else EmeraldLightMedium
        "high" -> if (darkTheme) EmeraldDarkHigh else EmeraldLightHigh
        else -> if (darkTheme) EmeraldDarkStandard else EmeraldLightStandard
    }
    "evergreen" -> when (accentContrast) {
        "medium" -> if (darkTheme) EvergreenDarkMedium else EvergreenLightMedium
        "high" -> if (darkTheme) EvergreenDarkHigh else EvergreenLightHigh
        else -> if (darkTheme) EvergreenDarkStandard else EvergreenLightStandard
    }
    "onyx" -> when (accentContrast) {
        "medium" -> if (darkTheme) OnyxDarkMedium else OnyxLightMedium
        "high" -> if (darkTheme) OnyxDarkHigh else OnyxLightHigh
        else -> if (darkTheme) OnyxDarkStandard else OnyxLightStandard
    }
    "cerulean" -> when (accentContrast) {
        "medium" -> if (darkTheme) CeruleanDarkMedium else CeruleanLightMedium
        "high" -> if (darkTheme) CeruleanDarkHigh else CeruleanLightHigh
        else -> if (darkTheme) CeruleanDarkStandard else CeruleanLightStandard
    }
    "midnight" -> when (accentContrast) {
        "medium" -> if (darkTheme) MidnightDarkMedium else MidnightLightMedium
        "high" -> if (darkTheme) MidnightDarkHigh else MidnightLightHigh
        else -> if (darkTheme) MidnightDarkStandard else MidnightLightStandard
    }
    "plum" -> when (accentContrast) {
        "medium" -> if (darkTheme) PlumDarkMedium else PlumLightMedium
        "high" -> if (darkTheme) PlumDarkHigh else PlumLightHigh
        else -> if (darkTheme) PlumDarkStandard else PlumLightStandard
    }
    "flamingo" -> when (accentContrast) {
        "medium" -> if (darkTheme) FlamingoDarkMedium else FlamingoLightMedium
        "high" -> if (darkTheme) FlamingoDarkHigh else FlamingoLightHigh
        else -> if (darkTheme) FlamingoDarkStandard else FlamingoLightStandard
    }
    else -> when (accentContrast) {
        "medium" -> if (darkTheme) CarmineDarkMedium else CarmineLightMedium
        "high" -> if (darkTheme) CarmineDarkHigh else CarmineLightHigh
        else -> if (darkTheme) CarmineDarkStandard else CarmineLightStandard
    }
}

/**
 * Readable foreground for a colour that isn't a scheme role.
 *
 * Everything themed should pair a scheme role with its `onX` counterpart — that is what guarantees
 * contrast. A handful of surfaces can't: the avatar circle takes a hue generated from an ORCID, and
 * the accent picker paints raw swatches. There is no `onX` for those, and hardcoding white fails on
 * the light end of the range.
 *
 * Choosing by relative luminance keeps both readable across the whole palette. Use this *only* for
 * genuinely non-scheme backgrounds; for anything themed, use the matching `on` role instead.
 */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
