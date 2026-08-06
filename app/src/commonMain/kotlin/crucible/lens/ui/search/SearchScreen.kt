@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.search

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.EffectsDefaultSpring
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.ResourceSearchResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.FilterSheet
import crucible.lens.ui.common.SearchFilters
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.EmptyListCard
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.ResourceRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun SearchScreen(
    apiKey: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    userOrcid: String? = null,
    graphExplorerUrl: String = ""
) {
    val apiClient = koinInject<ApiClient>()
    val repository = koinInject<CrucibleRepository>()
    var query by rememberSaveable { mutableStateOf("") }
    var showMineOnly by rememberSaveable { mutableStateOf(false) }
    // Off (default): project search results are limited to projects the user is already a
    // member of, matching /projects/search's old member-only scope. On: shows every project
    // matching the query, including ones the user isn't a member of — surfaced with a
    // "not a member" visual treatment and a way to request access instead of opening directly.
    var discoverAllProjects by rememberSaveable { mutableStateOf(false) }
    // Search mode: false = name search, true = metadata search
    var metadataMode by rememberSaveable { mutableStateOf(false) }
    val memberProjects by repository.observeProjects()
        .collectAsStateWithLifecycle(initialValue = null)
    val memberProjectIds = remember(memberProjects) { memberProjects?.map { it.projectId }?.toSet() }

    // Result sections start open — you searched to see results, not to be shown three closed
    // drawers. Saveable so collapsing a section survives opening a result and coming back.
    var projectsExpanded by rememberSaveable { mutableStateOf(true) }
    var samplesExpanded by rememberSaveable { mutableStateOf(true) }
    var datasetsExpanded by rememberSaveable { mutableStateOf(true) }

    var activeFilters by remember { mutableStateOf(SearchFilters()) }
    var isFilterLoading by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Both modes produce the same type — a unified list of ResourceSearchResult.
    // Name search maps Sample/Dataset/Project → ResourceSearchResult at the call site.
    // Metadata search returns ResourceSearchResult directly from the API.
    var nameResults by remember { mutableStateOf<List<ResourceSearchResult>>(emptyList()) }
    var isNameSearching by remember { mutableStateOf(false) }

    var metadataResults by remember { mutableStateOf<List<ResourceSearchResult>?>(null) }
    var isMetadataSearching by remember { mutableStateOf(false) }
    var metadataSearchError by remember { mutableStateOf<String?>(null) }
    var metadataRetryTrigger by remember { mutableIntStateOf(0) }

    var isFirstComposition by remember { mutableStateOf(true) }

    // Clear metadata results when query changes
    LaunchedEffect(query) { metadataResults = null }

    // Name search — fires in name mode
    LaunchedEffect(query, metadataMode, activeFilters.isActive, discoverAllProjects) {
        if (metadataMode) {
            nameResults = emptyList(); isNameSearching = false; return@LaunchedEffect
        }
        if (activeFilters.isActive || query.length < 3) {
            nameResults = emptyList(); isNameSearching = false; return@LaunchedEffect
        }
        if (!isFirstComposition) delay(350)
        isFirstComposition = false
        isNameSearching = true
        val q = query.trim()
        val samples = (apiClient.service.searchSamples(q) as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.projectId) }
            ?: emptyList()
        val datasets = (apiClient.service.searchDatasets(q) as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.projectId) }
            ?: emptyList()
        // /projects/search returns every matching project regardless of membership — when the
        // discover toggle is off, drop results the user isn't a member of, matching this
        // endpoint's old member-only scope. memberProjectIds == null (list not loaded yet)
        // means "don't filter" rather than "filter everything out".
        val allProjectMatches = (apiClient.service.searchProjects(q) as? ApiResult.Success)?.data
            ?: emptyList()
        val projects = (if (discoverAllProjects || memberProjectIds == null) allProjectMatches
                        else allProjectMatches.filter { it.projectId in memberProjectIds })
            .map { ResourceSearchResult(it.projectId, "project", it.title ?: it.projectId) }
        val combined = projects + samples + datasets
        nameResults = if (showMineOnly && userOrcid != null)
            combined.filter { it.ownerOrcid == userOrcid || it.resourceType == "project" }
        else combined
        isNameSearching = false
    }

    // Filter-based search — fires in name mode when filters are active
    LaunchedEffect(activeFilters) {
        if (!activeFilters.isActive || metadataMode) { return@LaunchedEffect }
        isFilterLoading = true
        try {
            val after = activeFilters.createdAfter.ifBlank { null }
            val before = activeFilters.createdBefore.ifBlank { null }
            val projectId = activeFilters.projectId.ifBlank { null }
            val ownerOrcid = activeFilters.ownerOrcid.ifBlank { null }
            val samples = (apiClient.service.getFilteredSamples(
                projectId = projectId,
                sampleType = activeFilters.sampleType.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data
                ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.projectId) }
                ?: emptyList()
            val datasets = (apiClient.service.getFilteredDatasets(
                projectId = projectId,
                measurement = activeFilters.measurement.ifBlank { null },
                instrumentName = activeFilters.instrumentName.ifBlank { null },
                dataFormat = activeFilters.dataFormat.ifBlank { null },
                sessionName = activeFilters.sessionName.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            ) as? ApiResult.Success)?.data
                ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.projectId) }
                ?: emptyList()
            nameResults = samples + datasets
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
        isFilterLoading = false
    }

    // Metadata search — fires in metadata mode
    LaunchedEffect(query, metadataMode, metadataRetryTrigger) {
        if (!metadataMode) {
            metadataResults = null; isMetadataSearching = false; metadataSearchError = null; return@LaunchedEffect
        }
        if (query.length < 3) { metadataResults = null; metadataSearchError = null; return@LaunchedEffect }
        if (!isFirstComposition) delay(350)
        isFirstComposition = false
        isMetadataSearching = true
        metadataSearchError = null
        try {
            when (val response = apiClient.service.searchScientificMetadata(query.trim())) {
                is ApiResult.Success -> metadataResults = response.data
                is ApiResult.Error -> { metadataResults = emptyList(); metadataSearchError = response.message }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            metadataResults = emptyList()
            metadataSearchError = e.message ?: "Metadata search failed"
        }
        isMetadataSearching = false
    }

    val filtersActive = activeFilters.isActive
    val textQuery = query.trim().lowercase()
    val searchActive = query.length >= 3 || filtersActive

    // Active results for current mode, with client-side text filter applied in filter mode
    val activeResults: List<ResourceSearchResult> = when {
        metadataMode -> metadataResults ?: emptyList()
        filtersActive && textQuery.length >= 3 ->
            nameResults.filter { it.name?.contains(textQuery, ignoreCase = true) == true }
        else -> nameResults
    }

    // Group by resource type for section headers
    val projectResults = activeResults.filter { it.resourceType == "project" }
    val sampleResults = activeResults.filter { it.resourceType == "sample" }
    val datasetResults = activeResults.filter { it.resourceType == "dataset" }

    val mfidCandidate = remember(query) {
        val q = query.trim()
        if (q.length >= 10 && q.all { c -> c.isLowerCase() || c.isDigit() }) q else null
    }

    val isLoading = if (metadataMode) isMetadataSearching else (isNameSearching || isFilterLoading)
    val hasResults = activeResults.isNotEmpty()
    val searchPending = when {
        metadataMode -> query.length >= 3 && !isMetadataSearching && metadataResults == null
        else -> !filtersActive && query.length >= 3 && !isNameSearching
    }
    val isSearchLoading = (isLoading || searchPending) && !hasResults && mfidCandidate == null

    val lazyListState = remember { LazyListState() }
    LaunchedEffect(isNameSearching, isMetadataSearching) {
        if (!isNameSearching && !isMetadataSearching) lazyListState.scrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        SearchBar(
            // M3 defaults this to surfaceContainerHigh. That role is meant for compact chrome; an
            // expanded SearchBar is effectively the whole screen, so any tint on it reads as a
            // coloured page rather than a raised surface — and it would fight the tinted section
            // headers inside it. Plain surface keeps the results on the same ground as every other
            // list in the app.
            colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .semantics { traversalIndex = 0f },
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = { if (!it) onBack() },
                    placeholder = { Text("Search the Crucible...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        IconButton(onClick = onBack) { AppIcon(AppIcons.Back) }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!metadataMode) {
                                BadgedBox(badge = {
                                    if (activeFilters.isActive) Badge { Text(activeFilters.activeCount.toString()) }
                                }) {
                                    IconButton(onClick = { showFilterSheet = true }) {
                                        AppIcon(AppIcons.Filter)
                                    }
                                }
                            }
                            IconButton(onClick = onHome) { AppIcon(AppIcons.Home) }
                        }
                    }
                )
            },
            expanded = true,
            onExpandedChange = { if (!it) onBack() }
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {

                    // ── Chips row ─────────────────────────────────────────────
                    val showChipsRow = searchActive || userOrcid != null || activeFilters.isActive
                    if (showChipsRow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!metadataMode && userOrcid != null) {
                                    FilterChip(
                                        selected = showMineOnly,
                                        onClick = { showMineOnly = !showMineOnly },
                                        label = { Text("Mine") },
                                        leadingIcon = if (showMineOnly) {
                                            { AppIcon(AppIcons.Check, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                                // Off (default): projects you're not a member of are excluded
                                // from results, matching the historical member-only search
                                // scope. On: search every project — results you're not a
                                // member of show a "Request to join" action instead of opening.
                                if (!metadataMode) {
                                    FilterChip(
                                        selected = discoverAllProjects,
                                        onClick = { discoverAllProjects = !discoverAllProjects },
                                        label = { Text("Discover") },
                                        leadingIcon = {
                                            AppIcon(
                                                if (discoverAllProjects) AppIcons.Check else AppIcons.Public,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                                if (!metadataMode && activeFilters.isActive) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { activeFilters = SearchFilters() },
                                        label = { Text("${activeFilters.activeCount} filter${if (activeFilters.activeCount > 1) "s" else ""} · Clear") },
                                        leadingIcon = { AppIcon(AppIcons.Filter, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                            if (searchActive) {
                                FilterChip(
                                    selected = metadataMode,
                                    onClick = { metadataMode = !metadataMode },
                                    label = { Text("Metadata") },
                                    leadingIcon = { AppIcon(AppIcons.SearchFilters, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    // ── Content area ──────────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when {
                            apiKey.isNullOrBlank() -> EmptyListCard(
                                resourceName = "API key",
                                defaultIcon = AppIcons.Key,
                                isFiltered = false,
                                emptyMessage = "Configure your API key in Settings to search"
                            )
                            query.isBlank() && !filtersActive -> EmptyListCard(
                                resourceName = "results",
                                defaultIcon = AppIcons.Search,
                                isFiltered = false,
                                emptyMessage = "Type at least 3 characters to search samples, datasets and projects"
                            )
                            isSearchLoading -> LoadingContent(title = "Searching")
                            metadataMode && metadataSearchError != null -> ErrorCard(
                                title = "Metadata search failed",
                                message = metadataSearchError ?: "",
                                onRetry = { metadataSearchError = null; metadataRetryTrigger++ }
                            )
                            // isFiltered = false despite this being the no-matches case:
                            // EmptyListCard's filtered branch hardcodes "No <x> match your search."
                            // and discards emptyMessage, which would throw away the mode-aware
                            // hint below. Passing SearchOff explicitly gives the same icon.
                            !hasResults && mfidCandidate == null -> EmptyListCard(
                                resourceName = "Results",
                                defaultIcon = AppIcons.SearchOff,
                                isFiltered = false,
                                emptyMessage = if (metadataMode)
                                    "No metadata matches found — try a different term"
                                else
                                    "No name matches found — try metadata search"
                            )
                            else -> LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                if (mfidCandidate != null) {
                                    item(key = "direct") {
                                        DirectLookupCard(mfidCandidate) { onResourceClick(it) }
                                        HorizontalDivider()
                                    }
                                }

                                // Projects (name mode only — metadata search doesn't return projects)
                                if (projectResults.isNotEmpty()) {
                                    item(key = "header_projects") { SectionHeader(title = "Projects", count = projectResults.size, icon = AppIcons.Project,
                                            expanded = projectsExpanded, onToggle = { projectsExpanded = !projectsExpanded }) }
                                    if (projectsExpanded) items(projectResults, key = { it.uniqueId }) { result ->
                                        // The muted text alone doesn't say *why* the row is
                                        // greyed, so the snippet line carries the reason.
                                        val isNonMember = memberProjectIds != null &&
                                            result.uniqueId !in memberProjectIds
                                        ResourceRow(
                                            title = result.name ?: result.uniqueId,
                                            subtitle = result.uniqueId,
                                            uniqueId = result.uniqueId,
                                            snippet = if (isNonMember) "Not a member" else null,
                                            muted = isNonMember
                                        ) { onProjectClick(result.uniqueId) }
                                    }
                                }

                                // Samples
                                if (sampleResults.isNotEmpty()) {
                                    item(key = "header_samples") {
                                        SectionHeader(title = "Samples", count = sampleResults.size, icon = AppIcons.Sample,
                                            expanded = samplesExpanded, onToggle = { samplesExpanded = !samplesExpanded })
                                    }
                                    if (samplesExpanded) items(sampleResults, key = { it.uniqueId }) { result ->
                                        val snippet = result.scientificMetadata
                                            ?.entries?.take(2)
                                            ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                                        // Searching spans every project, so "which project" is
                                        // the useful disambiguator; the mfid stays one tap away
                                        // via Copy ID. Metadata-mode results carry no project_id,
                                        // so those fall back to the mfid.
                                        ResourceRow(
                                            title = result.name ?: result.uniqueId,
                                            subtitle = result.projectId ?: result.uniqueId,
                                            subtitleMonospace = result.projectId == null,
                                            uniqueId = result.uniqueId,
                                            snippet = snippet,
                                            graphExplorerUrl = graphExplorerUrl,
                                            projectId = result.projectId,
                                            resourceType = result.resourceType ?: "sample"
                                        ) { onResourceClick(result.uniqueId) }
                                    }
                                }

                                // Datasets (includes untyped results from metadata search)
                                if (datasetResults.isNotEmpty()) {
                                    item(key = "header_datasets") {
                                        SectionHeader(title = "Datasets", count = datasetResults.size, icon = AppIcons.Dataset,
                                            expanded = datasetsExpanded, onToggle = { datasetsExpanded = !datasetsExpanded })
                                    }
                                    if (datasetsExpanded) items(datasetResults, key = { it.uniqueId }) { result ->
                                        val snippet = result.scientificMetadata
                                            ?.entries?.take(2)
                                            ?.joinToString(" · ") { (k, v) -> "$k: $v" }
                                        // Searching spans every project, so "which project" is
                                        // the useful disambiguator; the mfid stays one tap away
                                        // via Copy ID. Metadata-mode results carry no project_id,
                                        // so those fall back to the mfid.
                                        ResourceRow(
                                            title = result.name ?: result.uniqueId,
                                            subtitle = result.projectId ?: result.uniqueId,
                                            subtitleMonospace = result.projectId == null,
                                            uniqueId = result.uniqueId,
                                            snippet = snippet,
                                            graphExplorerUrl = graphExplorerUrl,
                                            projectId = result.projectId,
                                            resourceType = result.resourceType ?: "sample"
                                        ) { onResourceClick(result.uniqueId) }
                                    }
                                }
                            }
                        }
                    }
                }
                FilterLoadingBar(isFilterLoading)
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = activeFilters,
            onApply = { activeFilters = it },
            onDismiss = { showFilterSheet = false }
        )
    }
}


@Composable
private fun FilterLoadingBar(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = EffectsFastSpring),
        exit = fadeOut(animationSpec = EffectsDefaultSpring)
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DirectLookupCard(mfid: String, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onClick(mfid) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(AppIcons.OpenInNew, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Open resource directly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    mfid,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
