package crucible.lens.data.preferences

import crucible.lens.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(val uuid: String, val name: String, val timestamp: Long, val resourceType: String? = null)

interface AppPreferences {
    // StateFlows — always have a current value, no initial value needed at collection sites
    val apiKey: StateFlow<String?>
    val apiBaseUrl: StateFlow<String>
    val graphExplorerUrl: StateFlow<String>
    val themeMode: StateFlow<String>
    val accentColor: StateFlow<String>
    val useDynamicColor: StateFlow<Boolean>
    val lastVisitedResource: StateFlow<String?>
    val lastVisitedResourceName: StateFlow<String?>
    val floatingScanButton: StateFlow<Boolean>
    val pinnedProjects: StateFlow<Set<String>>
    val hiddenProjects: StateFlow<Set<String>>
    val pinnedInstruments: StateFlow<Set<String>>
    val hiddenInstruments: StateFlow<Set<String>>
    val userOrcid: StateFlow<String?>
    val userProfile: StateFlow<User?>
    val resourceHistory: StateFlow<List<HistoryItem>>
    val sampleGroupBy: StateFlow<String>
    val datasetGroupBy: StateFlow<String>
    val defaultProjectTab: StateFlow<String>

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
    suspend fun addToHistory(uuid: String, name: String, resourceType: String? = null)
    suspend fun clearHistory()
    suspend fun saveSampleGroupBy(value: String)
    suspend fun saveDatasetGroupBy(value: String)
    suspend fun saveDefaultProjectTab(tab: String)

    companion object {
        const val PROJECT_TAB_SAMPLES = "SAMPLES"
        const val PROJECT_TAB_DATASETS = "DATASETS"
        const val DEFAULT_API_BASE_URL = "https://crucible.lbl.gov/api/v2/"
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible.lbl.gov/explore/"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "blue"
    }
}
