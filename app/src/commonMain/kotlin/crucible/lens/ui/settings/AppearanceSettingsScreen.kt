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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.platform.supportsDynamicColor
import crucible.lens.ui.common.AppScaffold

@Composable
fun AppearanceSettingsScreen(
    currentThemeMode: String,
    currentAccentColor: String,
    currentFloatingScanButton: Boolean,
    currentUseDynamicColor: Boolean = false,
    currentDefaultProjectTab: String,
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    onFloatingScanButtonSave: (Boolean) -> Unit,
    onUseDynamicColorSave: (Boolean) -> Unit = {},
    onDefaultProjectTabSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val dynamicColorSupported = supportsDynamicColor()
    var themeModeInput          by remember { mutableStateOf(currentThemeMode) }
    var accentColorInput        by remember { mutableStateOf(currentAccentColor) }
    var floatingScanButtonInput by remember { mutableStateOf(currentFloatingScanButton) }
    var useDynamicColorInput    by remember { mutableStateOf(currentUseDynamicColor) }
    var defaultProjectTabInput  by remember { mutableStateOf(currentDefaultProjectTab) }
    var showColorPicker         by remember { mutableStateOf(false) }

    LaunchedEffect(currentThemeMode)         { themeModeInput         = currentThemeMode }
    LaunchedEffect(currentAccentColor)       { accentColorInput       = currentAccentColor }
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
            Card(modifier = Modifier.fillMaxWidth()) {
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
                Card(modifier = Modifier.fillMaxWidth()) {
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
                Card(modifier = Modifier.fillMaxWidth().clickable { showColorPicker = true }) {
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
            }

            // Floating scan button
            Card(modifier = Modifier.fillMaxWidth()) {
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
            Card(modifier = Modifier.fillMaxWidth()) {
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
                    accentColorInput = "blue";                              onAccentColorSave("blue")
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
private fun settingsChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun settingsChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    borderColor = MaterialTheme.colorScheme.outline,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.5.dp,
    enabled = true,
    selected = selected
)

@Composable
private fun ColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "blue"   to Color(0xFF1976D2), "indigo" to Color(0xFF3F51B5),
        "purple" to Color(0xFF9C27B0), "pink"   to Color(0xFFE91E63),
        "red"    to Color(0xFFD32F2F), "orange" to Color(0xFFF57C00),
        "amber"  to Color(0xFFFFA000), "green"  to Color(0xFF388E3C),
        "teal"   to Color(0xFF00796B), "brown"  to Color(0xFF5D4037)
    )
    var showCustomInput by remember { mutableStateOf(false) }
    var customHex by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Accent Color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                colors.chunked(5).forEach { rowColors ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { (name, color) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f).aspectRatio(1f)
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
                                    AppIcon(AppIcons.Selected, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!showCustomInput) {
                    OutlinedButton(onClick = { showCustomInput = true }, modifier = Modifier.fillMaxWidth()) {
                        AppIcon(AppIcons.Appearance)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Color (Hex)")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customHex,
                            onValueChange = { customHex = it.uppercase() },
                            label = { Text("Hex Color") },
                            placeholder = { Text("1976D2") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            prefix = { Text("#") },
                            isError = customHex.isNotEmpty() && !isValidHex(customHex)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showCustomInput = false; customHex = "" },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            Button(
                                onClick = { if (isValidHex(customHex)) { onColorSelected("#$customHex"); onDismiss() } },
                                enabled = isValidHex(customHex),
                                modifier = Modifier.weight(1f)
                            ) { Text("Apply") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showCustomInput) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

private fun isValidHex(hex: String): Boolean =
    hex.length == 6 && hex.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }

internal fun accentColorToColor(colorName: String): Color {
    if (colorName.startsWith("#") && colorName.length == 7) {
        return try {
            val hex = colorName.substring(1).toLong(16)
            Color((0xFF000000L or hex).toInt())
        } catch (_: Exception) { Color(0xFF1976D2) }
    }
    return when (colorName.lowercase()) {
        "blue"   -> Color(0xFF1976D2)
        "indigo" -> Color(0xFF3F51B5)
        "purple" -> Color(0xFF9C27B0)
        "pink"   -> Color(0xFFE91E63)
        "red"    -> Color(0xFFD32F2F)
        "orange" -> Color(0xFFF57C00)
        "amber"  -> Color(0xFFFFA000)
        "green"  -> Color(0xFF388E3C)
        "teal"   -> Color(0xFF00796B)
        "brown"  -> Color(0xFF5D4037)
        else     -> Color(0xFF1976D2)
    }
}
