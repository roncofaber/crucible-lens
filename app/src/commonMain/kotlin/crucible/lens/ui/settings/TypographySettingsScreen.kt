@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.theme.emphasizedTitleLarge
import crucible.lens.ui.theme.emphasizedTitleMedium

@Composable
fun TypographySettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Typography",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("The ramp", style = MaterialTheme.typography.titleMedium)

            RampSample(
                title = "Expanded bar hero",
                text = "Expanded collapsing-bar hero",
                style = MaterialTheme.typography.emphasizedTitleLarge,
                caption = "emphasizedTitleLarge · 22sp Medium"
            )

            RampSample(
                title = "Surface title",
                text = "App bars and sheet titles",
                style = MaterialTheme.typography.titleLarge,
                caption = "titleLarge · 22sp Regular"
            )

            RampSample(
                title = "Card / dialog heading",
                text = "Card title or heading",
                style = MaterialTheme.typography.emphasizedTitleMedium,
                caption = "emphasizedTitleMedium · 16sp Bold"
            )

            RampSample(
                title = "Group header",
                text = "Section headers with container",
                style = MaterialTheme.typography.titleMedium,
                caption = "titleMedium · 16sp Medium"
            )

            RampSample(
                title = "Text input",
                text = "Form fields and editable content",
                style = MaterialTheme.typography.bodyLarge,
                caption = "bodyLarge · 16sp Regular"
            )

            RampSample(
                title = "Row title / body copy",
                text = "Primary content and list row titles",
                style = MaterialTheme.typography.bodyMedium,
                caption = "bodyMedium · 14sp Regular"
            )

            RampSample(
                title = "Button / chip label",
                text = "Action buttons and controls",
                style = MaterialTheme.typography.labelLarge,
                caption = "labelLarge · 14sp Medium"
            )

            RampSample(
                title = "Secondary / caption / ID",
                text = "Metadata, subtitles, and secondary text",
                style = MaterialTheme.typography.bodySmall,
                caption = "bodySmall · 12sp Regular"
            )

            RampSample(
                title = "Inline section label",
                text = "Labels for collapsed sections",
                style = MaterialTheme.typography.labelMedium,
                caption = "labelMedium · 12sp Medium"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("In context", style = MaterialTheme.typography.titleMedium)

            GroupHeaderExample()
            CardExample()
            InlineLabelExample()
            TextInputExample()
            TopBarAndBodyExample()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RampSample(
    title: String,
    text: String,
    style: TextStyle,
    caption: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = style)
        Text(
            "$title · $caption",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupHeaderExample() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(AppIcons.Sample, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Sample Group", style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "3",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        repeat(3) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sample ${i + 1}", style = MaterialTheme.typography.bodyMedium)
                Text("smp_abc${(i + 1).toString().padStart(3, '0')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < 2) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun CardExample() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dataset Details", style = MaterialTheme.typography.emphasizedTitleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRowExample(label = "Type", value = "X-ray Absorption Spectroscopy")
                InfoRowExample(label = "Created", value = "2024-08-15")
                InfoRowExample(label = "Status", value = "Published")
            }
        }
    }
}

@Composable
private fun InfoRowExample(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InlineLabelExample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Browse samples and datasets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {}) {
                Text("Browse")
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {}) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun TextInputExample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val textValue = remember { mutableStateOf("Sample value") }
        OutlinedTextField(
            value = textValue.value,
            onValueChange = { textValue.value = it },
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Inherits bodyLarge with no override",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopBarAndBodyExample() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Resources", style = MaterialTheme.typography.titleLarge)
        Text(
            "This page demonstrates the condensed type scale by rendering specimens of each sanctioned treatment in actual use. Compare with real screens to verify the ramp supports your content's hierarchy.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
