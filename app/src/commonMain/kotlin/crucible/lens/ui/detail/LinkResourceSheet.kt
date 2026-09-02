@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.IdText





import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp


import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.data.util.ResourceLinkDirection
import crucible.lens.ui.scanner.QRCodeScannerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LinkResourceSheet(
    resource: CrucibleResource,
    recentHistory: List<HistoryItem> = emptyList(),
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val viewModel: LinkResourceViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repository = koinInject<CrucibleRepository>()

    var direction by rememberSaveable { mutableStateOf(ResourceLinkDirection.THEY_ARE_CHILD) }
    var directionExpanded by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val input = state.input
    val selectedResource = (state.resolution as? LinkResolutionState.Resolved)?.resource
    val resolvedType = selectedResource?.let {
        when (it) {
            is Sample -> "sample"
            is Dataset -> "dataset"
        }
    }
    val searchResults = (state.search as? LinkSearchState.Results)?.resources.orEmpty()
    val isResolving = state.resolution is LinkResolutionState.Resolving
    val isSearchingNames = state.search is LinkSearchState.Searching
    val isLinking = state.submission is LinkSubmissionState.Submitting

    LaunchedEffect(resource.uniqueId) { viewModel.start(resource.uniqueId) }
    LaunchedEffect(state.submission) {
        if (state.submission is LinkSubmissionState.Submitted) {
            viewModel.reset()
            onLinked()
        }
    }

    val currentType = when (resource) {
        is Sample -> "sample"
        is Dataset -> "dataset"
    }

    val isSameType = resolvedType == currentType
    val projectNames = remember {
        repository.getCachedProjects()?.associate { it.projectId to (it.title ?: it.projectId) } ?: emptyMap<String, String>()
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isLinking) {
                viewModel.reset()
                onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Link Resource", style = MaterialTheme.typography.titleLarge)

                // ── Selected resource card ────────────────────────────────────
                if (selectedResource != null) {
                    val sel = selectedResource
                    val selType = resolvedType ?: ""
                    val selProjectId = when (sel) {
                        is Sample -> sel.projectId
                        is Dataset -> sel.projectId
                    }
                    val selProjectName = selProjectId?.let { projectNames[it] }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppIcon(
                                if (sel is Sample) AppIcons.Sample else AppIcons.Dataset,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sel.name, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                val sub = listOfNotNull(selProjectName, selType.replaceFirstChar { it.uppercase() }).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(
                                onClick = { viewModel.updateInput("", resource) },
                                enabled = !isLinking,
                                modifier = Modifier.size(32.dp)
                            ) {
                                AppIcon(AppIcons.ClearInput,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                // ── QR scanner inline ─────────────────────────────────────────
                if (scanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    ) {
                        QRCodeScannerView(
                            modifier = Modifier.fillMaxSize(),
                            onCodeScanned = { code ->
                                viewModel.updateInput(code, resource)
                                scanning = false
                            }
                        )
                        IconButton(
                            onClick = { scanning = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            AppIcon(AppIcons.ClearInput)
                        }
                    }
                }

                // ── Search / UUID input ───────────────────────────────────────
                if (selectedResource == null) OutlinedTextField(
                    value = input,
                    onValueChange = { viewModel.updateInput(it, resource) },
                    enabled = !isLinking,
                    label = { Text("Search by name or paste UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { AppIcon(AppIcons.Search) },
                    trailingIcon = {
                        when {
                            isResolving || isSearchingNames -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            input.isNotBlank() -> IconButton(onClick = { viewModel.updateInput("", resource) }) {
                                AppIcon(AppIcons.ClearInput)
                            }
                            else -> IconButton(onClick = {
                                scanning = true
                            }) {
                                AppIcon(AppIcons.ScanQr)
                            }
                        }
                    },
                    isError = state.resolution is LinkResolutionState.NotFound ||
                        state.resolution is LinkResolutionState.Error || state.search is LinkSearchState.Error,
                    supportingText = when (val resolution = state.resolution) {
                        is LinkResolutionState.Resolved -> {
                            { Text("Detected: ${resolvedType.orEmpty().replaceFirstChar { it.uppercase() }}") }
                        }
                        LinkResolutionState.NotFound -> ({ Text("Resource not found") })
                        is LinkResolutionState.Error -> ({ Text(resolution.message) })
                        else -> when (val search = state.search) {
                            is LinkSearchState.Error -> ({ Text(search.message) })
                            is LinkSearchState.Results -> search.warning?.let { warning -> ({ Text(warning) }) }
                            else -> null
                        }
                    }
                )

                val lookupCanRetry = state.search is LinkSearchState.Error ||
                    (state.search as? LinkSearchState.Results)?.warning != null ||
                    state.resolution is LinkResolutionState.Error
                if (lookupCanRetry) {
                    TextButton(
                        onClick = { viewModel.retryLookup(resource) },
                        enabled = !isSearchingNames && !isResolving
                    ) {
                        Text("Retry")
                    }
                }

                // ── Search results from project ───────────────────────────────
                if (searchResults.isNotEmpty() && selectedResource == null) {
                    Text("Results", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    searchResults.forEach { result ->
                        val resultType = when (result) {
                            is Sample -> "sample"
                            is Dataset -> "dataset"
                        }
                        val resultIcon = when (result) {
                            is Sample -> AppIcons.Sample
                            is Dataset -> AppIcons.Dataset
                        }
                        val resultProjectId = when (result) {
                            is Sample -> result.projectId
                            is Dataset -> result.projectId
                        }
                        val projectName = resultProjectId?.let { projectNames[it] }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.selectResource(result)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(resultIcon,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    // Project name and mfid need different styling (prose vs.
                                    // monospace ID, full vs. dimmed opacity) but must still
                                    // truncate together as one line, so they're spans of one
                                    // AnnotatedString rather than separate Texts in a Row - a
                                    // Row wouldn't ellipsize as a unit the way a single Text does.
                                    val idColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    val subtitle = buildAnnotatedString {
                                        if (projectName != null) {
                                            append(projectName)
                                            append(" · ")
                                        }
                                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = idColor)) {
                                            append(result.uniqueId)
                                        }
                                    }
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(resultType, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.search is LinkSearchState.Results && searchResults.isEmpty()) {
                    Text(
                        "No matching resources",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Direction selector (same-type only) ───────────────────────
                if (resolvedType != null && isSameType) {
                    ExposedDropdownMenuBox(
                        expanded = directionExpanded,
                        onExpandedChange = { if (!isLinking) directionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (direction == ResourceLinkDirection.THEY_ARE_PARENT)
                                "Linked $currentType is parent" else "This $currentType is parent",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship direction") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionExpanded) },
                            leadingIcon = { AppIcon(AppIcons.ResourceHierarchy) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = directionExpanded,
                            onDismissRequest = { directionExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Linked $currentType is parent") },
                                onClick = { direction = ResourceLinkDirection.THEY_ARE_PARENT; directionExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("This $currentType is parent") },
                                onClick = { direction = ResourceLinkDirection.THEY_ARE_CHILD; directionExpanded = false }
                            )
                        }
                    }
                }

                // ── Link summary (cross-type) ─────────────────────────────────
                if (resolvedType != null && !isSameType) {
                    val desc = buildLinkDescription(currentType, resolvedType)
                    if (desc != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                desc,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // ── Recent suggestions ────────────────────────────────────────
                if (searchResults.isEmpty()) {
                    val recent = remember(recentHistory) {
                        recentHistory.filter { it.uuid != resource.uniqueId }.take(3)
                    }
                    if (recent.isNotEmpty()) {
                        Text("Recent", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        recent.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.updateInput(item.uuid, resource)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(AppIcons.History,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        IdText(item.uuid)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Link button ───────────────────────────────────────────────
                (state.submission as? LinkSubmissionState.Error)?.let {
                    Text(
                        it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { viewModel.submit(resource, direction) },
                    enabled = resolvedType != null && !isLinking,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (isLinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        AppIcon(AppIcons.LinkResource, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Link")
                    }
                }
            }
        }
    }
}

private fun buildLinkDescription(currentType: String, targetType: String): String? = when {
    currentType == "sample" && targetType == "dataset" -> "This sample will be linked to the dataset"
    currentType == "dataset" && targetType == "sample" -> "The sample will be linked to this dataset"
    else -> null
}
