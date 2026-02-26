package gov.lbl.crucible.scanner.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HistoryItem(val uuid: String, val name: String, val timestamp: Long)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val GRAPH_EXPLORER_URL = stringPreferencesKey("graph_explorer_url")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val LAST_VISITED_RESOURCE = stringPreferencesKey("last_visited_resource")
        private val LAST_VISITED_RESOURCE_NAME = stringPreferencesKey("last_visited_resource_name")
        private val SMOOTH_ANIMATIONS = stringPreferencesKey("smooth_animations")
        private val FLOATING_SCAN_BUTTON = stringPreferencesKey("floating_scan_button")
        private val PINNED_PROJECTS = stringPreferencesKey("pinned_projects")
        private val RESOURCE_HISTORY = stringPreferencesKey("resource_history")

        const val DEFAULT_API_BASE_URL = "https://crucible.lbl.gov/api/v1/"
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible-graph-explorer-776258882599.us-central1.run.app"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "blue"
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    val apiBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_BASE_URL] ?: DEFAULT_API_BASE_URL
    }

    val graphExplorerUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GRAPH_EXPLORER_URL] ?: DEFAULT_GRAPH_EXPLORER_URL
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: THEME_MODE_SYSTEM
    }

    val accentColor: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: DEFAULT_ACCENT_COLOR
    }

    val lastVisitedResource: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_VISITED_RESOURCE]
    }

    val lastVisitedResourceName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_VISITED_RESOURCE_NAME]
    }

    val smoothAnimations: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMOOTH_ANIMATIONS]?.toBoolean() ?: true // Default to true
    }

    val floatingScanButton: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOATING_SCAN_BUTTON]?.toBoolean() ?: true // Default to enabled
    }

    val pinnedProjects: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[PINNED_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    val resourceHistory: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        prefs[RESOURCE_HISTORY]?.split(",")?.mapNotNull { entry ->
            val parts = entry.split("|||")
            if (parts.size == 3) HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
        } ?: emptyList()
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun saveApiBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL] = url
        }
    }

    suspend fun saveGraphExplorerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[GRAPH_EXPLORER_URL] = url
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun saveAccentColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCENT_COLOR] = color
        }
    }

    suspend fun saveLastVisitedResource(uuid: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_VISITED_RESOURCE] = uuid
            preferences[LAST_VISITED_RESOURCE_NAME] = name
        }
    }

    suspend fun saveSmoothAnimations(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMOOTH_ANIMATIONS] = enabled.toString()
        }
    }

    suspend fun saveFloatingScanButton(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOATING_SCAN_BUTTON] = enabled.toString()
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(API_KEY)
        }
    }

    suspend fun togglePinnedProject(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PINNED_PROJECTS]?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (id in current) current.remove(id) else current.add(id)
            prefs[PINNED_PROJECTS] = current.joinToString(",")
        }
    }

    suspend fun addToHistory(uuid: String, name: String) {
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
