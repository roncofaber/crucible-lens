package gov.lbl.crucible.scanner.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    currentThemeMode: String,
    currentAccentColor: String,
    currentSmoothAnimations: Boolean,
    currentFloatingScanButton: Boolean,
    onThemeModeSave: (String) -> Unit,
    onAccentColorSave: (String) -> Unit,
    onSmoothAnimationsSave: (Boolean) -> Unit,
    onFloatingScanButtonSave: (Boolean) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    // Snapshot of values at screen entry — used for hasChanges detection and revert
    val initialThemeMode    = remember { currentThemeMode }
    val initialAccentColor  = remember { currentAccentColor }
    val initialSmooth       = remember { currentSmoothAnimations }
    val initialFloating     = remember { currentFloatingScanButton }

    var themeModeInput       by remember { mutableStateOf(currentThemeMode) }
    var accentColorInput     by remember { mutableStateOf(currentAccentColor) }
    var smoothAnimationsInput by remember { mutableStateOf(currentSmoothAnimations) }
    var floatingScanButtonInput by remember { mutableStateOf(currentFloatingScanButton) }
    var showColorPicker      by remember { mutableStateOf(false) }
    var showLeaveDialog      by remember { mutableStateOf(false) }
    var pendingNavigation    by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val hasChanges = themeModeInput != initialThemeMode ||
        accentColorInput != initialAccentColor ||
        smoothAnimationsInput != initialSmooth ||
        floatingScanButtonInput != initialFloating

    // Helper — intercepts navigation when there are unsaved changes
    fun navigate(action: () -> Unit) {
        if (hasChanges) {
            pendingNavigation = action
            showLeaveDialog = true
        } else {
            action()
        }
    }

    // Hardware/gesture back button
    BackHandler(enabled = hasChanges) {
        pendingNavigation = onBack
        showLeaveDialog = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = { navigate(onBack) }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navigate(onHome) }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Already applied live — just show confirmation
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Appearance settings saved",
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                containerColor = if (hasChanges) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceVariant,
                contentColor   = if (hasChanges) MaterialTheme.colorScheme.onPrimary
                                 else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Text(
                "Changes apply immediately as you select them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Theme Mode
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                        )
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            FilterChip(
                                selected = themeModeInput == value,
                                onClick = {
                                    themeModeInput = value
                                    onThemeModeSave(value)
                                },
                                label = { Text(label) },
                                leadingIcon = if (themeModeInput == value) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderColor = MaterialTheme.colorScheme.secondary,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.5.dp,
                                    enabled = true,
                                    selected = themeModeInput == value
                                )
                            )
                        }
                    }
                }
            }

            // Accent Color
            Card(modifier = Modifier.clickable { showColorPicker = true }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Accent Color", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(accentColorToColor(accentColorInput), shape = MaterialTheme.shapes.small)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = "Choose color")
                    }
                }
            }

            // Smooth Animations
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Smooth Animations", style = MaterialTheme.typography.titleMedium)
                            Text("Disable for maximum speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = smoothAnimationsInput,
                        onCheckedChange = {
                            smoothAnimationsInput = it
                            onSmoothAnimationsSave(it)
                        }
                    )
                }
            }

            // Floating Scan Button
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Floating Scan Button", style = MaterialTheme.typography.titleMedium)
                            Text("Show a quick-scan FAB while browsing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = floatingScanButtonInput,
                        onCheckedChange = {
                            floatingScanButtonInput = it
                            onFloatingScanButtonSave(it)
                        }
                    )
                }
            }
        }
    }

    // Color picker
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = accentColorInput,
            onColorSelected = { color ->
                accentColorInput = color
                onAccentColorSave(color)
            },
            onDismiss = { showColorPicker = false }
        )
    }

    // Leave without confirming dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unsaved changes") },
            text = { Text("You changed appearance settings without confirming. Revert to previous settings?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    pendingNavigation?.invoke()
                }) {
                    Text("Keep changes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // Revert all to initial values
                    onThemeModeSave(initialThemeMode)
                    onAccentColorSave(initialAccentColor)
                    onSmoothAnimationsSave(initialSmooth)
                    onFloatingScanButtonSave(initialFloating)
                    showLeaveDialog = false
                    pendingNavigation?.invoke()
                }) {
                    Text("Revert")
                }
            }
        )
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = listOf(
        "blue" to Color(0xFF1976D2), "indigo" to Color(0xFF3F51B5),
        "purple" to Color(0xFF9C27B0), "pink" to Color(0xFFE91E63),
        "red" to Color(0xFFD32F2F), "orange" to Color(0xFFF57C00),
        "amber" to Color(0xFFFFA000), "green" to Color(0xFF388E3C),
        "teal" to Color(0xFF00796B), "brown" to Color(0xFF5D4037)
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
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!showCustomInput) {
                    OutlinedButton(onClick = { showCustomInput = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Palette, contentDescription = null)
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
                            OutlinedButton(onClick = { showCustomInput = false; customHex = "" }, modifier = Modifier.weight(1f)) { Text("Cancel") }
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
        return try { Color(android.graphics.Color.parseColor(colorName)) } catch (e: Exception) { Color(0xFF1976D2) }
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
