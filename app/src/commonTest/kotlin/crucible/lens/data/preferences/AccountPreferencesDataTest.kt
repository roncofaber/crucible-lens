package crucible.lens.data.preferences

import crucible.lens.data.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AccountPreferencesDataTest {

    @Test
    fun serializationRoundTripPreservesAccountData() {
        val data = AccountPreferencesData(
            lastVisitedResource = "sample-1",
            lastVisitedResourceName = "Sample One",
            pinnedProjects = setOf("project-a"),
            syncedProjects = setOf("project-a", "project-b"),
            syncSetupComplete = true,
            pinnedInstruments = setOf("instrument-a"),
            hiddenInstruments = setOf("instrument-b"),
            userOrcid = "0000-0001",
            userProfile = User(uniqueId = "0000-0001", username = "scientist"),
            resourceHistory = listOf(HistoryItem("sample-1", "Sample One", 123L, "sample", "project-a"))
        )

        assertEquals(data, decodeAccountPreferences(encodeAccountPreferences(data)))
    }

    @Test
    fun malformedAccountDataFallsBackToEmpty() {
        assertEquals(AccountPreferencesData(), decodeAccountPreferences("not-json"))
    }

    @Test
    fun storageKeysAreStableAndAccountSpecific() {
        assertEquals(accountPreferencesStorageKey("account-a"), accountPreferencesStorageKey("account-a"))
        assertNotEquals(accountPreferencesStorageKey("account-a"), accountPreferencesStorageKey("account-b"))
    }

    @Test
    fun accountIdentityPrefersOrcid() {
        assertEquals("0000-0001", accountIdFor(User(uniqueId = "0000-0001", username = "scientist")))
    }

    @Test
    fun accountIdentityFallsBackToNamespacedUsername() {
        assertEquals("username:scientist", accountIdFor(User(username = "scientist")))
    }

    @Test
    fun accountIdentityRequiresStableField() {
        assertNull(accountIdFor(User(firstName = "Ada")))
    }
}
