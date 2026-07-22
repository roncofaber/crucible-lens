package crucible.lens.data.preferences

import com.russhwolf.settings.NSUserDefaultsSettings
import crucible.lens.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

class IosAppPreferences : AppPreferences {
    private val settings = NSUserDefaultsSettings.Factory().create("crucible_lens_prefs")
    private val iosProfileJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Private backing fields — updated synchronously on every save ──────────

    private val _apiKey = MutableStateFlow(settings.getStringOrNull("api_key"))
    override val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(settings.getString("api_base_url", AppPreferences.DEFAULT_API_BASE_URL))
    override val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _graphExplorerUrl = MutableStateFlow(settings.getString("graph_explorer_url", AppPreferences.DEFAULT_GRAPH_EXPLORER_URL))
    override val graphExplorerUrl: StateFlow<String> = _graphExplorerUrl.asStateFlow()

    private val _themeMode = MutableStateFlow(settings.getString("theme_mode", AppPreferences.THEME_MODE_SYSTEM))
    override val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(settings.getString("accent_color", AppPreferences.DEFAULT_ACCENT_COLOR))
    override val accentColor: StateFlow<String> = _accentColor.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(settings.getBoolean("use_dynamic_color", false))
    override val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _lastVisitedResource = MutableStateFlow(settings.getStringOrNull("last_visited_resource"))
    override val lastVisitedResource: StateFlow<String?> = _lastVisitedResource.asStateFlow()

    private val _lastVisitedResourceName = MutableStateFlow(settings.getStringOrNull("last_visited_resource_name"))
    override val lastVisitedResourceName: StateFlow<String?> = _lastVisitedResourceName.asStateFlow()

    private val _floatingScanButton = MutableStateFlow(settings.getBoolean("floating_scan_button", true))
    override val floatingScanButton: StateFlow<Boolean> = _floatingScanButton.asStateFlow()

    private val _pinnedProjects = MutableStateFlow(settings.getString("pinned_projects", "").toStringSet())
    override val pinnedProjects: StateFlow<Set<String>> = _pinnedProjects.asStateFlow()

    private val _hiddenProjects = MutableStateFlow(settings.getString("hidden_projects", "").toStringSet())
    override val hiddenProjects: StateFlow<Set<String>> = _hiddenProjects.asStateFlow()

    private val _hiddenInstruments = MutableStateFlow(settings.getString("hidden_instruments", "").toStringSet())
    override val hiddenInstruments: StateFlow<Set<String>> = _hiddenInstruments.asStateFlow()

    private val _pinnedInstruments = MutableStateFlow(settings.getString("pinned_instruments", "").toStringSet())
    override val pinnedInstruments: StateFlow<Set<String>> = _pinnedInstruments.asStateFlow()

    private val _userOrcid = MutableStateFlow(settings.getStringOrNull("user_orcid"))
    override val userOrcid: StateFlow<String?> = _userOrcid.asStateFlow()

    private val _userProfile = MutableStateFlow<User?>(
        settings.getStringOrNull("user_profile")?.let { json ->
            runCatching { iosProfileJson.decodeFromString<User>(json) }.getOrNull()
        }
    )
    override val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _resourceHistory = MutableStateFlow(
        settings.getString("resource_history", "").decodeHistory()
    )
    override val resourceHistory: StateFlow<List<HistoryItem>> = _resourceHistory.asStateFlow()

    private val _sampleGroupBy = MutableStateFlow(settings.getString("sample_group_by", "TYPE"))
    override val sampleGroupBy: StateFlow<String> = _sampleGroupBy.asStateFlow()

    private val _datasetGroupBy = MutableStateFlow(settings.getString("dataset_group_by", "MEASUREMENT"))
    override val datasetGroupBy: StateFlow<String> = _datasetGroupBy.asStateFlow()

    private val _defaultProjectTab = MutableStateFlow(settings.getString("default_project_tab", AppPreferences.PROJECT_TAB_SAMPLES))
    override val defaultProjectTab: StateFlow<String> = _defaultProjectTab.asStateFlow()

    // ── Save operations ───────────────────────────────────────────────────────

    override suspend fun saveApiKey(key: String) {
        settings.putString("api_key", key); _apiKey.value = key
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

    override suspend fun saveUseDynamicColor(enabled: Boolean) {
        settings.putBoolean("use_dynamic_color", enabled); _useDynamicColor.value = enabled
    }

    override suspend fun saveLastVisitedResource(uuid: String, name: String) {
        settings.putString("last_visited_resource", uuid); _lastVisitedResource.value = uuid
        settings.putString("last_visited_resource_name", name); _lastVisitedResourceName.value = name
    }

    override suspend fun saveFloatingScanButton(enabled: Boolean) {
        settings.putBoolean("floating_scan_button", enabled); _floatingScanButton.value = enabled
    }

    override suspend fun clearApiKey() {
        settings.remove("api_key"); _apiKey.value = null
    }

    override suspend fun togglePinnedProject(id: String) {
        val updated = _pinnedProjects.value.toMutableSet().apply { if (id in this) remove(id) else add(id) }
        settings.putString("pinned_projects", updated.joinToString(",")); _pinnedProjects.value = updated
    }

    override suspend fun toggleHiddenProject(id: String) {
        val updated = _hiddenProjects.value.toMutableSet().apply { if (id in this) remove(id) else add(id) }
        settings.putString("hidden_projects", updated.joinToString(",")); _hiddenProjects.value = updated
    }

    override suspend fun toggleHiddenInstrument(id: String) {
        val updated = _hiddenInstruments.value.toMutableSet().apply { if (id in this) remove(id) else add(id) }
        settings.putString("hidden_instruments", updated.joinToString(",")); _hiddenInstruments.value = updated
    }

    override suspend fun togglePinnedInstrument(id: String) {
        val updated = _pinnedInstruments.value.toMutableSet().apply { if (id in this) remove(id) else add(id) }
        settings.putString("pinned_instruments", updated.joinToString(",")); _pinnedInstruments.value = updated
    }

    override suspend fun saveUserOrcid(orcid: String?) {
        if (orcid != null) settings.putString("user_orcid", orcid) else settings.remove("user_orcid")
        _userOrcid.value = orcid
    }

    override suspend fun saveUserProfile(user: User?) {
        if (user != null) settings.putString("user_profile", iosProfileJson.encodeToString(User.serializer(), user))
        else settings.remove("user_profile")
        _userProfile.value = user
    }

    override suspend fun clearUserProfile() {
        settings.remove("user_profile"); _userProfile.value = null
    }

    override suspend fun addToHistory(uuid: String, name: String, resourceType: String?) {
        val updated = (listOf(HistoryItem(uuid, name, Clock.System.now().toEpochMilliseconds(), resourceType)) +
            _resourceHistory.value.filter { it.uuid != uuid }).take(20)
        settings.putString("resource_history", updated.encodeHistory())
        _resourceHistory.value = updated
    }

    override suspend fun clearHistory() {
        settings.remove("resource_history"); _resourceHistory.value = emptyList()
    }

    override suspend fun saveSampleGroupBy(value: String) {
        settings.putString("sample_group_by", value); _sampleGroupBy.value = value
    }

    override suspend fun saveDatasetGroupBy(value: String) {
        settings.putString("dataset_group_by", value); _datasetGroupBy.value = value
    }

    override suspend fun saveDefaultProjectTab(tab: String) {
        settings.putString("default_project_tab", tab); _defaultProjectTab.value = tab
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.toStringSet(): Set<String> =
        split(",").filter { it.isNotBlank() }.toSet()

    private fun String.decodeHistory(): List<HistoryItem> =
        split(",").mapNotNull { entry ->
            val parts = entry.split("|||")
            if (parts.size >= 3) HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L, parts.getOrNull(3)?.ifBlank { null })
            else null
        }

    private fun List<HistoryItem>.encodeHistory(): String =
        joinToString(",") { "${it.uuid}|||${it.name}|||${it.timestamp}|||${it.resourceType ?: ""}" }
}
