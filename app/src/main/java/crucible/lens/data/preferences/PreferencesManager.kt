package crucible.lens.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) : AppPreferences {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val GRAPH_EXPLORER_URL = stringPreferencesKey("graph_explorer_url")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val LAST_VISITED_RESOURCE = stringPreferencesKey("last_visited_resource")
        private val LAST_VISITED_RESOURCE_NAME = stringPreferencesKey("last_visited_resource_name")
        private val FLOATING_SCAN_BUTTON = stringPreferencesKey("floating_scan_button")
        private val PINNED_PROJECTS = stringPreferencesKey("pinned_projects")
        private val HIDDEN_PROJECTS = stringPreferencesKey("hidden_projects")
        private val HIDDEN_INSTRUMENTS = stringPreferencesKey("hidden_instruments")
        private val RESOURCE_HISTORY = stringPreferencesKey("resource_history")
        private val SAMPLE_GROUP_BY = stringPreferencesKey("sample_group_by")
        private val DATASET_GROUP_BY = stringPreferencesKey("dataset_group_by")
        private val DEFAULT_PROJECT_TAB = stringPreferencesKey("default_project_tab")
        private val USER_ORCID = stringPreferencesKey("user_orcid")
        private val PINNED_INSTRUMENTS = stringPreferencesKey("pinned_instruments")
        private val USE_DYNAMIC_COLOR = stringPreferencesKey("use_dynamic_color")
        private val AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val AI_API_URL = stringPreferencesKey("ai_api_url")
        private val AI_DIRECT_MODE = stringPreferencesKey("ai_direct_mode")

        const val PROJECT_TAB_SAMPLES = "SAMPLES"
        const val PROJECT_TAB_DATASETS = "DATASETS"

        const val DEFAULT_API_BASE_URL = "https://crucible.lbl.gov/api/v2/"
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible-graph-explorer-776258882599.us-central1.run.app"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "blue"
    }

    override val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    override val apiBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_BASE_URL] ?: DEFAULT_API_BASE_URL
    }

    override val graphExplorerUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GRAPH_EXPLORER_URL] ?: DEFAULT_GRAPH_EXPLORER_URL
    }

    override val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: THEME_MODE_SYSTEM
    }

    override val accentColor: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
    }

    override val lastVisitedResource: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_VISITED_RESOURCE]
    }

    override val lastVisitedResourceName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_VISITED_RESOURCE_NAME]
    }

    override val floatingScanButton: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_SCAN_BUTTON]?.toBoolean() ?: false
    }

    override val pinnedProjects: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PINNED_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    override val hiddenProjects: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    override val hiddenInstruments: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_INSTRUMENTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    override val sampleGroupBy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SAMPLE_GROUP_BY] ?: "TYPE"
    }

    override val datasetGroupBy: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DATASET_GROUP_BY] ?: "MEASUREMENT"
    }

    override val defaultProjectTab: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_PROJECT_TAB] ?: PROJECT_TAB_SAMPLES
    }

    override val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_DYNAMIC_COLOR]?.toBoolean() ?: false
    }

    override val pinnedInstruments: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PINNED_INSTRUMENTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    override val userOrcid: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_ORCID]
    }

    override val aiApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[AI_API_KEY]
    }

    override val aiApiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AI_API_URL] ?: AppPreferences.DEFAULT_AI_API_URL
    }

    override val aiDirectMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AI_DIRECT_MODE]?.toBoolean() ?: false
    }

    override val resourceHistory: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        prefs[RESOURCE_HISTORY]?.split(",")?.mapNotNull { entry ->
            val parts = entry.split("|||")
            if (parts.size == 3) HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
        } ?: emptyList()
    }

    override suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
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

    override suspend fun saveLastVisitedResource(uuid: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_VISITED_RESOURCE] = uuid
            preferences[LAST_VISITED_RESOURCE_NAME] = name
        }
    }

    override suspend fun saveFloatingScanButton(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_SCAN_BUTTON] = enabled.toString()
        }
    }

    override suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
    }

    override suspend fun togglePinnedProject(id: String) {
        context.dataStore.edit { prefs ->
            val projects = prefs[PINNED_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            val instruments = prefs[PINNED_INSTRUMENTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            if (id in projects) projects.remove(id)
            else projects.add(id)
            prefs[PINNED_PROJECTS] = projects.joinToString(",")
        }
    }

    override suspend fun toggleHiddenProject(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (id in current) current.remove(id) else current.add(id)
            prefs[HIDDEN_PROJECTS] = current.joinToString(",")
        }
    }

    override suspend fun toggleHiddenInstrument(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN_INSTRUMENTS]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (id in current) current.remove(id) else current.add(id)
            prefs[HIDDEN_INSTRUMENTS] = current.joinToString(",")
        }
    }

    override suspend fun saveSampleGroupBy(value: String) {
        context.dataStore.edit { prefs -> prefs[SAMPLE_GROUP_BY] = value }
    }

    override suspend fun saveDatasetGroupBy(value: String) {
        context.dataStore.edit { prefs -> prefs[DATASET_GROUP_BY] = value }
    }

    override suspend fun saveDefaultProjectTab(tab: String) {
        context.dataStore.edit { prefs -> prefs[DEFAULT_PROJECT_TAB] = tab }
    }

    override suspend fun saveUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[USE_DYNAMIC_COLOR] = enabled.toString() }
    }

    override suspend fun togglePinnedInstrument(id: String) {
        context.dataStore.edit { prefs ->
            val instruments = prefs[PINNED_INSTRUMENTS]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            val projects = prefs[PINNED_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            if (id in instruments) instruments.remove(id)
            else instruments.add(id)
            prefs[PINNED_INSTRUMENTS] = instruments.joinToString(",")
        }
    }

    override suspend fun saveUserOrcid(orcid: String?) {
        context.dataStore.edit { preferences ->
            if (orcid != null) preferences[USER_ORCID] = orcid
            else preferences.remove(USER_ORCID)
        }
    }

    override suspend fun saveAiApiKey(key: String) {
        context.dataStore.edit { preferences -> preferences[AI_API_KEY] = key }
    }

    override suspend fun saveAiApiUrl(url: String) {
        context.dataStore.edit { preferences -> preferences[AI_API_URL] = url }
    }

    override suspend fun saveAiDirectMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AI_DIRECT_MODE] = enabled.toString() }
    }

    override suspend fun clearHistory() {
        context.dataStore.edit { prefs -> prefs.remove(RESOURCE_HISTORY) }
    }

    override suspend fun addToHistory(uuid: String, name: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[RESOURCE_HISTORY]?.split(",")?.mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size == 3) HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
            } ?: emptyList()
            val updated = listOf(HistoryItem(uuid, name, System.currentTimeMillis())) +
                existing.filter { it.uuid != uuid }
            prefs[RESOURCE_HISTORY] = updated.take(20).joinToString(",") { "${it.uuid}|||${it.name}|||${it.timestamp}" }
        }
    }
}
