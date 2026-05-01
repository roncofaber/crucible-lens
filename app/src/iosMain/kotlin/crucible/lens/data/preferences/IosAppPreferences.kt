package crucible.lens.data.preferences

import com.russhwolf.settings.NSUserDefaultsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlinx.serialization.json.Json

class IosAppPreferences : AppPreferences {
    private val settings = NSUserDefaultsSettings.Factory().create("crucible_lens_prefs")
    private val json = Json

    override val apiKey: Flow<String?> =
        MutableStateFlow(settings.getStringOrNull("api_key"))

    override val apiBaseUrl: Flow<String> =
        MutableStateFlow(settings.getString("api_base_url", AppPreferences.DEFAULT_API_BASE_URL))

    override val graphExplorerUrl: Flow<String> =
        MutableStateFlow(settings.getString("graph_explorer_url", AppPreferences.DEFAULT_GRAPH_EXPLORER_URL))

    override val themeMode: Flow<String> =
        MutableStateFlow(settings.getString("theme_mode", AppPreferences.THEME_MODE_SYSTEM))

    override val accentColor: Flow<String> =
        MutableStateFlow(settings.getString("accent_color", AppPreferences.DEFAULT_ACCENT_COLOR))

    override val useDynamicColor: Flow<Boolean> =
        MutableStateFlow(settings.getBoolean("use_dynamic_color", false))

    override val lastVisitedResource: Flow<String?> =
        MutableStateFlow(settings.getStringOrNull("last_visited_resource"))

    override val lastVisitedResourceName: Flow<String?> =
        MutableStateFlow(settings.getStringOrNull("last_visited_resource_name"))

    override val floatingScanButton: Flow<Boolean> =
        MutableStateFlow(settings.getBoolean("floating_scan_button", true))

    override val pinnedProjects: Flow<Set<String>> =
        MutableStateFlow(
            settings.getString("pinned_projects", "")
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        )

    override val hiddenProjects: Flow<Set<String>> =
        MutableStateFlow(
            settings.getString("hidden_projects", "")
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        )

    override val hiddenInstruments: Flow<Set<String>> =
        MutableStateFlow(
            settings.getString("hidden_instruments", "")
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        )

    override val pinnedInstruments: Flow<Set<String>> =
        MutableStateFlow(
            settings.getString("pinned_instruments", "")
                .split(",")
                .filter { it.isNotBlank() }
                .toSet()
        )

    override val userOrcid: Flow<String?> =
        MutableStateFlow(settings.getStringOrNull("user_orcid"))

    override val resourceHistory: Flow<List<HistoryItem>> =
        MutableStateFlow(
            settings.getString("resource_history", "")
                .split(",")
                .mapNotNull { entry ->
                    val parts = entry.split("|||")
                    if (parts.size == 3) {
                        HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
                    } else {
                        null
                    }
                }
        )

    override val sampleGroupBy: Flow<String> =
        MutableStateFlow(settings.getString("sample_group_by", "TYPE"))

    override val datasetGroupBy: Flow<String> =
        MutableStateFlow(settings.getString("dataset_group_by", "MEASUREMENT"))

    override val defaultProjectTab: Flow<String> =
        MutableStateFlow(settings.getString("default_project_tab", AppPreferences.PROJECT_TAB_SAMPLES))

    override suspend fun saveApiKey(key: String) {
        settings.putString("api_key", key)
        (apiKey as? MutableStateFlow)?.value = key
    }

    override suspend fun saveApiBaseUrl(url: String) {
        settings.putString("api_base_url", url)
        (apiBaseUrl as? MutableStateFlow)?.value = url
    }

    override suspend fun saveGraphExplorerUrl(url: String) {
        settings.putString("graph_explorer_url", url)
        (graphExplorerUrl as? MutableStateFlow)?.value = url
    }

    override suspend fun saveThemeMode(mode: String) {
        settings.putString("theme_mode", mode)
        (themeMode as? MutableStateFlow)?.value = mode
    }

    override suspend fun saveAccentColor(color: String) {
        settings.putString("accent_color", color)
        (accentColor as? MutableStateFlow)?.value = color
    }

    override suspend fun saveUseDynamicColor(enabled: Boolean) {
        settings.putBoolean("use_dynamic_color", enabled)
        (useDynamicColor as? MutableStateFlow)?.value = enabled
    }

    override suspend fun saveLastVisitedResource(uuid: String, name: String) {
        settings.putString("last_visited_resource", uuid)
        settings.putString("last_visited_resource_name", name)
        (lastVisitedResource as? MutableStateFlow)?.value = uuid
        (lastVisitedResourceName as? MutableStateFlow)?.value = name
    }

    override suspend fun saveFloatingScanButton(enabled: Boolean) {
        settings.putBoolean("floating_scan_button", enabled)
        (floatingScanButton as? MutableStateFlow)?.value = enabled
    }

    override suspend fun clearApiKey() {
        settings.remove("api_key")
        (apiKey as? MutableStateFlow)?.value = null
    }

    override suspend fun togglePinnedProject(id: String) {
        val projects = settings.getString("pinned_projects", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableSet()
        val instruments = settings.getString("pinned_instruments", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()

        if (id in projects) projects.remove(id)
        else projects.add(id)

        settings.putString("pinned_projects", projects.joinToString(","))
        (pinnedProjects as? MutableStateFlow)?.value = projects
    }

    override suspend fun toggleHiddenProject(id: String) {
        val current = settings.getString("hidden_projects", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        settings.putString("hidden_projects", current.joinToString(","))
        (hiddenProjects as? MutableStateFlow)?.value = current
    }

    override suspend fun toggleHiddenInstrument(id: String) {
        val current = settings.getString("hidden_instruments", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        settings.putString("hidden_instruments", current.joinToString(","))
        (hiddenInstruments as? MutableStateFlow)?.value = current
    }

    override suspend fun togglePinnedInstrument(id: String) {
        val instruments = settings.getString("pinned_instruments", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toMutableSet()
        val projects = settings.getString("pinned_projects", "")
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()

        if (id in instruments) instruments.remove(id)
        else instruments.add(id)

        settings.putString("pinned_instruments", instruments.joinToString(","))
        (pinnedInstruments as? MutableStateFlow)?.value = instruments
    }

    override suspend fun saveUserOrcid(orcid: String?) {
        if (orcid != null) {
            settings.putString("user_orcid", orcid)
        } else {
            settings.remove("user_orcid")
        }
        (userOrcid as? MutableStateFlow)?.value = orcid
    }

    override suspend fun addToHistory(uuid: String, name: String) {
        val existing = settings.getString("resource_history", "")
            .split(",")
            .mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size == 3) {
                    HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
                } else {
                    null
                }
            }

        val updated = listOf(HistoryItem(uuid, name, Clock.System.now().toEpochMilliseconds())) +
            existing.filter { it.uuid != uuid }

        val encoded = updated.take(20).joinToString(",") {
            "${it.uuid}|||${it.name}|||${it.timestamp}"
        }
        settings.putString("resource_history", encoded)
        (resourceHistory as? MutableStateFlow)?.value = updated.take(20)
    }

    override suspend fun clearHistory() {
        settings.remove("resource_history")
        (resourceHistory as? MutableStateFlow)?.value = emptyList()
    }

    override suspend fun saveSampleGroupBy(value: String) {
        settings.putString("sample_group_by", value)
        (sampleGroupBy as? MutableStateFlow)?.value = value
    }

    override suspend fun saveDatasetGroupBy(value: String) {
        settings.putString("dataset_group_by", value)
        (datasetGroupBy as? MutableStateFlow)?.value = value
    }

    override suspend fun saveDefaultProjectTab(tab: String) {
        settings.putString("default_project_tab", tab)
        (defaultProjectTab as? MutableStateFlow)?.value = tab
    }
}
