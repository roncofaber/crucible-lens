package crucible.lens.data.preferences

import crucible.lens.data.model.User
import crucible.lens.data.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class HistoryItem(
    val uuid: String,
    val name: String,
    val timestamp: Long,
    val resourceType: String? = null,
    val projectId: String? = null
)

interface AppPreferences {
    // StateFlows — always have a current value, no initial value needed at collection sites
    val isLoaded: StateFlow<Boolean>
    val apiKey: StateFlow<String?>
    val activeAccountId: StateFlow<String?>
    val apiBaseUrl: StateFlow<String>
    val graphExplorerUrl: StateFlow<String>
    val themeMode: StateFlow<String>
    val accentColor: StateFlow<String>
    val accentContrast: StateFlow<String>
    val useDynamicColor: StateFlow<Boolean>
    val lastVisitedResource: StateFlow<String?>
    val lastVisitedResourceName: StateFlow<String?>
    val floatingScanButton: StateFlow<Boolean>
    val pinnedProjects: StateFlow<Set<String>>
    val syncedProjects: StateFlow<Set<String>>
    val syncSetupComplete: StateFlow<Boolean>
    val pinnedInstruments: StateFlow<Set<String>>
    val hiddenInstruments: StateFlow<Set<String>>
    val userOrcid: StateFlow<String?>
    val userProfile: StateFlow<User?>
    val resourceHistory: StateFlow<List<HistoryItem>>
    val sampleGroupBy: StateFlow<String>
    val datasetGroupBy: StateFlow<String>
    val instrumentGroupBy: StateFlow<String>
    val defaultProjectTab: StateFlow<String>
    val peopleResultLimit: StateFlow<Int>
    val projectResultLimit: StateFlow<Int>

    // Saves
    suspend fun saveApiKey(key: String)
    suspend fun activateAccount(accountId: String)
    suspend fun deactivateAccount()
    suspend fun saveApiBaseUrl(url: String)
    suspend fun saveGraphExplorerUrl(url: String)
    suspend fun saveThemeMode(mode: String)
    suspend fun saveAccentColor(color: String)
    suspend fun saveAccentContrast(contrast: String)
    suspend fun saveUseDynamicColor(enabled: Boolean)
    suspend fun saveLastVisitedResource(uuid: String, name: String)
    suspend fun saveFloatingScanButton(enabled: Boolean)
    suspend fun clearApiKey()
    suspend fun togglePinnedProject(id: String)
    suspend fun setPinnedProjects(ids: Set<String>)
    suspend fun toggleSyncedProject(id: String)
    suspend fun setSyncedProjects(ids: Set<String>)
    suspend fun togglePinnedInstrument(id: String)
    suspend fun toggleHiddenInstrument(id: String)
    suspend fun saveSyncSetupComplete(complete: Boolean)
    suspend fun saveUserOrcid(orcid: String?)
    suspend fun saveUserProfile(user: User?)
    suspend fun clearUserProfile()
    suspend fun addToHistory(uuid: String, name: String, resourceType: String? = null, projectId: String? = null)
    suspend fun clearHistory()
    suspend fun saveSampleGroupBy(value: String)
    suspend fun saveDatasetGroupBy(value: String)
    suspend fun saveInstrumentGroupBy(value: String)
    suspend fun saveDefaultProjectTab(tab: String)
    suspend fun savePeopleResultLimit(limit: Int)
    suspend fun saveProjectResultLimit(limit: Int)

    companion object {
        const val PROJECT_TAB_SAMPLES = "SAMPLES"
        const val PROJECT_TAB_DATASETS = "DATASETS"
        const val DEFAULT_API_BASE_URL = "https://crucible.lbl.gov/api/v3/"
        const val RETIRED_API_BASE_URL = "https://crucible.lbl.gov/api/v2/"
        const val DEFAULT_GRAPH_EXPLORER_URL = "https://crucible.lbl.gov/explore/"
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val DEFAULT_ACCENT_COLOR = "cerulean"
        const val DEFAULT_ACCENT_CONTRAST = "standard"
        const val DEFAULT_SEARCH_RESULT_LIMIT = 5
    }
}

internal fun migrateOfficialApiBaseUrl(url: String): String =
    if (url.trim().trimEnd('/') + "/" == AppPreferences.RETIRED_API_BASE_URL) {
        AppPreferences.DEFAULT_API_BASE_URL
    } else {
        url
    }

internal fun migratePinnedProjectReferences(references: Set<String>, projects: List<Project>): Set<String> {
    val mfidBySlug = projects.associate { it.projectId to it.uniqueId }
    return references.mapTo(mutableSetOf()) { mfidBySlug[it] ?: it }
}

internal fun migrateSyncedProjectReferences(references: Set<String>, projects: List<Project>): Set<String> {
    val projectByReference = projects.flatMap { project ->
        listOf(project.uniqueId to project, project.projectId to project)
    }.toMap()
    return references.mapNotNullTo(mutableSetOf()) { projectByReference[it]?.uniqueId }
}

internal fun syncedProjectReferencesNeedMigration(references: Set<String>, projects: List<Project>): Boolean =
    references.any { reference -> projects.none { it.uniqueId == reference } }

internal fun isMfidReference(reference: String): Boolean =
    crucible.lens.data.util.isMfidReference(reference)
