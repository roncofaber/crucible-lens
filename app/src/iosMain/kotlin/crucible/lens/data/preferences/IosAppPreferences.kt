package crucible.lens.data.preferences

import com.russhwolf.settings.NSUserDefaultsSettings
import crucible.lens.data.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

class IosAppPreferences(
    secureCredentialStore: SecureCredentialStore = IosSecureCredentialStore()
) : AppPreferences {
    private val settings = NSUserDefaultsSettings.Factory().create("crucible_lens_prefs")
    private val iosProfileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    private var currentAccountData = AccountPreferencesData()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val credentialManager = SecureCredentialManager(secureCredentialStore)

    private val _isLoaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _apiKey = MutableStateFlow<String?>(null)
    override val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    override val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    init {
        val legacyProfile = settings.getStringOrNull("user_profile")?.let { value ->
            runCatching { iosProfileJson.decodeFromString<User>(value) }.getOrNull()
        }
        val accountId = settings.getStringOrNull("active_account_id")
            ?: legacyProfile?.let(::accountIdFor)
            ?: settings.getStringOrNull("user_orcid")
        if (accountId != null) {
            val key = accountPreferencesStorageKey(accountId)
            currentAccountData = if (settings.hasKey(key)) {
                decodeAccountPreferences(settings.getStringOrNull(key))
            } else {
                legacyAccountData(legacyProfile).also { settings.putString(key, encodeAccountPreferences(it)) }
            }
            settings.putString("active_account_id", accountId)
            removeLegacyAccountData()
            _activeAccountId.value = accountId
        }
        scope.launch {
            try {
                _apiKey.value = credentialManager.loadAndMigrate(
                    legacyCredential = { settings.getStringOrNull("api_key") },
                    removeLegacyCredential = { settings.remove("api_key") }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _apiKey.value = null
            } finally {
                _isLoaded.value = true
            }
        }
    }

    // ── Private backing fields — updated synchronously on every save ──────────

    private val _apiBaseUrl = MutableStateFlow(settings.getString("api_base_url", AppPreferences.DEFAULT_API_BASE_URL))
    override val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _graphExplorerUrl = MutableStateFlow(settings.getString("graph_explorer_url", AppPreferences.DEFAULT_GRAPH_EXPLORER_URL))
    override val graphExplorerUrl: StateFlow<String> = _graphExplorerUrl.asStateFlow()

    private val _themeMode = MutableStateFlow(settings.getString("theme_mode", AppPreferences.THEME_MODE_SYSTEM))
    override val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(settings.getString("accent_color", AppPreferences.DEFAULT_ACCENT_COLOR))
    override val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _accentContrast = MutableStateFlow(settings.getString("accent_contrast", AppPreferences.DEFAULT_ACCENT_CONTRAST))
    override val accentContrast: StateFlow<String> = _accentContrast.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(settings.getBoolean("use_dynamic_color", false))
    override val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _lastVisitedResource = MutableStateFlow(currentAccountData.lastVisitedResource)
    override val lastVisitedResource: StateFlow<String?> = _lastVisitedResource.asStateFlow()

    private val _lastVisitedResourceName = MutableStateFlow(currentAccountData.lastVisitedResourceName)
    override val lastVisitedResourceName: StateFlow<String?> = _lastVisitedResourceName.asStateFlow()

    private val _floatingScanButton = MutableStateFlow(settings.getBoolean("floating_scan_button", true))
    override val floatingScanButton: StateFlow<Boolean> = _floatingScanButton.asStateFlow()

    private val _pinnedProjects = MutableStateFlow(currentAccountData.pinnedProjects)
    override val pinnedProjects: StateFlow<Set<String>> = _pinnedProjects.asStateFlow()

    private val _syncedProjects = MutableStateFlow(currentAccountData.syncedProjects)
    override val syncedProjects: StateFlow<Set<String>> = _syncedProjects.asStateFlow()

    private val _syncSetupComplete = MutableStateFlow(currentAccountData.syncSetupComplete)
    override val syncSetupComplete: StateFlow<Boolean> = _syncSetupComplete.asStateFlow()

    private val _hiddenInstruments = MutableStateFlow(currentAccountData.hiddenInstruments)
    override val hiddenInstruments: StateFlow<Set<String>> = _hiddenInstruments.asStateFlow()

    private val _pinnedInstruments = MutableStateFlow(currentAccountData.pinnedInstruments)
    override val pinnedInstruments: StateFlow<Set<String>> = _pinnedInstruments.asStateFlow()

    private val _userOrcid = MutableStateFlow(currentAccountData.userOrcid)
    override val userOrcid: StateFlow<String?> = _userOrcid.asStateFlow()

    private val _userProfile = MutableStateFlow(currentAccountData.userProfile)
    override val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _resourceHistory = MutableStateFlow(currentAccountData.resourceHistory)
    override val resourceHistory: StateFlow<List<HistoryItem>> = _resourceHistory.asStateFlow()

    private val _sampleGroupBy = MutableStateFlow(settings.getString("sample_group_by", "TYPE"))
    override val sampleGroupBy: StateFlow<String> = _sampleGroupBy.asStateFlow()

    private val _datasetGroupBy = MutableStateFlow(settings.getString("dataset_group_by", "MEASUREMENT"))
    override val datasetGroupBy: StateFlow<String> = _datasetGroupBy.asStateFlow()

    private val _instrumentGroupBy = MutableStateFlow(settings.getString("instrument_group_by", "MEASUREMENT"))
    override val instrumentGroupBy: StateFlow<String> = _instrumentGroupBy.asStateFlow()

    private val _defaultProjectTab = MutableStateFlow(settings.getString("default_project_tab", AppPreferences.PROJECT_TAB_SAMPLES))
    override val defaultProjectTab: StateFlow<String> = _defaultProjectTab.asStateFlow()

    private val _peopleResultLimit = MutableStateFlow(settings.getInt("people_result_limit", AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT))
    override val peopleResultLimit: StateFlow<Int> = _peopleResultLimit.asStateFlow()

    private val _projectResultLimit = MutableStateFlow(settings.getInt("project_result_limit", AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT))
    override val projectResultLimit: StateFlow<Int> = _projectResultLimit.asStateFlow()

    // ── Save operations ───────────────────────────────────────────────────────

    override suspend fun saveApiKey(key: String) {
        credentialManager.save(key)
        settings.remove("api_key")
        _apiKey.value = key
    }

    override suspend fun activateAccount(accountId: String) {
        val key = accountPreferencesStorageKey(accountId)
        currentAccountData = if (settings.hasKey(key)) {
            decodeAccountPreferences(settings.getStringOrNull(key))
        } else {
            val legacyProfile = settings.getStringOrNull("user_profile")?.let { value ->
                runCatching { iosProfileJson.decodeFromString<User>(value) }.getOrNull()
            }
            val legacyOwner = legacyProfile?.let(::accountIdFor) ?: settings.getStringOrNull("user_orcid")
            if (legacyOwner == accountId) legacyAccountData(legacyProfile)
            else AccountPreferencesData()
        }
        settings.putString(key, encodeAccountPreferences(currentAccountData))
        settings.putString("active_account_id", accountId)
        removeLegacyAccountData()
        _activeAccountId.value = accountId
        publishAccountData()
    }

    override suspend fun deactivateAccount() {
        settings.remove("active_account_id")
        removeLegacyAccountData()
        _activeAccountId.value = null
        currentAccountData = AccountPreferencesData()
        publishAccountData()
    }

    override suspend fun saveApiBaseUrl(url: String) {
        settings.putString("api_base_url", url); _apiBaseUrl.value = url
    }

    override suspend fun saveGraphExplorerUrl(url: String) {
        settings.putString("graph_explorer_url", url); _graphExplorerUrl.value = url
    }

    override suspend fun saveThemeMode(mode: String) {
        settings.putString("theme_mode", mode); _themeMode.value = mode
    }

    override suspend fun saveAccentColor(color: String) {
        settings.putString("accent_color", color); _accentColor.value = color
    }

    override suspend fun saveAccentContrast(contrast: String) {
        settings.putString("accent_contrast", contrast); _accentContrast.value = contrast
    }

    override suspend fun saveUseDynamicColor(enabled: Boolean) {
        settings.putBoolean("use_dynamic_color", enabled); _useDynamicColor.value = enabled
    }

    override suspend fun saveLastVisitedResource(uuid: String, name: String) {
        updateAccountData { it.copy(lastVisitedResource = uuid, lastVisitedResourceName = name) }
    }

    override suspend fun saveFloatingScanButton(enabled: Boolean) {
        settings.putBoolean("floating_scan_button", enabled); _floatingScanButton.value = enabled
    }

    override suspend fun clearApiKey() {
        credentialManager.clear()
        settings.remove("api_key")
        _apiKey.value = null
    }

    override suspend fun togglePinnedProject(id: String) {
        updateAccountData { data ->
            val pinned = data.pinnedProjects.toMutableSet()
            val adding = id !in pinned
            if (adding) pinned.add(id) else pinned.remove(id)
            val synced = data.syncedProjects.toMutableSet()
            if (adding) synced.add(id)
            data.copy(pinnedProjects = pinned, syncedProjects = synced)
        }
    }

    override suspend fun setPinnedProjects(ids: Set<String>) {
        updateAccountData { it.copy(pinnedProjects = ids) }
    }

    override suspend fun toggleSyncedProject(id: String) {
        updateAccountData { data ->
            val updated = data.syncedProjects.toMutableSet().apply { if (id in this) remove(id) else add(id) }
            data.copy(syncedProjects = updated)
        }
    }

    override suspend fun setSyncedProjects(ids: Set<String>) {
        updateAccountData { it.copy(syncedProjects = ids) }
    }

    override suspend fun saveSyncSetupComplete(complete: Boolean) {
        updateAccountData { it.copy(syncSetupComplete = complete) }
    }

    override suspend fun toggleHiddenInstrument(id: String) {
        updateAccountData { data ->
            val updated = data.hiddenInstruments.toMutableSet().apply { if (id in this) remove(id) else add(id) }
            data.copy(hiddenInstruments = updated)
        }
    }

    override suspend fun togglePinnedInstrument(id: String) {
        updateAccountData { data ->
            val updated = data.pinnedInstruments.toMutableSet().apply { if (id in this) remove(id) else add(id) }
            data.copy(pinnedInstruments = updated)
        }
    }

    override suspend fun saveUserOrcid(orcid: String?) {
        updateAccountData { it.copy(userOrcid = orcid) }
    }

    override suspend fun saveUserProfile(user: User?) {
        updateAccountData { it.copy(userProfile = user, userOrcid = user?.uniqueId ?: it.userOrcid) }
    }

    override suspend fun clearUserProfile() {
        updateAccountData { it.copy(userProfile = null) }
    }

    override suspend fun addToHistory(uuid: String, name: String, resourceType: String?, projectId: String?) {
        updateAccountData { data ->
            val updated = (listOf(HistoryItem(uuid, name, Clock.System.now().toEpochMilliseconds(), resourceType, projectId)) +
                data.resourceHistory.filter { it.uuid != uuid }).take(20)
            data.copy(resourceHistory = updated)
        }
    }

    override suspend fun clearHistory() {
        updateAccountData { it.copy(resourceHistory = emptyList()) }
    }

    override suspend fun saveSampleGroupBy(value: String) {
        settings.putString("sample_group_by", value); _sampleGroupBy.value = value
    }

    override suspend fun saveDatasetGroupBy(value: String) {
        settings.putString("dataset_group_by", value); _datasetGroupBy.value = value
    }

    override suspend fun saveInstrumentGroupBy(value: String) {
        settings.putString("instrument_group_by", value); _instrumentGroupBy.value = value
    }

    override suspend fun saveDefaultProjectTab(tab: String) {
        settings.putString("default_project_tab", tab); _defaultProjectTab.value = tab
    }

    override suspend fun savePeopleResultLimit(limit: Int) {
        settings.putInt("people_result_limit", limit); _peopleResultLimit.value = limit
    }

    override suspend fun saveProjectResultLimit(limit: Int) {
        settings.putInt("project_result_limit", limit); _projectResultLimit.value = limit
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateAccountData(update: (AccountPreferencesData) -> AccountPreferencesData) {
        val accountId = _activeAccountId.value ?: return
        currentAccountData = update(currentAccountData)
        settings.putString(accountPreferencesStorageKey(accountId), encodeAccountPreferences(currentAccountData))
        publishAccountData()
    }

    private fun publishAccountData() {
        _lastVisitedResource.value = currentAccountData.lastVisitedResource
        _lastVisitedResourceName.value = currentAccountData.lastVisitedResourceName
        _pinnedProjects.value = currentAccountData.pinnedProjects
        _syncedProjects.value = currentAccountData.syncedProjects
        _syncSetupComplete.value = currentAccountData.syncSetupComplete
        _hiddenInstruments.value = currentAccountData.hiddenInstruments
        _pinnedInstruments.value = currentAccountData.pinnedInstruments
        _userOrcid.value = currentAccountData.userOrcid
        _userProfile.value = currentAccountData.userProfile
        _resourceHistory.value = currentAccountData.resourceHistory
    }

    private fun legacyAccountData(profile: User?): AccountPreferencesData = AccountPreferencesData(
        lastVisitedResource = settings.getStringOrNull("last_visited_resource"),
        lastVisitedResourceName = settings.getStringOrNull("last_visited_resource_name"),
        pinnedProjects = settings.getString("pinned_projects", "").toStringSet(),
        syncedProjects = settings.getString("synced_projects", "").toStringSet(),
        syncSetupComplete = settings.getBoolean("sync_setup_complete", false),
        pinnedInstruments = settings.getString("pinned_instruments", "").toStringSet(),
        hiddenInstruments = settings.getString("hidden_instruments", "").toStringSet(),
        userOrcid = settings.getStringOrNull("user_orcid"),
        userProfile = profile,
        resourceHistory = settings.getString("resource_history", "").decodeHistory()
    )

    private fun removeLegacyAccountData() {
        listOf(
            "last_visited_resource",
            "last_visited_resource_name",
            "pinned_projects",
            "synced_projects",
            "sync_setup_complete",
            "hidden_instruments",
            "pinned_instruments",
            "user_orcid",
            "user_profile",
            "resource_history"
        ).forEach(settings::remove)
    }

    private fun String.toStringSet(): Set<String> =
        split(",").filter { it.isNotBlank() }.toSet()

    private fun String.decodeHistory(): List<HistoryItem> =
        split(",").mapNotNull { entry ->
            val parts = entry.split("|||")
            if (parts.size >= 3) HistoryItem(
                uuid = parts[0],
                name = parts[1],
                timestamp = parts[2].toLongOrNull() ?: 0L,
                resourceType = parts.getOrNull(3)?.ifBlank { null },
                projectId = parts.getOrNull(4)?.ifBlank { null }
            ) else null
        }

}
