@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.search

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.SkeletonRow
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
import crucible.lens.data.model.User
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.FilterSheet
import crucible.lens.ui.common.SearchFilters
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.EmptyListCard
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.ResourceRow
import crucible.lens.ui.common.UserResultItem
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private enum class SearchResultCategory(val label: String, val icon: AppIconToken) {
    People("People", AppIcons.User),
    Projects("Projects", AppIcons.Project),
    Samples("Samples", AppIcons.Sample),
    Datasets("Datasets", AppIcons.Dataset)
}

@Composable
fun SearchScreen(
    apiKey: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onResourceClick: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    userOrcid: String? = null,
    graphExplorerUrl: String = ""
) {
    val apiClient = koinInject<ApiClient>()
    val repository = koinInject<CrucibleRepository>()
    val prefs = koinInject<AppPreferences>()
    val peopleResultLimit by prefs.peopleResultLimit.collectAsStateWithLifecycle()
    val projectResultLimit by prefs.projectResultLimit.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showMineOnly by rememberSaveable { mutableStateOf(false) }
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
    var usersExpanded by rememberSaveable { mutableStateOf(true) }

    var activeFilters by remember { mutableStateOf(SearchFilters()) }
    var isFilterLoading by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Both modes produce the same type — a unified list of ResourceSearchResult.
    // Name search maps Sample/Dataset/Project → ResourceSearchResult at the call site.
    // Metadata search returns ResourceSearchResult directly from the API.
    var nameResults by remember { mutableStateOf<List<ResourceSearchResult>>(emptyList()) }
    var isNameSearching by remember { mutableStateOf(false) }
    // People are a separate result type (User, not ResourceSearchResult) so they're tracked in
    // their own list rather than folded into the unified samples/datasets/projects one.
    var userResults by remember { mutableStateOf<List<User>>(emptyList()) }

    var metadataResults by remember { mutableStateOf<List<ResourceSearchResult>?>(null) }
    var isMetadataSearching by remember { mutableStateOf(false) }
    var metadataSearchError by remember { mutableStateOf<String?>(null) }
    var metadataRetryTrigger by remember { mutableIntStateOf(0) }

    // Name/filter search error - both silently degraded any API failure into "zero results"
    // before, indistinguishable from a genuine no-match query. Only surfaced as a hard error when
    // every category failed (e.g. actually offline); one category hiccuping while the others
    // succeed still yields a useful result set, so that stays a silent per-category empty.
    var nameSearchError by remember { mutableStateOf<String?>(null) }
    var nameSearchRetryTrigger by remember { mutableIntStateOf(0) }
    // The query the currently-displayed nameResults/userResults actually correspond to (or null
    // if none yet) - lets searchPending below tell "haven't searched this query yet" apart from
    // "searched it, got zero results", which query.length>=3 && !isNameSearching alone can't.
    var lastSearchedQuery by remember { mutableStateOf<String?>(null) }

    var isFirstComposition by remember { mutableStateOf(true) }

    // Clear metadata results when query changes
    LaunchedEffect(query) { metadataResults = null }

    // Name search — fires in name mode
    LaunchedEffect(query, metadataMode, activeFilters.isActive, peopleResultLimit, projectResultLimit, nameSearchRetryTrigger) {
        if (metadataMode) {
            nameResults = emptyList(); isNameSearching = false; userResults = emptyList(); return@LaunchedEffect
        }
        if (activeFilters.isActive || query.length < 3) {
            nameResults = emptyList(); isNameSearching = false; userResults = emptyList(); nameSearchError = null; return@LaunchedEffect
        }
        if (!isFirstComposition) delay(350)
        isFirstComposition = false
        isNameSearching = true
        nameSearchError = null
        val q = query.trim()
        val samplesResult = apiClient.service.searchSamples(q)
        val datasetsResult = apiClient.service.searchDatasets(q)
        val projectsResult = apiClient.service.searchProjects(q, limit = projectResultLimit)
        val usersResult = apiClient.service.searchUsers(q, limit = peopleResultLimit)

        if (samplesResult is ApiResult.Error && datasetsResult is ApiResult.Error &&
            projectsResult is ApiResult.Error && usersResult is ApiResult.Error
        ) {
            nameSearchError = samplesResult.message
            nameResults = emptyList()
            userResults = emptyList()
            lastSearchedQuery = q
            isNameSearching = false
            return@LaunchedEffect
        }

        val samples = (samplesResult as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.projectId) }
            ?: emptyList()
        val datasets = (datasetsResult as? ApiResult.Success)?.data
            ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.projectId) }
            ?: emptyList()
        // /projects/search returns every matching project regardless of membership - shown
        // as-is, with non-member projects flagged via memberProjectIds below rather than hidden.
        val allProjectMatches = (projectsResult as? ApiResult.Success)?.data ?: emptyList()
        val projects = allProjectMatches.map { ResourceSearchResult(it.projectId, "project", it.title ?: it.projectId) }
        val combined = projects + samples + datasets
        nameResults = if (showMineOnly && userOrcid != null)
            combined.filter { it.ownerOrcid == userOrcid || it.resourceType == "project" }
        else combined
        // People aren't scoped by "Mine" - that's about resource ownership, which doesn't apply
        // to a person search.
        // People and Projects are each capped independently (Settings > Search, default tighter
        // than the API's own default of 20) - neither is "the main event" here, and a long list
        // of either would crowd out samples/datasets for a query that happens to also match them.
        // UserResultItem renders a username-less account too (falling back to their ORCID), so
        // no filtering needed here for the header count to match what's shown.
        userResults = (usersResult as? ApiResult.Success)?.data ?: emptyList()
        lastSearchedQuery = q
        isNameSearching = false
    }

    // Filter-based search — fires in name mode when filters are active
    LaunchedEffect(activeFilters, nameSearchRetryTrigger) {
        if (!activeFilters.isActive || metadataMode) { return@LaunchedEffect }
        isFilterLoading = true
        nameSearchError = null
        try {
            val after = activeFilters.createdAfter.ifBlank { null }
            val before = activeFilters.createdBefore.ifBlank { null }
            val projectId = activeFilters.projectId.ifBlank { null }
            val ownerOrcid = activeFilters.ownerOrcid.ifBlank { null }
            val samplesResult = apiClient.service.getFilteredSamples(
                projectId = projectId,
                sampleType = activeFilters.sampleType.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            )
            val datasetsResult = apiClient.service.getFilteredDatasets(
                projectId = projectId,
                measurement = activeFilters.measurement.ifBlank { null },
                instrumentName = activeFilters.instrumentName.ifBlank { null },
                dataFormat = activeFilters.dataFormat.ifBlank { null },
                sessionName = activeFilters.sessionName.ifBlank { null },
                ownerOrcid = ownerOrcid,
                creationTimeGte = after,
                creationTimeLte = before
            )
            if (samplesResult is ApiResult.Error && datasetsResult is ApiResult.Error) {
                nameSearchError = samplesResult.message
                nameResults = emptyList()
            } else {
                val samples = (samplesResult as? ApiResult.Success)?.data
                    ?.map { ResourceSearchResult(it.uniqueId, "sample", it.name, it.ownerOrcid, projectId = it.projectId) }
                    ?: emptyList()
                val datasets = (datasetsResult as? ApiResult.Success)?.data
                    ?.map { ResourceSearchResult(it.uniqueId, "dataset", it.name, it.ownerOrcid, projectId = it.projectId) }
                    ?: emptyList()
                nameResults = samples + datasets
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            nameSearchError = e.message ?: "Search failed"
        }
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

    // Quick category filter chips - narrows the result list to one of
    // People/Projects/Samples/Datasets, single-select like Instagram's Top/Accounts/Tags tabs or
    // Google's All/Images/News row: null means "show everything" (the default, like Top/All),
    // tapping a chip isolates that category, tapping it again returns to null.
    var selectedCategory by remember { mutableStateOf<SearchResultCategory?>(null) }
    val availableCategories = remember(userResults, projectResults, sampleResults, datasetResults) {
        buildList {
            if (userResults.isNotEmpty()) add(SearchResultCategory.People)
            if (projectResults.isNotEmpty()) add(SearchResultCategory.Projects)
            if (sampleResults.isNotEmpty()) add(SearchResultCategory.Samples)
            if (datasetResults.isNotEmpty()) add(SearchResultCategory.Datasets)
        }
    }
    // Keeps a selected chip visible/toggleable even if its category has no matches for the
    // current query, so a stale filter can still be cleared instead of just vanishing.
    val chipCategories = SearchResultCategory.entries.filter { it in availableCategories || it == selectedCategory }
    val visibleUserResults = if (selectedCategory == null || selectedCategory == SearchResultCategory.People) userResults else emptyList()
    val visibleProjectResults = if (selectedCategory == null || selectedCategory == SearchResultCategory.Projects) projectResults else emptyList()
    val visibleSampleResults = if (selectedCategory == null || selectedCategory == SearchResultCategory.Samples) sampleResults else emptyList()
    val visibleDatasetResults = if (selectedCategory == null || selectedCategory == SearchResultCategory.Datasets) datasetResults else emptyList()
    val hasVisibleResults = visibleUserResults.isNotEmpty() || visibleProjectResults.isNotEmpty() ||
        visibleSampleResults.isNotEmpty() || visibleDatasetResults.isNotEmpty()

    val mfidCandidate = remember(query) {
        val q = query.trim()
        if (q.length >= 10 && q.all { c -> c.isLowerCase() || c.isDigit() }) q else null
    }

    val isLoading = if (metadataMode) isMetadataSearching else (isNameSearching || isFilterLoading)
    val hasResults = activeResults.isNotEmpty() || userResults.isNotEmpty()
    val searchPending = when {
        metadataMode -> query.length >= 3 && !isMetadataSearching && metadataResults == null
        // query.trim() != lastSearchedQuery (not just !isNameSearching) - otherwise a genuinely
        // empty-result search looks identical to "haven't searched this query yet" once it
        // finishes, and isSearchLoading below (which requires !hasResults) would show the loading
        // skeleton forever instead of ever reaching the "no results"/error branches.
        else -> !filtersActive && query.length >= 3 && !isNameSearching && query.trim() != lastSearchedQuery
    }
    val isSearchLoading = (isLoading || searchPending) && !hasResults && mfidCandidate == null

    val lazyListState = remember { LazyListState() }
    LaunchedEffect(isNameSearching, isMetadataSearching) {
        if (!isNameSearching && !isMetadataSearching) lazyListState.scrollToItem(0)
    }

    Box(modifier = modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        SearchBar(
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
                // Recedes below the SearchBar panel's own surfaceContainerHigh backdrop (nested
                // surfaces recede, they don't share a tier - same principle as LinkedResourceCards'
                // ResourceRow) - otherwise this content area IS surfaceContainerHigh, the same
                // role SectionHeader uses for its "expanded" tint, so an expanded group header
                // vanishes into the page instead of standing out, and collapsed (plain `surface`)
                // reads as lighter than its surroundings instead of blending in.
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {

                    // ── Chips row ─────────────────────────────────────────────
                    // One scrollable row: "Mine" is an independent toggle (can be combined with
                    // a category), the People/Projects/Samples/Datasets chips are single-select
                    // (pick a lens, like Instagram's Top/Accounts/Tags or Google's All/Images/
                    // News row), and "Metadata" switches search mode entirely.
                    val showChipsRow = searchActive || userOrcid != null || activeFilters.isActive
                    if (showChipsRow) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            if (!metadataMode && chipCategories.size > 1) {
                                chipCategories.forEach { category ->
                                    val selected = category == selectedCategory
                                    FilterChip(
                                        selected = selected,
                                        onClick = { selectedCategory = if (selected) null else category },
                                        label = { Text(category.label) },
                                        leadingIcon = {
                                            AppIcon(
                                                if (selected) AppIcons.Check else category.icon,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                            if (!metadataMode && activeFilters.isActive) {
                                FilterChip(
                                    selected = true,
                                    onClick = { activeFilters = SearchFilters() },
                                    label = { Text("${activeFilters.activeCount} filter${if (activeFilters.activeCount > 1) "s" else ""} · Clear") },
                                    leadingIcon = { AppIcon(AppIcons.Filter, modifier = Modifier.size(16.dp)) }
                                )
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
                                emptyMessage = "Type at least 3 characters to search samples, datasets, projects, and people"
                            )
                            isSearchLoading -> Column(modifier = Modifier.fillMaxSize()) {
                                // First row previews People's avatar-leading shape - it renders
                                // first when present, unlike the icon-less ResourceRow sections.
                                SkeletonRow(hasLeadingIcon = true, hasSupportingLine = true)
                                repeat(5) { SkeletonRow(hasLeadingIcon = false, hasSupportingLine = true) }
                            }
                            metadataMode && metadataSearchError != null -> ErrorCard(
                                title = "Metadata search failed",
                                message = metadataSearchError ?: "",
                                onRetry = { metadataSearchError = null; metadataRetryTrigger++ }
                            )
                            !metadataMode && nameSearchError != null -> ErrorCard(
                                title = "Search failed",
                                message = nameSearchError ?: "",
                                onRetry = { nameSearchError = null; nameSearchRetryTrigger++ }
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
                            selectedCategory != null && !hasVisibleResults && mfidCandidate == null -> EmptyListCard(
                                resourceName = "Results",
                                defaultIcon = AppIcons.SearchOff,
                                isFiltered = false,
                                emptyMessage = "No ${selectedCategory?.label} match this search"
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

                                // People (name mode only - metadata search is scientific-metadata
                                // content, which users have none of). Shown first: finding a
                                // person is a different kind of search than browsing scientific
                                // data, and shouldn't be buried under three other sections.
                                if (visibleUserResults.isNotEmpty()) {
                                    item(key = "header_users") {
                                        SectionHeader(title = "People", count = visibleUserResults.size, icon = AppIcons.User,
                                            expanded = usersExpanded, onToggle = { usersExpanded = !usersExpanded })
                                    }
                                    if (usersExpanded) itemsIndexed(visibleUserResults, key = { _, it -> it.username ?: it.uniqueId ?: it.hashCode().toString() }) { _, result ->
                                        // UserResultItem is a tonal Surface, not a flat row - it's
                                        // separated by whitespace here, matching how it's already
                                        // spaced in its native home (SearchPickerSheet's
                                        // Arrangement.spacedBy(8.dp)), not a HorizontalDivider
                                        // (which the flat ResourceRow sections above use instead).
                                        // Username preferred, ORCID fallback - same identifier
                                        // preference UserProfileScreen's route already resolves
                                        // either way, and every other onUserClick call site uses.
                                        UserResultItem(
                                            user = result,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            onClick = { (result.username ?: result.uniqueId)?.let { onUserClick(it) } }
                                        )
                                    }
                                }

                                // Projects (name mode only — metadata search doesn't return projects)
                                if (visibleProjectResults.isNotEmpty()) {
                                    item(key = "header_projects") { SectionHeader(title = "Projects", count = visibleProjectResults.size, icon = AppIcons.Project,
                                            expanded = projectsExpanded, onToggle = { projectsExpanded = !projectsExpanded }) }
                                    if (projectsExpanded) itemsIndexed(visibleProjectResults, key = { _, it -> it.uniqueId }) { index, result ->
                                        // The muted text alone doesn't say *why* the row is
                                        // greyed, so the snippet line carries the reason.
                                        val isNonMember = memberProjectIds != null &&
                                            result.uniqueId !in memberProjectIds
                                        ResourceRow(
                                            title = result.name ?: result.uniqueId,
                                            subtitle = result.uniqueId,
                                            uniqueId = result.uniqueId,
                                            snippet = if (isNonMember) "Not a member" else null,
                                            muted = isNonMember,
                                            showDivider = index > 0
                                        ) { onProjectClick(result.uniqueId) }
                                    }
                                }

                                // Samples
                                if (visibleSampleResults.isNotEmpty()) {
                                    item(key = "header_samples") {
                                        SectionHeader(title = "Samples", count = visibleSampleResults.size, icon = AppIcons.Sample,
                                            expanded = samplesExpanded, onToggle = { samplesExpanded = !samplesExpanded })
                                    }
                                    if (samplesExpanded) itemsIndexed(visibleSampleResults, key = { _, it -> it.uniqueId }) { index, result ->
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
                                            resourceType = result.resourceType ?: "sample",
                                            showDivider = index > 0
                                        ) { onResourceClick(result.uniqueId) }
                                    }
                                }

                                // Datasets (includes untyped results from metadata search)
                                if (visibleDatasetResults.isNotEmpty()) {
                                    item(key = "header_datasets") {
                                        SectionHeader(title = "Datasets", count = visibleDatasetResults.size, icon = AppIcons.Dataset,
                                            expanded = datasetsExpanded, onToggle = { datasetsExpanded = !datasetsExpanded })
                                    }
                                    if (datasetsExpanded) itemsIndexed(visibleDatasetResults, key = { _, it -> it.uniqueId }) { index, result ->
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
                                            resourceType = result.resourceType ?: "sample",
                                            showDivider = index > 0
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(AppIcons.OpenInNew, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Open resource directly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    mfid,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
