package crucible.lens.ui.navigation

private fun encodeRouteSegment(s: String) =
    s.replace("%", "%25").replace("/", "%2F").replace("?", "%3F").replace("&", "%26").replace("=", "%3D").replace(" ", "%20")

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Settings : Screen("settings")
    object SettingsApi : Screen("settings/api")
    object SettingsAi : Screen("settings/ai")
    object SettingsAppearance : Screen("settings/appearance")
    object SettingsCache : Screen("settings/cache")
    object SettingsAbout : Screen("settings/about")
    object SettingsAccount : Screen("settings/account")
    object Projects : Screen("projects")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: String) = "project/$projectId"
    }
    object Detail : Screen("detail/{mfid}?groupBy={groupBy}") {
        fun createRoute(mfid: String, groupBy: String? = null) =
            "detail/${encodeRouteSegment(mfid)}?groupBy=${groupBy ?: ""}"
    }
    object History : Screen("history")
    object Search : Screen("search")
    object OrcidLogin : Screen("orcid-login")
    object CreateSample : Screen("create-sample?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId != null) "create-sample?projectId=${encodeRouteSegment(projectId)}" else "create-sample?projectId="
    }
    object CreateDataset : Screen("create-dataset?projectId={projectId}") {
        fun createRoute(projectId: String? = null) =
            if (projectId != null) "create-dataset?projectId=${encodeRouteSegment(projectId)}" else "create-dataset?projectId="
    }
    object Instruments : Screen("instruments")
    object InstrumentDetail : Screen("instrument/{instrumentId}") {
        fun createRoute(id: String) = "instrument/${encodeRouteSegment(id)}"
    }
    object MetadataEditor : Screen("metadata-editor")
    object AddFiles : Screen("add-files?datasetId={datasetId}") {
        fun createRoute(datasetId: String? = null) =
            if (datasetId != null) "add-files?datasetId=$datasetId" else "add-files?datasetId="
    }
}
