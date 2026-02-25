package gov.lbl.crucible.scanner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Blue theme
private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7)
)

private val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF388E3C)
)

// Purple theme
private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCE93D8),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF9C27B0),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Green theme
private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFCE93D8)
)

private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF9C27B0)
)

// Orange theme
private val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val OrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFFF57C00),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Red theme
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF5350),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Teal theme
private val TealDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val TealLightColorScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Pink theme
private val PinkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val PinkLightColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Indigo theme
private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7986CB),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Amber theme
private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD54F),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFFFFA000),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Lime theme
private val LimeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFDCE775),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val LimeLightColorScheme = lightColorScheme(
    primary = Color(0xFFAFB42B),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Cyan theme
private val CyanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val CyanLightColorScheme = lightColorScheme(
    primary = Color(0xFF0097A7),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Brown theme
private val BrownDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA1887F),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val BrownLightColorScheme = lightColorScheme(
    primary = Color(0xFF5D4037),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Deep Purple theme
private val DeepPurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9575CD),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val DeepPurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF512DA8),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Light Blue theme
private val LightBlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7)
)

private val LightBlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF388E3C)
)

// Light Green theme
private val LightGreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CCC65),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFCE93D8)
)

private val LightGreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF689F38),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF9C27B0)
)

// Deep Orange theme
private val DeepOrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF8A65),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val DeepOrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFFE64A19),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Blue Grey theme
private val BlueGreyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF78909C),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val BlueGreyLightColorScheme = lightColorScheme(
    primary = Color(0xFF455A64),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Yellow theme
private val YellowDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFDD835),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val YellowLightColorScheme = lightColorScheme(
    primary = Color(0xFFF9A825),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Magenta theme
private val MagentaDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEC407A),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)

private val MagentaLightColorScheme = lightColorScheme(
    primary = Color(0xFFAD1457),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Turquoise theme
private val TurquoiseDarkColorScheme = darkColorScheme(
    primary = Color(0xFF26A69A),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFCE93D8)
)

private val TurquoiseLightColorScheme = lightColorScheme(
    primary = Color(0xFF00897B),
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF9C27B0)
)

@Composable
fun CrucibleScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: String = "blue",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Check if it's a custom hex color
        accentColor.startsWith("#") -> {
            val customColor = try {
                Color(android.graphics.Color.parseColor(accentColor))
            } catch (e: Exception) {
                Color(0xFF1976D2) // Default to blue
            }
            // Create a custom color scheme with the hex color
            if (darkTheme) {
                darkColorScheme(
                    primary = customColor,
                    secondary = Color(0xFF90CAF9),
                    tertiary = Color(0xFFA5D6A7)
                )
            } else {
                lightColorScheme(
                    primary = customColor,
                    secondary = Color(0xFF1976D2),
                    tertiary = Color(0xFF388E3C)
                )
            }
        }
        else -> {
            when (accentColor.lowercase()) {
                "purple" -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
                "green" -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
                "orange" -> if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
                "red" -> if (darkTheme) RedDarkColorScheme else RedLightColorScheme
                "teal" -> if (darkTheme) TealDarkColorScheme else TealLightColorScheme
                "pink" -> if (darkTheme) PinkDarkColorScheme else PinkLightColorScheme
                "indigo" -> if (darkTheme) IndigoDarkColorScheme else IndigoLightColorScheme
                "amber" -> if (darkTheme) AmberDarkColorScheme else AmberLightColorScheme
                "lime" -> if (darkTheme) LimeDarkColorScheme else LimeLightColorScheme
                "cyan" -> if (darkTheme) CyanDarkColorScheme else CyanLightColorScheme
                "brown" -> if (darkTheme) BrownDarkColorScheme else BrownLightColorScheme
                "deeppurple" -> if (darkTheme) DeepPurpleDarkColorScheme else DeepPurpleLightColorScheme
                "lightblue" -> if (darkTheme) LightBlueDarkColorScheme else LightBlueLightColorScheme
                "lightgreen" -> if (darkTheme) LightGreenDarkColorScheme else LightGreenLightColorScheme
                "deeporange" -> if (darkTheme) DeepOrangeDarkColorScheme else DeepOrangeLightColorScheme
                "bluegrey" -> if (darkTheme) BlueGreyDarkColorScheme else BlueGreyLightColorScheme
                "yellow" -> if (darkTheme) YellowDarkColorScheme else YellowLightColorScheme
                "magenta" -> if (darkTheme) MagentaDarkColorScheme else MagentaLightColorScheme
                "turquoise" -> if (darkTheme) TurquoiseDarkColorScheme else TurquoiseLightColorScheme
                else -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
