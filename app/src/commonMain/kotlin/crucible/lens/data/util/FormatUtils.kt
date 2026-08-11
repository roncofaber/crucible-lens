package crucible.lens.data.util

import crucible.lens.data.model.User
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Backend-generated timestamps (creation/modification time) are ISO 8601 UTC with an explicit
 * offset (e.g. "2026-07-23T14:32:10.123456+00:00") and are converted to the device's local
 * timezone before display. Some timestamp fields (e.g. Sample/Dataset.timestamp) are free-text —
 * pre-filled from [crucible.lens.platform.currentIsoDateTime] (which has no UTC offset, since
 * it's already local) but editable by the user, so they may arrive without an offset at all.
 * For those, parsing as an absolute [Instant] fails and we fall back to treating the string as
 * an already-local [LocalDateTime] and format it as-is, without converting it again.
 */
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
    val local = try {
        Instant.parse(s).toLocalDateTime(TimeZone.currentSystemDefault())
    } catch (_: Exception) {
        try { LocalDateTime.parse(s) } catch (_: Exception) { null }
    } ?: return raw
    return try {
        val dateStr = "${MONTH_NAMES[local.month.number - 1]} ${local.day}, ${local.year}"
        val hour = local.hour
        val ampm = if (hour < 12) "AM" else "PM"
        val hour12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        "$dateStr · $hour12:${local.minute.toString().padStart(2, '0')} $ampm"
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

/**
 * Full name if either first/last name is set, else "@username", else the raw ORCID/ID.
 * This is the app-wide default for showing a person "in context" (owner rows, member lists,
 * project leads) — see CLAUDE.md's "User identity conventions". The deliberate exceptions are
 * identity-lookup UI (UserResultItem, FilterSheet's search results, UserProfileScreen's own
 * header), which stay username-primary since disambiguating a search result is the whole point
 * there, unlike showing someone already in context.
 */
fun userDisplayName(firstName: String?, lastName: String?, username: String?, uniqueId: String? = null): String {
    val name = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }
    return name ?: username?.let { "@$it" } ?: uniqueId ?: "Unknown"
}

fun userDisplayName(user: User?): String =
    userDisplayName(user?.firstName, user?.lastName, user?.username, user?.uniqueId)

/**
 * Sort key for alphabetizing member/user lists by last name — falls back to username, then
 * ORCID, for users without a name on file, so they still sort predictably instead of clumping
 * at one end.
 */
fun userSortKey(user: User?): String =
    (user?.lastName?.takeIf { it.isNotBlank() } ?: user?.username ?: user?.uniqueId ?: "").lowercase()

/**
 * True if [a] and [b] identify the same account, matched on username or ORCID (either is
 * enough — a mismatched other field never overrides a match). Two users with no identifier in
 * common are never considered the same, even if both are null.
 */
fun isSameUser(a: User?, b: User?): Boolean {
    if (a == null || b == null) return false
    return (a.username != null && a.username == b.username) ||
        (a.uniqueId != null && a.uniqueId == b.uniqueId)
}
