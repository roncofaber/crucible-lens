package crucible.lens.ui.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private fun LazyListScope.loadMoreItem(
    keyPrefix: String,
    groupKey: String,
    displayed: Int,
    total: Int,
    onLoadMore: () -> Unit
) {
    if (displayed < total) {
        val remaining = total - displayed
        item(key = "load_more_${keyPrefix}_$groupKey") {
            TextButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(
                    "Load ${minOf(50, remaining)} more… ($remaining remaining)",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Shared content for [SamplesList]/[DatasetsList]'s LazyColumn: empty state, optional grouping with
 * sticky headers + per-group pagination, and the flat (ungrouped) list — everything that doesn't
 * depend on whether [T] is a Sample or Dataset. Emits directly into the caller's LazyColumn.
 *
 * Deliberately NOT @Composable: a LazyColumn's `content: LazyListScope.() -> Unit` builder lambda
 * is not itself a composable slot (only the `item {}`/`stickyHeader {}` trailing lambdas are), so
 * any @Composable state (owner names, rememberSaveable expand/pagination maps, the grouped-items
 * computation) has to live in the calling @Composable function — see [SamplesList]/[DatasetsList],
 * which pass the already-resolved groupedItems/expandedGroups/displayedCounts in here.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun <T : CrucibleResource> LazyListScope.groupedResourceItems(
    items: List<T>,
    projectId: String,
    graphExplorerUrl: String,
    isGrouped: Boolean,
    groupedItems: List<Map.Entry<String, List<T>>>,
    resourceIcon: AppIconToken,
    resourceType: String,
    sortState: SortState,
    onItemClick: (String) -> Unit,
    expandedGroups: SnapshotStateMap<String, Boolean>,
    displayedCounts: SnapshotStateMap<String, Int>,
    cacheAgeMinutes: Long?
) {
    if (!isGrouped) {
        val sortedItems = items.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
        items(sortedItems, key = { it.uniqueId }) { resource ->
            ResourceRow(
                title = resource.name,
                subtitle = resource.uniqueId,
                uniqueId = resource.uniqueId,
                graphExplorerUrl = graphExplorerUrl,
                projectId = projectId,
                resourceType = resourceType,
                onClick = { onItemClick(resource.uniqueId) }
            )
        }
    } else groupedItems.forEach { (groupKey, itemsInGroup) ->
        val expanded = expandedGroups[groupKey] == true
        val sortedItems = itemsInGroup.applySortState(sortState, name = { name }, mfid = { uniqueId }, date = { creationTimeOrEmpty() })
        val displayedCount = displayedCounts[groupKey] ?: 50

        stickyHeader(key = "header_${resourceType}_$groupKey") {
            SectionHeader(
                title = groupKey,
                count = itemsInGroup.size,
                icon = resourceIcon,
                expanded = expanded,
                onToggle = { expandedGroups[groupKey] = !expanded }
            )
        }

        if (expanded) {
            items(sortedItems.take(displayedCount), key = { it.uniqueId }) { resource ->
                ResourceRow(
                    title = resource.name,
                    subtitle = resource.uniqueId,
                    uniqueId = resource.uniqueId,
                    graphExplorerUrl = graphExplorerUrl,
                    projectId = projectId,
                    resourceType = resourceType,
                    onClick = { onItemClick(resource.uniqueId) }
                )
            }
            loadMoreItem(resourceType, groupKey, displayedCount, sortedItems.size) {
                displayedCounts[groupKey] = displayedCount + 50
            }
        }
    }
    if (cacheAgeMinutes != null) {
        item(key = "cache_age_$resourceType") {
            Text(
                text = "Cached ${cacheAgeMinutes}m ago",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
    val groupedItems = remember(samples, groupBy, ownerNames.toMap()) {
        if (!isGrouped) emptyList()
        else samples.groupBy { sample -> when (groupBy) {
            SampleGroupBy.NONE  -> ""
            SampleGroupBy.TYPE  -> sample.sampleType ?: "No type"
            SampleGroupBy.DATE  -> dateGroupKey(sample.timestamp)
            SampleGroupBy.OWNER -> sample.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
        } }.entries.sortedBy { it.key.lowercase() }
    }
    val expandedGroups = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Boolean>() }
    val displayedCounts = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Int>() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                        emptyMessage = "This project has no samples.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                !ownerNamesReady -> item(key = "owner_loading_sample") {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    }
                }
                else -> groupedResourceItems(
                    items = samples,
                    projectId = projectId,
                    graphExplorerUrl = graphExplorerUrl,
                    isGrouped = isGrouped,
                    groupedItems = groupedItems,
                    resourceIcon = AppIcons.Sample,
                    resourceType = "sample",
                    sortState = sortState,
                    onItemClick = onSampleClick,
                    expandedGroups = expandedGroups,
                    displayedCounts = displayedCounts,
                    cacheAgeMinutes = if (fromCache) repository.projectDataAgeMinutes(projectId) ?: 0 else null
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
    val groupedItems = remember(datasets, groupBy, ownerNames.toMap()) {
        if (!isGrouped) emptyList()
        else datasets.groupBy { dataset -> when (groupBy) {
            DatasetGroupBy.NONE        -> ""
            DatasetGroupBy.MEASUREMENT -> dataset.measurement ?: "No measurement"
            DatasetGroupBy.INSTRUMENT  -> dataset.instrumentName ?: "No instrument"
            DatasetGroupBy.DATE        -> dateGroupKey(dataset.timestamp)
            DatasetGroupBy.FORMAT      -> dataset.dataFormat ?: "No format"
            DatasetGroupBy.SESSION     -> dataset.sessionName ?: "No session"
            DatasetGroupBy.OWNER       -> dataset.ownerOrcid?.let { ownerNames[it] ?: it } ?: "Unknown owner"
        } }.entries.sortedBy { it.key.lowercase() }
    }
    val expandedGroups = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Boolean>() }
    val displayedCounts = rememberSaveable(groupBy, saver = stateMapSaver()) { mutableStateMapOf<String, Int>() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                        emptyMessage = "This project has no datasets.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                !ownerNamesReady -> item(key = "owner_loading_dataset") {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    }
                }
                else -> groupedResourceItems(
                    items = datasets,
                    projectId = projectId,
                    graphExplorerUrl = graphExplorerUrl,
                    isGrouped = isGrouped,
                    groupedItems = groupedItems,
                    resourceIcon = AppIcons.Dataset,
                    resourceType = "dataset",
                    sortState = sortState,
                    onItemClick = onDatasetClick,
                    expandedGroups = expandedGroups,
                    displayedCounts = displayedCounts,
                    cacheAgeMinutes = if (fromCache) repository.projectDataAgeMinutes(projectId) ?: 0 else null
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
