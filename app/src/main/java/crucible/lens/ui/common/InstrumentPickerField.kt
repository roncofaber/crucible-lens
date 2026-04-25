package crucible.lens.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.model.Instrument

@Composable
fun InstrumentPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var instruments by remember { mutableStateOf<List<Instrument>?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val resp = ApiClient.service.getInstruments()
            instruments = if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
        } catch (_: Exception) {
            instruments = emptyList()
        }
    }

    val filtered = remember(value, instruments) {
        val list = instruments ?: return@remember emptyList()
        if (value.isBlank()) emptyList()
        else list.filter { (it.instrumentName ?: "").contains(value, ignoreCase = true) }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Instrument") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) expanded = true },
            singleLine = true,
            enabled = instruments != null,
            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
            trailingIcon = {
                when {
                    instruments == null ->
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    value.isNotEmpty() ->
                        IconButton(onClick = { onValueChange(""); expanded = false }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                }
            }
        )

        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            filtered.forEach { instrument ->
                DropdownMenuItem(
                    text = { Text(instrument.instrumentName ?: instrument.uniqueId) },
                    onClick = { onValueChange(instrument.instrumentName ?: ""); expanded = false }
                )
            }
        }
    }
}
