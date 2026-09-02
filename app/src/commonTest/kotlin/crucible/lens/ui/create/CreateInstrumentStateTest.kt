package crucible.lens.ui.create

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateInstrumentStateTest {
    @Test
    fun generatedInstrumentIdUsesValidBoundedSlug() {
        assertEquals("high-resolution-xrd", instrumentSlugFromName(" High Resolution XRD "))
        assertEquals(25, instrumentSlugFromName("abcdefghijklmnopqrstuvwxyz0123456789").length)
    }

    @Test
    fun formRequiresNameLocationAndValidInstrumentId() {
        assertFalse(CreateInstrumentFormState().canCreate)
        assertFalse(CreateInstrumentFormState(name = "XRD", instrumentId = "xy", location = "Lab").canCreate)

        val valid = CreateInstrumentFormState(name = "XRD", instrumentId = "xrd-1", location = "Lab")

        assertNull(valid.instrumentIdError)
        assertTrue(valid.canCreate)
        assertTrue(valid.hasUnsavedChanges)
    }

    @Test
    fun createErrorsExplainExpectedV3Failures() {
        assertEquals("Service accounts cannot register instruments", createInstrumentError(403))
        assertEquals("That instrument ID is already in use", createInstrumentError(409))
        assertEquals("Check the required fields and instrument ID", createInstrumentError(422))
    }
}
