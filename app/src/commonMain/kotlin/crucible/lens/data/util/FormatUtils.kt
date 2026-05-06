package crucible.lens.data.util

import kotlin.math.pow
import kotlin.math.round

fun formatDateTime(raw: String?): String {
    if (raw == null) return "None"
    val s = raw.trim()
    val compact = Regex("""(\d{4})(\d{2})(\d{2})_(am|pm)""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (compact != null) {
        val (y, mo, d, ampm) = compact.destructured
        val m = mo.toIntOrNull() ?: return raw
        if (m < 1 || m > 12) return raw
        return "${MONTH_NAMES[m - 1]} ${d.trimStart('0').ifEmpty { "0" }}, $y · ${ampm.uppercase()}"
    }
    return try {
        val datePart = s.substring(0, 10)
        val year = datePart.substring(0, 4).toInt()
        val month = datePart.substring(5, 7).toInt()
        val day = datePart.substring(8, 10).toInt()
        if (month < 1 || month > 12) return raw
        val dateStr = "${MONTH_NAMES[month - 1]} $day, $year"
        if (s.length < 13) return dateStr
        val timePart = s.substring(11, 16)
        val hour = timePart.substring(0, 2).toInt()
        val minute = timePart.substring(3, 5).toInt()
        val ampm = if (hour < 12) "AM" else "PM"
        val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        "$dateStr · $hour12:${minute.toString().padStart(2, '0')} $ampm"
    } catch (_: Exception) { raw }
}

fun formatDecimal(value: Double, places: Int): String {
    val factor = 10.0.pow(places.toDouble())
    val rounded = round(value * factor) / factor
    val str = rounded.toString()
    val dotIdx = str.indexOf('.')
    return if (dotIdx < 0) {
        "$str.${"0".repeat(places)}"
    } else {
        val dec = str.substring(dotIdx + 1).padEnd(places, '0').take(places)
        "${str.substring(0, dotIdx)}.$dec"
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${formatDecimal(bytes / 1_073_741_824.0, 2)} GB"
    bytes >= 1_048_576     -> "${formatDecimal(bytes / 1_048_576.0, 2)} MB"
    bytes >= 1_024         -> "${formatDecimal(bytes / 1_024.0, 1)} KB"
    else                   -> "$bytes B"
}
