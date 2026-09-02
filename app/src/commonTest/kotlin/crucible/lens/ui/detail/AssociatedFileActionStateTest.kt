package crucible.lens.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AssociatedFileActionStateTest {

    @Test
    fun downloadAndShareStatesRemainIndependent() {
        val download = AssociatedFileActionKey("dataset", "file", AssociatedFileAction.DOWNLOAD)
        val share = AssociatedFileActionKey("dataset", "file", AssociatedFileAction.SHARE)

        val states = updateAssociatedFileActionState(
            updateAssociatedFileActionState(emptyMap(), download, AssociatedFileActionState.Resolving),
            share,
            AssociatedFileActionState.Error("Unavailable")
        )

        assertEquals(AssociatedFileActionState.Resolving, states[download])
        assertEquals(AssociatedFileActionState.Error("Unavailable"), states[share])
    }

    @Test
    fun clearingOneActionPreservesOtherFilesAndActions() {
        val download = AssociatedFileActionKey("dataset", "file", AssociatedFileAction.DOWNLOAD)
        val share = AssociatedFileActionKey("dataset", "file", AssociatedFileAction.SHARE)
        val otherFile = AssociatedFileActionKey("dataset", "other", AssociatedFileAction.DOWNLOAD)
        val initial = mapOf(
            download to AssociatedFileActionState.Ready("download-url"),
            share to AssociatedFileActionState.Ready("share-url"),
            otherFile to AssociatedFileActionState.Resolving
        )

        val states = updateAssociatedFileActionState(initial, download, null)

        assertFalse(download in states)
        assertEquals(AssociatedFileActionState.Ready("share-url"), states[share])
        assertEquals(AssociatedFileActionState.Resolving, states[otherFile])
    }

    @Test
    fun fileLinkErrorsDistinguishAccessAndMissingFiles() {
        assertEquals("Sign in again to access this file", associatedFileUrlError(401))
        assertEquals("You do not have permission to access this file", associatedFileUrlError(403))
        assertEquals("File link not found", associatedFileUrlError(404))
        assertEquals("Crucible service error (503)", associatedFileUrlError(503))
    }
}
