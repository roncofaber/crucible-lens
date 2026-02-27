package crucible.lens.ui.theme

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

// Shared onPrimary values:
// Dark schemes have light/pastel primaries → dark text on top
// Light schemes have dark/saturated primaries → white text on top
private val onPrimaryDark  = Color(0xFF1C1B1F)
private val onPrimaryLight = Color.White

// Blue theme
private val BlueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val BlueLightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Purple theme
private val PurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCE93D8), onPrimary = onPrimaryDark,
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)
private val PurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF9C27B0), onPrimary = onPrimaryLight,
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Green theme
private val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7), onPrimary = onPrimaryDark,
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFCE93D8)
)
private val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF388E3C), onPrimary = onPrimaryLight,
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF9C27B0)
)

// Orange theme
private val OrangeDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val OrangeLightColorScheme = lightColorScheme(
    primary = Color(0xFFF57C00), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Red theme
private val RedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFEF5350), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val RedLightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Teal theme
private val TealDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC), onPrimary = onPrimaryDark,
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFA5D6A7)
)
private val TealLightColorScheme = lightColorScheme(
    primary = Color(0xFF00796B), onPrimary = onPrimaryLight,
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFF388E3C)
)

// Pink theme
private val PinkDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF48FB1), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val PinkLightColorScheme = lightColorScheme(
    primary = Color(0xFFE91E63), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Indigo theme
private val IndigoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7986CB), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val IndigoLightColorScheme = lightColorScheme(
    primary = Color(0xFF3F51B5), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Amber theme
private val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD54F), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFFFFA000), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
)

// Brown theme
private val BrownDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA1887F), onPrimary = onPrimaryDark,
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7)
)
private val BrownLightColorScheme = lightColorScheme(
    primary = Color(0xFF5D4037), onPrimary = onPrimaryLight,
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFF388E3C)
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
            if (darkTheme) {
                darkColorScheme(
                    primary = customColor, onPrimary = onPrimaryDark,
                    secondary = Color(0xFF80DEEA),
                    tertiary = Color(0xFFA5D6A7)
                )
            } else {
                lightColorScheme(
                    primary = customColor, onPrimary = onPrimaryLight,
                    secondary = Color(0xFF0097A7),
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
                "brown" -> if (darkTheme) BrownDarkColorScheme else BrownLightColorScheme
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
