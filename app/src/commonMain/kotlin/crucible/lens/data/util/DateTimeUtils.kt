package crucible.lens.data.util

val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Groups an ISO 8601 timestamp string into a "Mon YYYY" label, e.g. "Jan 2025". */
fun dateGroupKey(raw: String?): String {
    if (raw == null) return "No date"
    return try {
        val year = raw.trim().substring(0, 4).toInt()
        val month = raw.trim().substring(5, 7).toInt()
        "${MONTH_NAMES[month - 1]} $year"
    } catch (_: Exception) { "No date" }
}
