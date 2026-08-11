@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.platform.supportsDynamicColor
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.theme.accentColorPalette
import crucible.lens.ui.theme.accentColorToColor
import crucible.lens.ui.theme.readableOn

@Composable
fun AppearanceSettingsScreen(
    currentThemeMode: String,
    currentAccentColor: String,
    currentAccentContrast: String,
    currentFloatingScanButton: Boolean,
    currentUseDynamicColor: Boolean = false,
    currentDefaultProjectTab: String,
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    onAccentContrastSave: (String) -> Unit,
    onFloatingScanButtonSave: (Boolean) -> Unit,
    onUseDynamicColorSave: (Boolean) -> Unit = {},
    onDefaultProjectTabSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val dynamicColorSupported = supportsDynamicColor()
    var themeModeInput          by remember { mutableStateOf(currentThemeMode) }
    var accentColorInput        by remember { mutableStateOf(currentAccentColor) }
    var accentContrastInput     by remember { mutableStateOf(currentAccentContrast) }
    var floatingScanButtonInput by remember { mutableStateOf(currentFloatingScanButton) }
    var useDynamicColorInput    by remember { mutableStateOf(currentUseDynamicColor) }
    var defaultProjectTabInput  by remember { mutableStateOf(currentDefaultProjectTab) }
    var showColorPicker         by remember { mutableStateOf(false) }

    LaunchedEffect(currentThemeMode)         { themeModeInput         = currentThemeMode }
    LaunchedEffect(currentAccentColor)       { accentColorInput       = currentAccentColor }
    LaunchedEffect(currentAccentContrast)    { accentContrastInput    = currentAccentContrast }
    LaunchedEffect(currentFloatingScanButton){ floatingScanButtonInput = currentFloatingScanButton }
    LaunchedEffect(currentUseDynamicColor)   { useDynamicColorInput   = currentUseDynamicColor }
    LaunchedEffect(currentDefaultProjectTab) { defaultProjectTabInput = currentDefaultProjectTab }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Appearance",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Theme mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(AppIcons.DarkTheme, tint = MaterialTheme.colorScheme.primary)
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            FilterChip(
                                selected = themeModeInput == value,
                                onClick = { themeModeInput = value; onThemeModeSave(value) },
                                label = { Text(label) },
                                leadingIcon = if (themeModeInput == value) {
                                    { AppIcon(AppIcons.Selected, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                colors = settingsChipColors(),
                                border = settingsChipBorder(selected = themeModeInput == value)
                            )
                        }
                    }
                }
            }

            // Dynamic color — Android 12+ only
            if (dynamicColorSupported) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(AppIcons.ColorPicker, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Follow system wallpaper colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = useDynamicColorInput,
                            onCheckedChange = { useDynamicColorInput = it; onUseDynamicColorSave(it) }
                        )
                    }
                }
            }

            // Accent color — hidden when dynamic color is active
            if (!useDynamicColorInput) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showColorPicker = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(AppIcons.Appearance, tint = MaterialTheme.colorScheme.primary)
                            Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(accentColorToColor(accentColorInput), shape = MaterialTheme.shapes.small)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            )
                            AppIcon(AppIcons.NavigateNext)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(AppIcons.ColorPicker, tint = MaterialTheme.colorScheme.primary)
                            Text("Contrast", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "standard" to "Standard", "medium" to "Medium", "high" to "High"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = accentContrastInput == value,
                                    onClick = { accentContrastInput = value; onAccentContrastSave(value) },
                                    label = { Text(label) },
                                    leadingIcon = if (accentContrastInput == value) {
                                        { AppIcon(AppIcons.Selected, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f),
                                    colors = settingsChipColors(),
                                    border = settingsChipBorder(selected = accentContrastInput == value)
                                )
                            }
                        }
                    }
                }
            }

            // Floating scan button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(AppIcons.ScanQr, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Floating Scan Button", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Show a quick-scan FAB while browsing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = floatingScanButtonInput,
                        onCheckedChange = { floatingScanButtonInput = it; onFloatingScanButtonSave(it) }
                    )
                }
            }

            // Default project tab
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(AppIcons.Project, tint = MaterialTheme.colorScheme.primary)
                        Text("Default project tab", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Which tab opens first when browsing a project",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AppPreferences.PROJECT_TAB_SAMPLES to "Samples",
                            AppPreferences.PROJECT_TAB_DATASETS to "Datasets"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = defaultProjectTabInput == value,
                                onClick = { defaultProjectTabInput = value; onDefaultProjectTabSave(value) },
                                label = { Text(label) },
                                leadingIcon = if (defaultProjectTabInput == value) {
                                    { AppIcon(AppIcons.Selected, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                colors = settingsChipColors(),
                                border = settingsChipBorder(selected = defaultProjectTabInput == value)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    themeModeInput = "system";                              onThemeModeSave("system")
                    useDynamicColorInput = false;                           onUseDynamicColorSave(false)
                    accentColorInput = AppPreferences.DEFAULT_ACCENT_COLOR; onAccentColorSave(AppPreferences.DEFAULT_ACCENT_COLOR)
                    accentContrastInput = AppPreferences.DEFAULT_ACCENT_CONTRAST
                    onAccentContrastSave(AppPreferences.DEFAULT_ACCENT_CONTRAST)
                    floatingScanButtonInput = true;                         onFloatingScanButtonSave(true)
                    defaultProjectTabInput = AppPreferences.PROJECT_TAB_SAMPLES
                    onDefaultProjectTabSave(AppPreferences.PROJECT_TAB_SAMPLES)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(AppIcons.ResetToDefault, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults")
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = accentColorInput,
            onColorSelected = { color -> accentColorInput = color; onAccentColorSave(color) },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = accentColorPalette

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Accent Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                colors.chunked(4).forEach { rowColors ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { (name, color) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth().aspectRatio(1f)
                                    .background(color, shape = MaterialTheme.shapes.medium)
                                    .border(
                                        width = if (currentColor == name) 3.dp else 1.dp,
                                        color = if (currentColor == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clickable { onColorSelected(name); onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentColor == name) {
                                    AppIcon(AppIcons.Selected, tint = readableOn(color), modifier = Modifier.size(24.dp))
                                }
                            }
                            Text(
                                text = name.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
