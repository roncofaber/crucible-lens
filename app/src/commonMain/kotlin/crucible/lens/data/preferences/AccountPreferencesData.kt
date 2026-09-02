package crucible.lens.data.preferences

import crucible.lens.data.model.User
import crucible.lens.data.util.PlatformCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AccountPreferencesData(
    val lastVisitedResource: String? = null,
    val lastVisitedResourceName: String? = null,
    val pinnedProjects: Set<String> = emptySet(),
    val syncedProjects: Set<String> = emptySet(),
    val syncSetupComplete: Boolean = false,
    val pinnedInstruments: Set<String> = emptySet(),
    val hiddenInstruments: Set<String> = emptySet(),
    val userOrcid: String? = null,
    val userProfile: User? = null,
    val resourceHistory: List<HistoryItem> = emptyList()
)

internal val accountPreferencesJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun accountPreferencesStorageKey(accountId: String): String {
    val digest = PlatformCrypto.sha256Hex(accountId.encodeToByteArray())
    return "account_preferences_$digest"
}

internal fun accountIdFor(user: User): String? =
    user.uniqueId ?: user.username?.let { "username:$it" } ?: user.email?.let { "email:$it" }

internal fun decodeAccountPreferences(value: String?): AccountPreferencesData =
    value?.let { runCatching { accountPreferencesJson.decodeFromString<AccountPreferencesData>(it) }.getOrNull() }
        ?: AccountPreferencesData()

internal fun encodeAccountPreferences(value: AccountPreferencesData): String =
    accountPreferencesJson.encodeToString(AccountPreferencesData.serializer(), value)
