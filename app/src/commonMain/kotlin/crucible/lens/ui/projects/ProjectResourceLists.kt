package crucible.lens.ui.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.model.creationTimeOrEmpty
import crucible.lens.data.model.resolvedInstrumentName
import crucible.lens.data.model.resolvedProjectId
import crucible.lens.data.model.resolvedProjectName
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.SortState
import crucible.lens.data.util.applySortState
import crucible.lens.data.util.dateGroupKey
import crucible.lens.data.util.userDisplayName
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.EmptyListCard
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LazyColumnScrollbar
import crucible.lens.ui.common.LoadState
import crucible.lens.ui.common.LoadingItem
import crucible.lens.ui.common.ResourceRow
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.common.ScrollToTopButtonClearance
import crucible.lens.ui.common.SectionHeader
import crucible.lens.ui.common.stateMapSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal enum class SampleGroupBy(val label: String) {
    NONE("None"), TYPE("Type"), DATE("Date"), OWNER("Owner")
}

internal enum class DatasetGroupBy(val label: String) {
    NONE("None"), MEASUREMENT("Measurement"), INSTRUMENT("Instrument"),
    DATE("Date"), FORMAT("Format"), SESSION("Session"), OWNER("Owner")
}

@Composable
private fun rememberOwnerNames(
    isOwnerGroupBy: Boolean,
    projectId: String
): Pair<SnapshotStateMap<String, String>, Boolean> {
    val ownerNames = remember { mutableStateMapOf<String, String>() }
    var ownerNamesReady by remember(isOwnerGroupBy) {
        mutableStateOf(!isOwnerGroupBy || ownerNames.isNotEmpty())
    }
    // Shares CrucibleRepository's project-members cache with the collapsing top bar's member
    // count (both fetched via repository.fetchProjectMembers) instead of hitting
    // GET /projects/{id}/users a second time here.
    val repository = koinInject<CrucibleRepository>()
    LaunchedEffect(isOwnerGroupBy, projectId) {
        if (!isOwnerGroupBy || ownerNames.isNotEmpty()) { ownerNamesReady = true; return@LaunchedEffect }
        try {
            when (val resp = repository.fetchProjectMembers(projectId)) {
                is ApiResult.Success -> {
                    resp.data.forEach { u -> u.uniqueId?.let { id -> ownerNames[id] = userDisplayName(u) } }
                }
                is ApiResult.Error -> {
                    // Fail silently
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
        ownerNamesReady = true
    }
    return ownerNames to ownerNamesReady
}

/**
 * Shared content for [SamplesList]/[DatasetsList]'s LazyColumn: empty state, optional grouping with
 * sticky headers, and the flat (ungrouped) list — everything that doesn't depend on whether [T] is
 * a Sample or Dataset. Emits directly into the caller's LazyColumn.
 *
 * Every group renders in full rather than an incremental "Load more" slice: the data is already
 * entirely in memory by the time this runs (`CrucibleRepository.fetchProjectData` fetches and
 * caches the complete sample/dataset lists up front, there's no further network page to defer),
 * and `LazyColumn` only composes/measures items near the viewport regardless of how many are
 * registered — the flat/ungrouped branch below has always rendered its full list this way. Capping
 * a group's `items()` call bought nothing but an extra tap.
 *
 * Deliberately NOT @Composable: a LazyColumn's `content: LazyListScope.() -> Unit` builder lambda
 * is not itself a composable slot (only the `item {}`/`stickyHeader {}` trailing lambdas are), so
 * any @Composable state (owner names, rememberSaveable expand-state map, the grouping/sort
 * computation) has to live in the calling @Composable function — see [SamplesList]/[DatasetsList],
 * which pass the already-grouped-and-sorted `flatItems`/`groupedItems` and `expandedGroups` in here.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun <T : CrucibleResource> LazyListScope.groupedResourceItems(
    projectId: String,
    graphExplorerUrl: String,
    isGrouped: Boolean,
    flatItems: List<T>,
    groupedItems: List<Pair<String, List<T>>>,
    resourceIcon: AppIconToken,
    resourceType: String,
    onItemClick: (String) -> Unit,
    expandedGroups: SnapshotStateMap<String, Boolean>,
    cacheAgeMinutes: Long?,
    projectContextResourceIds: Set<String>
) {
    if (!isGrouped) {
        itemsIndexed(flatItems, key = { _, it -> it.uniqueId }) { index, resource ->
            val resourceProjectId = when (resource) {
                is Sample -> resource.resolvedProjectId
                is Dataset -> resource.resolvedProjectId
            }
            val resourceProjectName = when (resource) {
                is Sample -> resource.resolvedProjectName
                is Dataset -> resource.resolvedProjectName
            }
            ResourceRow(
                title = resource.name,
                subtitle = resource.uniqueId,
                uniqueId = resource.uniqueId,
                graphExplorerUrl = graphExplorerUrl,
                snippet = if (resource.uniqueId in projectContextResourceIds) resourceProjectName?.let { "Assigned to $it" } ?: "Not assigned to a project" else null,
                projectId = if (resource.uniqueId in projectContextResourceIds) resourceProjectId else projectId,
                resourceType = resourceType,
                showDivider = index > 0,
                onClick = { onItemClick(resource.uniqueId) }
            )
        }
    } else groupedItems.forEach { (groupKey, sortedItems) ->
        val expanded = expandedGroups[groupKey] == true

        stickyHeader(key = "header_${resourceType}_$groupKey") {
            SectionHeader(
                title = groupKey,
                count = sortedItems.size,
                icon = resourceIcon,
                expanded = expanded,
                onToggle = { expandedGroups[groupKey] = !expanded }
            )
        }

        if (expanded) {
            itemsIndexed(sortedItems, key = { _, it -> it.uniqueId }) { index, resource ->
                val resourceProjectId = when (resource) {
                    is Sample -> resource.resolvedProjectId
                    is Dataset -> resource.resolvedProjectId
                }
                val resourceProjectName = when (resource) {
                    is Sample -> resource.resolvedProjectName
                    is Dataset -> resource.resolvedProjectName
                }
                ResourceRow(
                    title = resource.name,
                    subtitle = resource.uniqueId,
                    uniqueId = resource.uniqueId,
                    graphExplorerUrl = graphExplorerUrl,
                    snippet = if (resource.uniqueId in projectContextResourceIds) resourceProjectName?.let { "Assigned to $it" } ?: "Not assigned to a project" else null,
                    projectId = if (resource.uniqueId in projectContextResourceIds) resourceProjectId else projectId,
                    resourceType = resourceType,
                    showDivider = index > 0,
                    onClick = { onItemClick(resource.uniqueId) }
                )
            }
        }
    }
    if (cacheAgeMinutes != null) {
        item(key = "cache_age_$resourceType") {
            Text(
                text = "Cached ${cacheAgeMinutes}m ago",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun SamplesList(
    samples: List<Sample>,
    isFiltered: Boolean,
    fromCache: Boolean = false,
    projectContextResourceIds: Set<String> = emptySet(),
    emptyMessage: String = "This project has no samples.",
    projectId: String = "",
    graphExplorerUrl: String = "",
    groupBy: SampleGroupBy = SampleGroupBy.TYPE,
    sortState: SortState = SortState(),
    onSampleClick: (String) -> Unit,
    loadState: LoadState<*>,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val repository = koinInject<CrucibleRepository>()
    val isGrouped = groupBy != SampleGroupBy.NONE
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == SampleGroupBy.OWNER, projectId)
    // Grouping only depends on data/groupBy/owner names, so a sort-order change alone doesn't
    // recompute it; sorting is a separate memo below keyed on sortState too, so toggling sort
    // alone doesn't redo the (more expensive) regrouping either.
    val groupedByKey = remember(samples, groupBy, ownerNames.toMap()) {
        if (!isGrouped) emptyList()
        else samples.groupBy { sample -> when (groupBy) {
            SampleGroupBy.NONE  -> ""
            SampleGroupBy.TYPE  -> sample.sampleType ?: "No type"
            SampleGroupBy.DATE  -> dateGroupKey(sample.timestamp)
            SampleGroupBy.OWNER -> sample.owner?.let(::userDisplayName) ?: sample.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
        } }.entries.sortedBy { it.key.lowercase() }
    }
    val sortedFlatItems = remember(samples, sortState) {
        samples.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
    }
    val groupedItems = remember(groupedByKey, sortState) {
        groupedByKey.map { (key, itemsInGroup) ->
            key to itemsInGroup.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
        }
    }
    val expandedGroups = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Boolean>() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Clears the ScrollToTopButton FAB so the last item is never obscured.
            contentPadding = PaddingValues(bottom = ScrollToTopButtonClearance)
        ) {
            (loadState as? LoadState.Success)?.refreshError?.let { message ->
                item(key = "refresh_error_sample") {
                    ErrorCard(title = "Could Not Refresh", message = message, modifier = Modifier.padding(16.dp), onRetry = onRetry)
                }
            }
            when {
                loadState is LoadState.Loading -> item(key = "loading") { LoadingItem(label = "Loading samples…") }
                loadState is LoadState.Error -> item(key = "error") {
                    ErrorCard(title = "Error Loading Data", message = loadState.message, modifier = Modifier.padding(16.dp), onRetry = onRetry)
                }
                samples.isEmpty() -> item(key = "empty_sample") {
                    EmptyListCard(
                        resourceName = "Samples",
                        defaultIcon = AppIcons.Sample,
                        isFiltered = isFiltered,
                        emptyMessage = emptyMessage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                !ownerNamesReady -> item(key = "owner_loading_sample") {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    }
                }
                else -> groupedResourceItems(
                    projectId = projectId,
                    graphExplorerUrl = graphExplorerUrl,
                    isGrouped = isGrouped,
                    flatItems = sortedFlatItems,
                    groupedItems = groupedItems,
                    resourceIcon = AppIcons.Sample,
                    resourceType = "sample",
                    onItemClick = onSampleClick,
                    expandedGroups = expandedGroups,
                    cacheAgeMinutes = if (fromCache) repository.projectDataAgeMinutes(projectId) ?: 0 else null,
                    projectContextResourceIds = projectContextResourceIds
                )
            }
        }
        LazyColumnScrollbar(listState = listState, modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
        ScrollToTopButton(
            visible = showScrollToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
internal fun DatasetsList(
    datasets: List<Dataset>,
    isFiltered: Boolean,
    fromCache: Boolean = false,
    projectContextResourceIds: Set<String> = emptySet(),
    emptyMessage: String = "This project has no datasets.",
    projectId: String = "",
    graphExplorerUrl: String = "",
    groupBy: DatasetGroupBy = DatasetGroupBy.MEASUREMENT,
    sortState: SortState = SortState(),
    onDatasetClick: (String) -> Unit,
    loadState: LoadState<*>,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val repository = koinInject<CrucibleRepository>()
    val isGrouped = groupBy != DatasetGroupBy.NONE
    val (ownerNames, ownerNamesReady) = rememberOwnerNames(groupBy == DatasetGroupBy.OWNER, projectId)
    // Grouping only depends on data/groupBy/owner names, so a sort-order change alone doesn't
    // recompute it; sorting is a separate memo below keyed on sortState too, so toggling sort
    // alone doesn't redo the (more expensive) regrouping either.
    val groupedByKey = remember(datasets, groupBy, ownerNames.toMap()) {
        if (!isGrouped) emptyList()
        else datasets.groupBy { dataset -> when (groupBy) {
            DatasetGroupBy.NONE        -> ""
            DatasetGroupBy.MEASUREMENT -> dataset.measurement ?: "No measurement"
            DatasetGroupBy.INSTRUMENT  -> dataset.resolvedInstrumentName ?: "No instrument"
            DatasetGroupBy.DATE        -> dateGroupKey(dataset.timestamp)
            DatasetGroupBy.FORMAT      -> dataset.dataFormat ?: "No format"
            DatasetGroupBy.SESSION     -> dataset.sessionName ?: "No session"
            DatasetGroupBy.OWNER       -> dataset.owner?.let(::userDisplayName) ?: dataset.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
        } }.entries.sortedBy { it.key.lowercase() }
    }
    val sortedFlatItems = remember(datasets, sortState) {
        datasets.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
    }
    val groupedItems = remember(groupedByKey, sortState) {
        groupedByKey.map { (key, itemsInGroup) ->
            key to itemsInGroup.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
        }
    }
    val expandedGroups = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Boolean>() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Clears the ScrollToTopButton FAB so the last item is never obscured.
            contentPadding = PaddingValues(bottom = ScrollToTopButtonClearance)
        ) {
            (loadState as? LoadState.Success)?.refreshError?.let { message ->
                item(key = "refresh_error_dataset") {
                    ErrorCard(title = "Could Not Refresh", message = message, modifier = Modifier.padding(16.dp), onRetry = onRetry)
                }
            }
            when {
                loadState is LoadState.Loading -> item(key = "loading") { LoadingItem(label = "Loading datasets…") }
                loadState is LoadState.Error -> item(key = "error") {
                    ErrorCard(title = "Error Loading Data", message = loadState.message, modifier = Modifier.padding(16.dp), onRetry = onRetry)
                }
                datasets.isEmpty() -> item(key = "empty_dataset") {
                    EmptyListCard(
                        resourceName = "Datasets",
                        defaultIcon = AppIcons.Dataset,
                        isFiltered = isFiltered,
                        emptyMessage = emptyMessage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                !ownerNamesReady -> item(key = "owner_loading_dataset") {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    }
                }
                else -> groupedResourceItems(
                    projectId = projectId,
                    graphExplorerUrl = graphExplorerUrl,
                    isGrouped = isGrouped,
                    flatItems = sortedFlatItems,
                    groupedItems = groupedItems,
                    resourceIcon = AppIcons.Dataset,
                    resourceType = "dataset",
                    onItemClick = onDatasetClick,
                    expandedGroups = expandedGroups,
                    cacheAgeMinutes = if (fromCache) repository.projectDataAgeMinutes(projectId) ?: 0 else null,
                    projectContextResourceIds = projectContextResourceIds
                )
            }
        }
        LazyColumnScrollbar(listState = listState, modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd))
        ScrollToTopButton(
            visible = showScrollToTop,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}
