package crucible.lens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Explicit M3 type scale (values match Compose Material3 1.4.0's TypographyTokens defaults —
// see https://m3.material.io/styles/typography/overview). Declared in full, rather than relying
// on Typography()'s internal defaults, so the app's type ramp is visible and intentional here
// rather than implicit. Every role uses the same family: M3's brand/plain split is for apps that
// have two typefaces, and this one doesn't. (1.5.0 adds a Typography(defaultFontFamily = …)
// constructor that would make this a single argument — worth adopting when CMP ships it.)
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// ── Emphasized type styles ───────────────────────────────────────────────────────────────────
//
// M3's type scale has 30 styles: the 15 baseline ones above, plus 15 "emphasized" ones added in
// the May 2025 Expressive update. An emphasized style is its baseline style at a heavier weight —
// same size, same line height, same tracking — and M3's guidance is to "swap the baseline token
// for the emphasized token of the same style" wherever text needs emphasis.
//
// Compose ships them, but not to us. They were added during 1.4.0-alpha, then removed from the
// stable branch: 1.4.0-beta01's notes say every `ExperimentalMaterial3ExpressiveApi` symbol "has
// been removed, please switch to 1.5.0-alpha". The tokens survive internally, and all 15
// accessors on `Typography` are `internal val`, so `MaterialTheme.typography.titleMediumEmphasized`
// does not compile against the version CMP 1.10.x resolves. 1.5.0 is at alpha25 with no beta, and
// forcing it in a KMP project would desync Android from iOS — not worth it for a naming change.
//
// So these derive the same styles locally, by M3's own rule, from our own baseline.
// Weights are M3's: Display/Headline/Title-Large/Body emphasize to Medium, Title-Medium/Small and
// all Labels to Bold. Note SemiBold appears nowhere in M3; if you reach for it, you want one of
// these instead.
//
// When CMP ships a stable M3 that exposes them, delete this block. Call sites already use the
// official names, so nothing else changes.

val Typography.emphasizedDisplayLarge: TextStyle get() = displayLarge.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedDisplayMedium: TextStyle get() = displayMedium.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedDisplaySmall: TextStyle get() = displaySmall.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedHeadlineLarge: TextStyle get() = headlineLarge.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedHeadlineMedium: TextStyle get() = headlineMedium.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedHeadlineSmall: TextStyle get() = headlineSmall.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedTitleLarge: TextStyle get() = titleLarge.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedTitleMedium: TextStyle get() = titleMedium.copy(fontWeight = FontWeight.Bold)
val Typography.emphasizedTitleSmall: TextStyle get() = titleSmall.copy(fontWeight = FontWeight.Bold)
val Typography.emphasizedBodyLarge: TextStyle get() = bodyLarge.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedBodyMedium: TextStyle get() = bodyMedium.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedBodySmall: TextStyle get() = bodySmall.copy(fontWeight = FontWeight.Medium)
val Typography.emphasizedLabelLarge: TextStyle get() = labelLarge.copy(fontWeight = FontWeight.Bold)
val Typography.emphasizedLabelMedium: TextStyle get() = labelMedium.copy(fontWeight = FontWeight.Bold)
val Typography.emphasizedLabelSmall: TextStyle get() = labelSmall.copy(fontWeight = FontWeight.Bold)
