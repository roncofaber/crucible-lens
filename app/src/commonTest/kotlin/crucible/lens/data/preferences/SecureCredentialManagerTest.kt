package crucible.lens.data.preferences

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureCredentialManagerTest {
    @Test
    fun existingSecureCredentialIsLoadedAndLegacyIsRemoved() = runTest {
        val store = FakeCredentialStore(value = "secure")
        var legacyRemoved = false

        val result = SecureCredentialManager(store).loadAndMigrate(
            legacyCredential = { "legacy" },
            removeLegacyCredential = { legacyRemoved = true }
        )

        assertEquals("secure", result)
        assertTrue(legacyRemoved)
    }

    @Test
    fun legacyCredentialIsVerifiedBeforeRemoval() = runTest {
        val store = FakeCredentialStore()
        var legacyRemoved = false

        val result = SecureCredentialManager(store).loadAndMigrate(
            legacyCredential = { "legacy" },
            removeLegacyCredential = { legacyRemoved = true }
        )

        assertEquals("legacy", result)
        assertEquals("legacy", store.value)
        assertTrue(legacyRemoved)
    }

    @Test
    fun failedMigrationKeepsLegacyCredential() = runTest {
        val store = FakeCredentialStore(failWrite = true)
        var legacyRemoved = false

        val result = SecureCredentialManager(store).loadAndMigrate(
            legacyCredential = { "legacy" },
            removeLegacyCredential = { legacyRemoved = true }
        )

        assertNull(result)
        assertFalse(legacyRemoved)
    }

    @Test
    fun corruptSecureCredentialIsDeletedAndSignsOut() = runTest {
        val store = FakeCredentialStore(value = "corrupt", failRead = true)

        val result = SecureCredentialManager(store).loadAndMigrate(
            legacyCredential = { null },
            removeLegacyCredential = {}
        )

        assertNull(result)
        assertNull(store.value)
        assertTrue(store.deleteCalled)
    }

    @Test
    fun saveReadAndDeleteRoundTrip() = runTest {
        val store = FakeCredentialStore()
        val manager = SecureCredentialManager(store)

        manager.save("credential")
        assertEquals("credential", store.read())

        manager.clear()
        assertNull(store.read())
    }

    @Test
    fun failedVerificationDeletesUnusableCredential() = runTest {
        val store = FakeCredentialStore(readAfterWrite = "different")

        assertFailsWith<IllegalStateException> {
            SecureCredentialManager(store).save("credential")
        }
        assertNull(store.value)
        assertTrue(store.deleteCalled)
    }

    private class FakeCredentialStore(
        var value: String? = null,
        private val failRead: Boolean = false,
        private val failWrite: Boolean = false,
        private val readAfterWrite: String? = null
    ) : SecureCredentialStore {
        var deleteCalled = false
        private var wrote = false

        override suspend fun read(): String? {
            if (failRead) error("read failed")
            return if (wrote && readAfterWrite != null) readAfterWrite else value
        }

        override suspend fun write(value: String) {
            if (failWrite) error("write failed")
            this.value = value
            wrote = true
        }

        override suspend fun delete() {
            value = null
            deleteCalled = true
            wrote = false
        }
    }
}
