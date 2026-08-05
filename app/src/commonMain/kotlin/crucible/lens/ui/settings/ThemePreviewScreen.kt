package crucible.lens.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.materialkolor.rememberDynamicColorScheme
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.detail.components.ResourceRow
import crucible.lens.ui.theme.emphasizedTitleMedium
import crucible.lens.ui.theme.resolveAccentColorScheme

/**
 * Debug-only comparison tool: renders the same representative UI chunk (a section header, a card
 * with nested tappable rows, a button, and a link) twice - once under this app's current
 * accent-into-surface system ([resolveAccentColorScheme]), once under a MaterialKolor-generated
 * scheme seeded from the same resolved primary colour. Both panels use the *real* [SectionHeader]
 * and [ResourceRow] composables, not mocks, so the comparison reflects actual production
 * rendering. See docs/superpowers/specs/2026-08-05-theme-color-system-design.md.
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
    val currentScheme = resolveAccentColorScheme(currentAccentColor, darkTheme)
    val generatedScheme = rememberDynamicColorScheme(
        seedColor = currentScheme.primary,
        isDark = darkTheme
    )

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
            HorizontalDivider(thickness = 4.dp)
            ThemePreviewPanel(label = "MaterialKolor-generated", colorScheme = generatedScheme)
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
            }
        }
    }
}
