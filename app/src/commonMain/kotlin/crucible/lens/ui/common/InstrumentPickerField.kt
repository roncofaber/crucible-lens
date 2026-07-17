package crucible.lens.ui.common
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InstrumentPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var results by remember { mutableStateOf<List<Instrument>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(value) {
        if (value.length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        isSearching = true
        results = when (val resp = ApiClient.service.searchInstruments(value)) {
            is ApiResult.Success -> resp.data
            is ApiResult.Error -> emptyList()
        }
        isSearching = false
        expanded = results.isNotEmpty()
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
                .onFocusChanged { if (it.isFocused && value.length >= 3) expanded = true },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = { Icon(Icons.Default.Biotech, contentDescription = null) },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    value.isNotEmpty() -> IconButton(onClick = { onValueChange(""); expanded = false }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        )

        DropdownMenu(
            expanded = expanded && results.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            results.forEach { instrument ->
                DropdownMenuItem(
                    text = { Text(instrument.instrumentName ?: instrument.uniqueId) },
                    onClick = { onValueChange(instrument.instrumentName ?: ""); expanded = false }
                )
            }
        }
    }
}
