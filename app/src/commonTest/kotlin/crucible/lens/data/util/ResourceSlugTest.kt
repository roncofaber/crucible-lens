package crucible.lens.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourceSlugTest {
    @Test
    fun projectAndInstrumentIdsUseTheSharedFormat() {
        listOf("abc", "XRD_1", "instrument-25").forEach { value ->
            assertNull(projectSlugValidationError(value))
            assertNull(instrumentSlugValidationError(value))
        }

        listOf("ab", "-leading", "has a space", "x".repeat(26)).forEach { value ->
            assertEquals(projectSlugValidationError(value), instrumentSlugValidationError(value))
        }
    }

    @Test
    fun projectOnlyReservedIdsRemainRejected() {
        assertEquals("That project ID is reserved", projectSlugValidationError("public"))
        assertNull(instrumentSlugValidationError("public"))
    }
}
