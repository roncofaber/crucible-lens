package crucible.lens.data.preferences

import crucible.lens.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(val uuid: String, val name: String, val timestamp: Long)

interface AppPreferences {
    // Flows
    val apiKey: Flow<String?>
    val apiBaseUrl: Flow<String>
    val graphExplorerUrl: Flow<String>
    val themeMode: Flow<String>
    val accentColor: Flow<String>
    val useDynamicColor: Flow<Boolean>
    val lastVisitedResource: Flow<String?>
    val lastVisitedResourceName: Flow<String?>
    val floatingScanButton: Flow<Boolean>
    val pinnedProjects: Flow<Set<String>>
    val hiddenProjects: Flow<Set<String>>
    val pinnedInstruments: Flow<Set<String>>
    val hiddenInstruments: Flow<Set<String>>
    val userOrcid: Flow<String?>
    val userProfile: Flow<User?>
    val resourceHistory: Flow<List<HistoryItem>>
    val sampleGroupBy: Flow<String>
    val datasetGroupBy: Flow<String>
    val defaultProjectTab: Flow<String>
    val aiApiKey: Flow<String?>
    val aiApiUrl: Flow<String>
    val aiDirectMode: Flow<Boolean>

    // Saves
    suspend fun saveApiKey(key: String)
    suspend fun saveApiBaseUrl(url: String)
    suspend fun saveGraphExplorerUrl(url: String)
    suspend fun saveThemeMode(mode: String)
    suspend fun saveAccentColor(color: String)
    suspend fun saveUseDynamicColor(enabled: Boolean)
    suspend fun saveLastVisitedResource(uuid: String, name: String)
    suspend fun saveFloatingScanButton(enabled: Boolean)
    suspend fun clearApiKey()
    suspend fun togglePinnedProject(id: String)
    suspend fun toggleHiddenProject(id: String)
    suspend fun togglePinnedInstrument(id: String)
    suspend fun toggleHiddenInstrument(id: String)
    suspend fun saveUserOrcid(orcid: String?)
    suspend fun saveUserProfile(user: User?)
    suspend fun clearUserProfile()
    suspend fun addToHistory(uuid: String, name: String)
    suspend fun clearHistory()
    suspend fun saveSampleGroupBy(value: String)
    suspend fun saveDatasetGroupBy(value: String)
    suspend fun saveDefaultProjectTab(tab: String)
    suspend fun saveAiApiKey(key: String)
    suspend fun saveAiApiUrl(url: String)
    suspend fun saveAiDirectMode(enabled: Boolean)

    companion object {
        const val PROJECT_TAB_SAMPLES = "SAMPLES"
        const val PROJECT_TAB_DATASETS = "DATASETS"
        const val DEFAULT_API_BASE_URL = "https://crucible.lbl.gov/api/v2/"
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible-graph-explorer-776258882599.us-central1.run.app"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "blue"
        const val DEFAULT_AI_API_URL = "https://api.cborg.lbl.gov"
    }
}
