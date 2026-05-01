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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private val isoFormatter: String = "yyyy-MM-dd'T'HH:mm"

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
        else try {
            LocalDateTime.parse(value)
        } catch (_: Exception) {
            null
        }
    }
    // Creating → default to now; editing → default to existing value
    val pickerDefault = remember(parsedValue) {
        parsedValue ?: LocalDateTime.parse(
            Clock.System.now().toLocalDateTime(TimeZone.UTC).toString()
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    val displayText = parsedValue?.let { dt ->
        "${dt.month.number.toString().padStart(2, '0')}/${dt.day.toString().padStart(2, '0')}/${dt.year} · " +
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    } ?: ""

    // Open date picker when the field is pressed
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDatePicker = true
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = pickerDefault.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
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
                        ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                        ?: pickerDefault.date
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
            initialHour = pickerDefault.hour,
            initialMinute = pickerDefault.minute,
            is24Hour = true
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
                            val date = pendingDate ?: pickerDefault.date
                            val dt = LocalDateTime(
                                date,
                                LocalTime(timePickerState.hour, timePickerState.minute)
                            )
                            onValueChange(dt.toString())
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}
