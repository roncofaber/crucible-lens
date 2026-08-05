package crucible.lens.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.detail.components.ResourceRow
import crucible.lens.ui.theme.emphasizedTitleMedium
import crucible.lens.ui.theme.resolveAccentColorScheme

private val COMPARISON_STYLES = listOf(
    "TonalSpot" to PaletteStyle.TonalSpot,
    "Neutral" to PaletteStyle.Neutral,
    "Vibrant" to PaletteStyle.Vibrant,
    "Expressive" to PaletteStyle.Expressive,
    "Rainbow" to PaletteStyle.Rainbow,
    "FruitSalad" to PaletteStyle.FruitSalad,
    "Monochrome" to PaletteStyle.Monochrome,
    "Fidelity" to PaletteStyle.Fidelity,
    "Content" to PaletteStyle.Content,
)

/**
 * Debug-only comparison tool: renders the same representative UI chunk (a section header, a card
 * with nested tappable rows, a button, and a link) once under this app's current
 * accent-into-surface system ([resolveAccentColorScheme]), then once per MaterialKolor
 * [PaletteStyle], all seeded from the same resolved primary colour. Both panels use the *real*
 * [SectionHeader] and [ResourceRow] composables, not mocks, so the comparison reflects actual
 * production rendering. See docs/superpowers/specs/2026-08-05-theme-color-system-design.md.
 *
 * Purely a Phase 1 prototyping tool - it doesn't change any production colour, and its own fate
 * (deleted vs. kept as a permanent debug screen) is a Phase 2 decision.
 */
@Composable
fun ThemePreviewScreen(
    currentAccentColor: String,
    darkTheme: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val currentScheme = resolveAccentColorScheme(currentAccentColor, "tonal_spot", darkTheme)
    val seed = currentScheme.primary

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Theme Preview",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ThemePreviewPanel(label = "Current", colorScheme = currentScheme)

            COMPARISON_STYLES.forEach { (label, style) ->
                HorizontalDivider(thickness = 4.dp)
                val scheme2021 = rememberDynamicColorScheme(
                    seedColor = seed,
                    isDark = darkTheme,
                    style = style,
                    specVersion = ColorSpec.SpecVersion.SPEC_2021
                )
                ThemePreviewPanel(label = "$label (2021)", colorScheme = scheme2021)

                HorizontalDivider(thickness = 4.dp)
                val scheme2025 = rememberDynamicColorScheme(
                    seedColor = seed,
                    isDark = darkTheme,
                    style = style,
                    specVersion = ColorSpec.SpecVersion.SPEC_2025
                )
                ThemePreviewPanel(label = "$label (2025)", colorScheme = scheme2025)
            }
        }
    }
}

@Composable
private fun ThemePreviewPanel(label: String, colorScheme: ColorScheme) {
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.emphasizedTitleMedium)

                SectionHeader(
                    title = "Parent Samples",
                    count = 2,
                    icon = AppIcons.ParentResource
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ResourceRow(name = "SS00005", onClick = {})
                        ResourceRow(name = "SS00011", onClick = {})
                    }
                }

                Button(onClick = {}) { Text("Sample action") }

                Text(
                    "Learn more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {}
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Secondary",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Tertiary",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    )
                }
            }
        }
    }
}
