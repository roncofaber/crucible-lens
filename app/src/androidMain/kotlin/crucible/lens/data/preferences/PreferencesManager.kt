package crucible.lens.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import crucible.lens.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(
    private val context: Context,
    secureCredentialStore: SecureCredentialStore = AndroidSecureCredentialStore(context)
) : AppPreferences {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialManager = SecureCredentialManager(secureCredentialStore)
    private val credentialsLoaded = MutableStateFlow(false)

    private val dataStoreLoaded = context.dataStore.data
        .map { true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val isLoaded: StateFlow<Boolean> = combine(dataStoreLoaded, credentialsLoaded) { data, credentials ->
        data && credentials
    }.stateIn(scope, SharingStarted.Eagerly, false)

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val GRAPH_EXPLORER_URL = stringPreferencesKey("graph_explorer_url")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val ACCENT_CONTRAST = stringPreferencesKey("accent_contrast")
        private val LAST_VISITED_RESOURCE = stringPreferencesKey("last_visited_resource")
        private val LAST_VISITED_RESOURCE_NAME = stringPreferencesKey("last_visited_resource_name")
        private val FLOATING_SCAN_BUTTON = stringPreferencesKey("floating_scan_button")
        private val PINNED_PROJECTS = stringPreferencesKey("pinned_projects")
        private val SYNCED_PROJECTS = stringPreferencesKey("synced_projects")
        private val SYNC_SETUP_COMPLETE = stringPreferencesKey("sync_setup_complete")
        private val HIDDEN_INSTRUMENTS = stringPreferencesKey("hidden_instruments")
        private val RESOURCE_HISTORY = stringPreferencesKey("resource_history")
        private val SAMPLE_GROUP_BY = stringPreferencesKey("sample_group_by")
        private val INSTRUMENT_GROUP_BY = stringPreferencesKey("instrument_group_by")
        private val DATASET_GROUP_BY = stringPreferencesKey("dataset_group_by")
        private val DEFAULT_PROJECT_TAB = stringPreferencesKey("default_project_tab")
        private val USER_ORCID = stringPreferencesKey("user_orcid")
        private val USER_PROFILE = stringPreferencesKey("user_profile")
        private val PINNED_INSTRUMENTS = stringPreferencesKey("pinned_instruments")
        private val USE_DYNAMIC_COLOR = stringPreferencesKey("use_dynamic_color")
        private val PEOPLE_RESULT_LIMIT = intPreferencesKey("people_result_limit")
        private val PROJECT_RESULT_LIMIT = intPreferencesKey("project_result_limit")

        const val PROJECT_TAB_SAMPLES = "SAMPLES"
        const val PROJECT_TAB_DATASETS = "DATASETS"

        const val DEFAULT_API_BASE_URL = AppPreferences.DEFAULT_API_BASE_URL
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible.lbl.gov/explore/"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "cerulean"
        const val DEFAULT_ACCENT_CONTRAST = "standard"

        private val profileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private fun Preferences.legacyProfile(): User? = this[USER_PROFILE]?.let { value ->
        runCatching { profileJson.decodeFromString<User>(value) }.getOrNull()
    }

    private fun Preferences.legacyAccountData(): AccountPreferencesData = AccountPreferencesData(
        lastVisitedResource = this[LAST_VISITED_RESOURCE],
        lastVisitedResourceName = this[LAST_VISITED_RESOURCE_NAME],
        pinnedProjects = this[PINNED_PROJECTS].toStringSet(),
        syncedProjects = this[SYNCED_PROJECTS].toStringSet(),
        syncSetupComplete = this[SYNC_SETUP_COMPLETE]?.toBoolean() ?: false,
        pinnedInstruments = this[PINNED_INSTRUMENTS].toStringSet(),
        hiddenInstruments = this[HIDDEN_INSTRUMENTS].toStringSet(),
        userOrcid = this[USER_ORCID],
        userProfile = legacyProfile(),
        resourceHistory = this[RESOURCE_HISTORY]?.split(",")?.mapNotNull { it.toHistoryItem() } ?: emptyList()
    )

    private fun Preferences.currentAccount(): Pair<String?, AccountPreferencesData> {
        val activeId = this[ACTIVE_ACCOUNT_ID]
        if (activeId != null) {
            val value = this[stringPreferencesKey(accountPreferencesStorageKey(activeId))]
            return activeId to decodeAccountPreferences(value)
        }
        val legacyProfile = legacyProfile()
        val legacyId = legacyProfile?.let(::accountIdFor) ?: this[USER_ORCID]
        return legacyId to if (legacyId != null) legacyAccountData() else AccountPreferencesData()
    }

    private val accountState = context.dataStore.data
        .map { it.currentAccount() }
        .stateIn(scope, SharingStarted.Eagerly, null to AccountPreferencesData())

    override val activeAccountId: StateFlow<String?> = accountState
        .map { it.first }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _apiKey = MutableStateFlow<String?>(null)
    override val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    init {
        scope.launch {
            try {
                _apiKey.value = credentialManager.loadAndMigrate(
                    legacyCredential = { context.dataStore.data.first()[API_KEY] },
                    removeLegacyCredential = {
                        context.dataStore.edit { preferences -> preferences.remove(API_KEY) }
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _apiKey.value = null
            } finally {
                credentialsLoaded.value = true
            }
        }
    }

    override val apiBaseUrl: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[API_BASE_URL] ?: DEFAULT_API_BASE_URL
    }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_API_BASE_URL)

    override val graphExplorerUrl: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[GRAPH_EXPLORER_URL] ?: DEFAULT_GRAPH_EXPLORER_URL
    }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_GRAPH_EXPLORER_URL)

    override val themeMode: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: THEME_MODE_SYSTEM
    }
        .stateIn(scope, SharingStarted.Eagerly, THEME_MODE_SYSTEM)

    override val accentColor: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
    }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_ACCENT_COLOR)

    override val accentContrast: StateFlow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_CONTRAST] ?: DEFAULT_ACCENT_CONTRAST
    }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_ACCENT_CONTRAST)

    override val lastVisitedResource: StateFlow<String?> = accountState.map { it.second.lastVisitedResource }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val lastVisitedResourceName: StateFlow<String?> = accountState.map { it.second.lastVisitedResourceName }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val floatingScanButton: StateFlow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_SCAN_BUTTON]?.toBoolean() ?: false
    }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val pinnedProjects: StateFlow<Set<String>> = accountState.map { it.second.pinnedProjects }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override val syncedProjects: StateFlow<Set<String>> = accountState.map { it.second.syncedProjects }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override val syncSetupComplete: StateFlow<Boolean> = accountState.map { it.second.syncSetupComplete }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val hiddenInstruments: StateFlow<Set<String>> = accountState.map { it.second.hiddenInstruments }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override val sampleGroupBy: StateFlow<String> = context.dataStore.data.map { prefs ->
        prefs[SAMPLE_GROUP_BY] ?: "TYPE"
    }
        .stateIn(scope, SharingStarted.Eagerly, "TYPE")

    override val datasetGroupBy: StateFlow<String> = context.dataStore.data.map { prefs ->
        prefs[DATASET_GROUP_BY] ?: "MEASUREMENT"
    }
        .stateIn(scope, SharingStarted.Eagerly, "MEASUREMENT")

    override val instrumentGroupBy: StateFlow<String> = context.dataStore.data.map { prefs ->
        prefs[INSTRUMENT_GROUP_BY] ?: "MEASUREMENT"
    }
        .stateIn(scope, SharingStarted.Eagerly, "MEASUREMENT")

    override val defaultProjectTab: StateFlow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_PROJECT_TAB] ?: PROJECT_TAB_SAMPLES
    }
        .stateIn(scope, SharingStarted.Eagerly, PROJECT_TAB_SAMPLES)

    override val useDynamicColor: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR]?.toBoolean() ?: false
    }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val peopleResultLimit: StateFlow<Int> = context.dataStore.data.map { prefs ->
        prefs[PEOPLE_RESULT_LIMIT] ?: AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT
    }
        .stateIn(scope, SharingStarted.Eagerly, AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT)

    override val projectResultLimit: StateFlow<Int> = context.dataStore.data.map { prefs ->
        prefs[PROJECT_RESULT_LIMIT] ?: AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT
    }
        .stateIn(scope, SharingStarted.Eagerly, AppPreferences.DEFAULT_SEARCH_RESULT_LIMIT)

    override val pinnedInstruments: StateFlow<Set<String>> = accountState.map { it.second.pinnedInstruments }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override val userOrcid: StateFlow<String?> = accountState.map { it.second.userOrcid }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val userProfile: StateFlow<User?> = accountState.map { it.second.userProfile }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val resourceHistory: StateFlow<List<HistoryItem>> = accountState.map { it.second.resourceHistory }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun saveApiKey(key: String) {
        credentialManager.save(key)
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
        _apiKey.value = key
    }

    override suspend fun activateAccount(accountId: String) {
        context.dataStore.edit { prefs ->
            val storageKey = stringPreferencesKey(accountPreferencesStorageKey(accountId))
            if (prefs[storageKey] == null) {
                val legacyOwner = prefs.legacyProfile()?.let(::accountIdFor) ?: prefs[USER_ORCID]
                val initialData = if (legacyOwner == accountId) {
                    prefs.legacyAccountData()
                } else {
                    AccountPreferencesData()
                }
                prefs[storageKey] = encodeAccountPreferences(initialData)
            }
            prefs[ACTIVE_ACCOUNT_ID] = accountId
            prefs.removeLegacyAccountData()
        }
    }

    override suspend fun deactivateAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACTIVE_ACCOUNT_ID)
            prefs.removeLegacyAccountData()
        }
    }

    override suspend fun saveApiBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL] = url
        }
    }

    override suspend fun saveGraphExplorerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[GRAPH_EXPLORER_URL] = url
        }
    }

    override suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    override suspend fun saveAccentColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    override suspend fun saveAccentContrast(contrast: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_CONTRAST] = contrast
        }
    }

    override suspend fun saveLastVisitedResource(uuid: String, name: String) {
        updateAccountData { data ->
            data.copy(lastVisitedResource = uuid, lastVisitedResourceName = name)
        }
    }

    private suspend fun updateAccountData(update: (AccountPreferencesData) -> AccountPreferencesData) {
        context.dataStore.edit { prefs ->
            val accountId = prefs[ACTIVE_ACCOUNT_ID] ?: return@edit
            val key = stringPreferencesKey(accountPreferencesStorageKey(accountId))
            prefs[key] = encodeAccountPreferences(update(decodeAccountPreferences(prefs[key])))
        }
    }

    override suspend fun saveFloatingScanButton(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_SCAN_BUTTON] = enabled.toString()
        }
    }

    override suspend fun clearApiKey() {
        credentialManager.clear()
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
        _apiKey.value = null
    }

    override suspend fun togglePinnedProject(id: String) {
        updateAccountData { data ->
            val current = data.pinnedProjects.toMutableSet()
            val adding = id !in current
            if (adding) current.add(id) else current.remove(id)
            val synced = data.syncedProjects.toMutableSet()
            if (adding) synced.add(id)
            data.copy(pinnedProjects = current, syncedProjects = synced)
        }
    }

    override suspend fun setPinnedProjects(ids: Set<String>) {
        updateAccountData { it.copy(pinnedProjects = ids) }
    }

    override suspend fun toggleSyncedProject(id: String) {
        updateAccountData { data ->
            val current = data.syncedProjects.toMutableSet()
            if (id in current) current.remove(id) else current.add(id)
            data.copy(syncedProjects = current)
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
            val current = data.hiddenInstruments.toMutableSet()
            if (id in current) current.remove(id) else current.add(id)
            data.copy(hiddenInstruments = current)
        }
    }

    override suspend fun saveSampleGroupBy(value: String) {
        context.dataStore.edit { prefs -> prefs[SAMPLE_GROUP_BY] = value }
    }

    override suspend fun saveDatasetGroupBy(value: String) {
        context.dataStore.edit { prefs -> prefs[DATASET_GROUP_BY] = value }
    }

    override suspend fun saveInstrumentGroupBy(value: String) {
        context.dataStore.edit { prefs -> prefs[INSTRUMENT_GROUP_BY] = value }
    }

    override suspend fun saveDefaultProjectTab(tab: String) {
        context.dataStore.edit { prefs -> prefs[DEFAULT_PROJECT_TAB] = tab }
    }

    override suspend fun saveUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[USE_DYNAMIC_COLOR] = enabled.toString() }
    }

    override suspend fun savePeopleResultLimit(limit: Int) {
        context.dataStore.edit { prefs -> prefs[PEOPLE_RESULT_LIMIT] = limit }
    }

    override suspend fun saveProjectResultLimit(limit: Int) {
        context.dataStore.edit { prefs -> prefs[PROJECT_RESULT_LIMIT] = limit }
    }

    override suspend fun togglePinnedInstrument(id: String) {
        updateAccountData { data ->
            val instruments = data.pinnedInstruments.toMutableSet()
            if (id in instruments) instruments.remove(id)
            else instruments.add(id)
            data.copy(pinnedInstruments = instruments)
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

    override suspend fun clearHistory() {
        updateAccountData { it.copy(resourceHistory = emptyList()) }
    }

    override suspend fun addToHistory(uuid: String, name: String, resourceType: String?, projectId: String?) {
        updateAccountData { data ->
            val updated = listOf(HistoryItem(uuid, name, System.currentTimeMillis(), resourceType, projectId)) +
                data.resourceHistory.filter { it.uuid != uuid }
            data.copy(resourceHistory = updated.take(20))
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.removeLegacyAccountData() {
        remove(LAST_VISITED_RESOURCE)
        remove(LAST_VISITED_RESOURCE_NAME)
        remove(PINNED_PROJECTS)
        remove(SYNCED_PROJECTS)
        remove(SYNC_SETUP_COMPLETE)
        remove(HIDDEN_INSTRUMENTS)
        remove(PINNED_INSTRUMENTS)
        remove(USER_ORCID)
        remove(USER_PROFILE)
        remove(RESOURCE_HISTORY)
    }

    private fun String?.toStringSet(): Set<String> =
        this?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun String.toHistoryItem(): HistoryItem? {
        val parts = split("|||")
        return if (parts.size >= 3) HistoryItem(
            uuid = parts[0],
            name = parts[1],
            timestamp = parts[2].toLongOrNull() ?: 0L,
            resourceType = parts.getOrNull(3)?.ifBlank { null },
            projectId = parts.getOrNull(4)?.ifBlank { null }
        ) else null
    }

}
