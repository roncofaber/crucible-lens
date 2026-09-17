package crucible.lens.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DateTimeUtilsTest {
    @Test
    fun queryTimestampNormalizesOffsetsToUtc() {
        assertEquals("2026-09-16T19:30:00Z", toUtcQueryTimestamp("2026-09-16T12:30:00-07:00"))
    }

    @Test
    fun queryTimestampTreatsPickerValueAsUtc() {
        assertEquals("2026-09-16T12:30:00Z", toUtcQueryTimestamp("2026-09-16T12:30:00"))
    }

    @Test
    fun queryTimestampPreservesInvalidInputForServerValidation() {
        assertEquals("not-a-date", toUtcQueryTimestamp("not-a-date"))
        assertNull(toUtcQueryTimestamp("  "))
    }
}
