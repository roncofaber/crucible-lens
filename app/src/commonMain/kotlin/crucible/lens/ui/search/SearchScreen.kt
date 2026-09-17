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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.ResourceSearchResult
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    val viewModel: SearchViewModel = koinViewModel()
    val searchState by viewModel.state.collectAsStateWithLifecycle()
    val repository = koinInject<CrucibleRepository>()
    val query = searchState.query
    var showMineOnly by rememberSaveable { mutableStateOf(false) }
    val metadataMode = searchState.metadataMode
    val memberProjects by repository.observeProjects()
        .collectAsStateWithLifecycle(initialValue = null)
    val memberProjectIds = remember(memberProjects) { memberProjects?.map { it.projectId }?.toSet() }

    // Result sections start open — you searched to see results, not to be shown three closed
    // drawers. Saveable so collapsing a section survives opening a result and coming back.
    var projectsExpanded by rememberSaveable { mutableStateOf(true) }
    var samplesExpanded by rememberSaveable { mutableStateOf(true) }
    var datasetsExpanded by rememberSaveable { mutableStateOf(true) }
    var usersExpanded by rememberSaveable { mutableStateOf(true) }

    val activeFilters = searchState.filters
    val isFilterLoading = searchState.isLoading && activeFilters.isActive && !metadataMode
    var showFilterSheet by remember { mutableStateOf(false) }

    val nameResults = searchState.resourceResults
    val userResults = searchState.userResults
    val metadataResults = if (metadataMode && searchState.hasSearched) searchState.resourceResults else null
    val isNameSearching = searchState.isLoading && !metadataMode && !activeFilters.isActive
    val isMetadataSearching = searchState.isLoading && metadataMode
    val metadataSearchError = searchState.error.takeIf { metadataMode }
    val nameSearchError = searchState.error.takeIf { !metadataMode }

    // Autofocus only the very first time this screen is entered - rememberSaveable so it
    // survives navigating to a result and back (the composable is fully recreated on return),
    // without re-popping the keyboard over the results the user is looking at.
    var hasAutoFocused by rememberSaveable { mutableStateOf(false) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!hasAutoFocused) {
            hasAutoFocused = true
            searchFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
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
    }.let { results ->
        if (!metadataMode && showMineOnly && userOrcid != null) {
            results.filter { it.ownerOrcid == userOrcid || it.resourceType == "project" }
        } else {
            results
        }
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

    val isLoading = searchState.isLoading
    val hasResults = activeResults.isNotEmpty() || userResults.isNotEmpty()
    val isSearchLoading = isLoading && !hasResults && mfidCandidate == null

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
                    onQueryChange = viewModel::updateQuery,
                    onSearch = {},
                    modifier = Modifier.focusRequester(searchFieldFocusRequester),
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
                                    IconButton(onClick = {
                                        viewModel.loadFacetSuggestions()
                                        showFilterSheet = true
                                    }) {
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
                                    onClick = { viewModel.setFilters(SearchFilters()) },
                                    label = { Text("${activeFilters.activeCount} filter${if (activeFilters.activeCount > 1) "s" else ""} · Clear") },
                                    leadingIcon = { AppIcon(AppIcons.Filter, modifier = Modifier.size(16.dp)) }
                                )
                            }
                            if (searchActive) {
                                FilterChip(
                                    selected = metadataMode,
                                    onClick = { viewModel.setMetadataMode(!metadataMode) },
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
                                message = metadataSearchError,
                                onRetry = viewModel::retry
                            )
                            !metadataMode && nameSearchError != null -> ErrorCard(
                                title = "Search failed",
                                message = nameSearchError,
                                onRetry = viewModel::retry
                            )
                            searchState.warning != null && !hasResults -> ErrorCard(
                                title = "Some results unavailable",
                                message = searchState.warning ?: "",
                                onRetry = viewModel::retry
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
                                searchState.warning?.let { warning ->
                                    item(key = "partial_error") {
                                        ErrorCard(
                                            title = "Some results unavailable",
                                            message = warning,
                                            onRetry = viewModel::retry,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
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
                                            subtitle = result.projectLabel ?: result.projectId ?: result.uniqueId,
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
                                            subtitle = result.projectLabel ?: result.projectId ?: result.uniqueId,
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
                FilterLoadingBar(isFilterLoading || (isLoading && hasResults))
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = activeFilters,
            suggestions = searchState.facetSuggestions,
            suggestionsError = searchState.facetError,
            onRetrySuggestions = { viewModel.loadFacetSuggestions(forceRefresh = true) },
            onApply = viewModel::setFilters,
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
