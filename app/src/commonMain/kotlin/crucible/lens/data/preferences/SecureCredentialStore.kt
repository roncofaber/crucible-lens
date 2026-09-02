package crucible.lens.data.preferences

import kotlinx.coroutines.CancellationException

interface SecureCredentialStore {
    suspend fun read(): String?
    suspend fun write(value: String)
    suspend fun delete()
}

class SecureCredentialManager(
    private val store: SecureCredentialStore
) {
    suspend fun loadAndMigrate(
        legacyCredential: suspend () -> String?,
        removeLegacyCredential: suspend () -> Unit
    ): String? {
        val storedCredential = try {
            store.read()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            deleteAfterFailure()
            null
        }

        if (storedCredential != null) {
            removeLegacyCredential()
            return storedCredential
        }

        val legacy = legacyCredential()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            save(legacy)
            removeLegacyCredential()
            legacy
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(credential: String) {
        try {
            store.write(credential)
            check(store.read() == credential) { "Credential verification failed" }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deleteAfterFailure()
            throw error
        }
    }

    suspend fun clear() {
        store.delete()
    }

    private suspend fun deleteAfterFailure() {
        try {
            store.delete()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }
}
