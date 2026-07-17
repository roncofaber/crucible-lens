package crucible.lens.data.util

val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun isLeapYear(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

/** Groups an ISO 8601 timestamp string into a "Mon YYYY" label, e.g. "Jan 2025". */
fun dateGroupKey(raw: String?): String {
    if (raw == null) return "No date"
    return try {
        val year = raw.trim().substring(0, 4).toInt()
        val month = raw.trim().substring(5, 7).toInt()
        "${MONTH_NAMES[month - 1]} $year"
    } catch (_: Exception) { "No date" }
}

/** Returns the inclusive ISO 8601 start/end bounds for the month containing [raw]. */
internal fun monthBounds(raw: String?): Pair<String, String>? {
    if (raw == null) return null
    return try {
        val s = raw.trim().replace("T", " ")
        val year = s.substring(0, 4).toInt()
        val month = s.substring(5, 7).toInt()
        val daysInMonth = if (month == 2 && isLeapYear(year)) 29 else DAYS_IN_MONTH[month - 1]
        val mm = month.toString().padStart(2, '0')
        val dd = daysInMonth.toString().padStart(2, '0')
        "${year}-${mm}-01T00:00:00" to "${year}-${mm}-${dd}T23:59:59"
    } catch (_: Exception) { null }
}
