package crucible.lens.data.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class FormatUtilsTest {

    @Test
    fun formatDateTimeConvertsUtcOffsetToLocalTimezone() {
        // A fixed UTC instant. Whatever the device's local timezone, this must not be displayed
        // as if the raw 14:32 UTC clock time were already local — see the historical bug this
        // guards against (the field was rendered without any timezone conversion at all).
        val utc = "2026-07-23T14:32:10.123456+00:00"
        val result = formatDateTime(utc)
        assertTrue(result != "None")

        // Recompute the expected local hour/minute the same way production code does, so the
        // assertion holds regardless of which timezone the test runs in.
        val expectedLocal = Instant.parse(utc).toLocalDateTime(TimeZone.currentSystemDefault())
        val expectedHour12 = when {
            expectedLocal.hour == 0 -> 12
            expectedLocal.hour > 12 -> expectedLocal.hour - 12
            else -> expectedLocal.hour
        }
        assertTrue(result.contains("$expectedHour12:${expectedLocal.minute.toString().padStart(2, '0')}"))
    }

    @Test
    fun formatDateTimeFallsBackToLocalDateTimeWithoutOffset() {
        // currentIsoDateTime() (used to pre-fill the user-editable "timestamp" field) has no UTC
        // offset since it's already local — this must render the literal clock values verbatim,
        // not be misinterpreted as UTC and shifted again.
        val noOffset = "2026-07-23T09:15:00"
        val result = formatDateTime(noOffset)
        assertEquals("Jul 23, 2026 · 9:15 AM", result)
    }

    @Test
    fun formatDateTimeHandlesCompactAmPmFormat() {
        assertEquals("Mar 5, 2026 · AM", formatDateTime("20260305_am"))
    }

    @Test
    fun formatDateTimeReturnsNoneForNull() {
        assertEquals("None", formatDateTime(null))
    }

    @Test
    fun formatDateTimeReturnsRawStringForUnparseable() {
        assertEquals("not-a-date", formatDateTime("not-a-date"))
    }
}
