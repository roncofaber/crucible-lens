package crucible.lens.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val isoFormatter     = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Timestamp",
    modifier: Modifier = Modifier
) {
    val parsedValue: LocalDateTime? = remember(value) {
        if (value.isBlank()) null
        else try { LocalDateTime.parse(value, isoFormatter) }
             catch (_: DateTimeParseException) { null }
    }
    // Creating → default to now; editing → default to existing value
    val pickerDefault = remember(parsedValue) { parsedValue ?: LocalDateTime.now() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate    by remember { mutableStateOf<LocalDate?>(null) }

    val displayText = parsedValue?.format(displayFormatter) ?: ""

    // Open date picker when the field is pressed
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDatePicker = true
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = pickerDefault.toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    OutlinedTextField(
        value = displayText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
        modifier = modifier,
        interactionSource = interactionSource
    )

    // ── Date picker ──────────────────────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    pendingDate = datePickerState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: pickerDefault.toLocalDate()
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time picker ──────────────────────────────────────────────────────────
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour   = pickerDefault.hour,
            initialMinute = pickerDefault.minute,
            is24Hour      = true
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Select time", style = MaterialTheme.typography.titleMedium)
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            showTimePicker = false
                            val date = pendingDate ?: pickerDefault.toLocalDate()
                            val dt = LocalDateTime.of(
                                date,
                                LocalTime.of(timePickerState.hour, timePickerState.minute)
                            )
                            onValueChange(dt.format(isoFormatter))
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}
