package crucible.lens.ui.detail.components

import crucible.lens.data.util.MONTH_NAMES

internal fun formatDateTime(raw: String?): String {
    if (raw == null) return "None"
    val s = raw.trim()
    // Compact yyyyMMdd_am|pm pattern
    val compact = Regex("""(\d{4})(\d{2})(\d{2})_(am|pm)""", RegexOption.IGNORE_CASE).matchEntire(s)
    if (compact != null) {
        val (y, mo, d, ampm) = compact.destructured
        val m = mo.toIntOrNull() ?: return raw
        if (m < 1 || m > 12) return raw
        return "${MONTH_NAMES[m - 1]} ${d.trimStart('0').ifEmpty { "0" }}, $y · ${ampm.uppercase()}"
    }
    // ISO 8601: YYYY-MM-DDThh:mm... (with optional offset/Z/fractional seconds)
    return try {
        val datePart = s.substring(0, 10) // YYYY-MM-DD
        val year = datePart.substring(0, 4).toInt()
        val month = datePart.substring(5, 7).toInt()
        val day = datePart.substring(8, 10).toInt()
        if (month < 1 || month > 12) return raw
        val monthName = MONTH_NAMES[month - 1]
        val dateStr = "$monthName $day, $year"
        if (s.length < 13) return dateStr
        // Parse time hh:mm
        val timePart = s.substring(11, 16) // hh:mm
        val hour = timePart.substring(0, 2).toInt()
        val minute = timePart.substring(3, 5).toInt()
        val ampm = if (hour < 12) "AM" else "PM"
        val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        "$dateStr · $hour12:${minute.toString().padStart(2, '0')} $ampm"
    } catch (_: Exception) { raw }
}
