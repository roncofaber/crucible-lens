package crucible.lens.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.util.Log
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.data.util.fetchProjectData
import crucible.lens.data.util.matchesSearch
import crucible.lens.ui.scanner.QRCodeScannerView
import kotlinx.coroutines.launch

private const val TAG = "LinkResourceSheet"

private enum class Direction { THEY_ARE_PARENT, THEY_ARE_CHILD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkResourceSheet(
    resource: CrucibleResource,
    recentHistory: List<HistoryItem> = emptyList(),
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var input by remember { mutableStateOf("") }
    var resolvedType by remember { mutableStateOf<String?>(null) }
    var resolvedUuid by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var isLinking by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(Direction.THEY_ARE_CHILD) }
    var directionExpanded by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    val currentType = when (resource) {
        is Sample -> "sample"
        is Dataset -> "dataset"
    }

    // Load project resources for name search
    val projectId = when (resource) {
        is Sample -> resource.projectId
        is Dataset -> resource.projectId
    }
    var projectResources by remember { mutableStateOf<List<CrucibleResource>>(emptyList()) }
    LaunchedEffect(projectId) {
        if (projectId == null) return@LaunchedEffect
        try {
            val (samples, datasets) = fetchProjectData(projectId)
            projectResources = (samples + datasets).filter { it.uniqueId != resource.uniqueId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load project resources for $projectId", e)
        }
    }

    // Name/UUID search results from project pool
    val searchResults = remember(input, projectResources) {
        if (input.length < 3) emptyList()
        else {
            val q = input.trim()
            projectResources.filter { r -> r.matchesSearch(q) }.take(6)
        }
    }

    // UUID resolve for direct UUID input (no spaces, long enough)
    LaunchedEffect(input) {
        val trimmed = input.trim()
        if (trimmed.length < 10 || trimmed.contains(' ')) {
            if (resolvedUuid == null) resolvedType = null
            return@LaunchedEffect
        }
        isResolving = true
        resolvedType = try {
            val resp = ApiClient.service.getResourceType(trimmed)
            if (resp.isSuccessful) {
                resolvedUuid = trimmed
                resp.body()?.resolvedType?.lowercase()
            } else null
        } catch (_: Exception) { null }
        isResolving = false
    }

    val isSameType = resolvedType == currentType

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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

                // ── QR scanner inline ─────────────────────────────────────────
                if (scanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    ) {
                        QRCodeScannerView(
                            modifier = Modifier.fillMaxSize(),
                            onCodeScanned = { code ->
                                input = code
                                resolvedUuid = null
                                resolvedType = null
                                scanning = false
                            }
                        )
                        IconButton(
                            onClick = { scanning = false },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close scanner")
                        }
                    }
                }

                // ── Search / UUID input ───────────────────────────────────────
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        resolvedUuid = null
                        resolvedType = null
                    },
                    label = { Text("Search by name or paste UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            isResolving -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            input.isNotBlank() -> IconButton(onClick = { input = ""; resolvedUuid = null; resolvedType = null }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                            else -> IconButton(onClick = {
                                if (hasCameraPermission) scanning = true
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                            }
                        }
                    },
                    supportingText = resolvedType?.let {
                        { Text("Detected: ${it.replaceFirstChar { c -> c.uppercase() }}") }
                    }
                )

                // ── Search results from project ───────────────────────────────
                if (searchResults.isNotEmpty() && resolvedUuid == null) {
                    val projectNames = remember {
                        CacheManager.getProjects()?.associate { it.uniqueId to it.name } ?: emptyMap()
                    }
                    Text("Results", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    searchResults.forEach { result ->
                        val resultType = when (result) {
                            is Sample -> "sample"
                            is Dataset -> "dataset"
                        }
                        val resultIcon = when (result) {
                            is Sample -> Icons.Default.Science
                            is Dataset -> Icons.Default.Dataset
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
                                input = result.uniqueId
                                resolvedUuid = result.uniqueId
                                resolvedType = resultType
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(resultIcon, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    val subtitle = listOfNotNull(projectName, result.uniqueId).joinToString(" · ")
                                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1)
                                }
                                Text(resultType, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // ── Direction selector (same-type only) ───────────────────────
                if (resolvedType != null && isSameType) {
                    ExposedDropdownMenuBox(
                        expanded = directionExpanded,
                        onExpandedChange = { directionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (direction == Direction.THEY_ARE_PARENT)
                                "Linked $currentType is parent" else "This $currentType is parent",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship direction") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = directionExpanded) },
                            leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = directionExpanded,
                            onDismissRequest = { directionExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Linked $currentType is parent") },
                                onClick = { direction = Direction.THEY_ARE_PARENT; directionExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("This $currentType is parent") },
                                onClick = { direction = Direction.THEY_ARE_CHILD; directionExpanded = false }
                            )
                        }
                    }
                }

                // ── Link summary (cross-type) ─────────────────────────────────
                if (resolvedType != null && !isSameType) {
                    val desc = buildLinkDescription(currentType, resolvedType!!)
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
                                    input = item.uuid
                                    resolvedUuid = null
                                    resolvedType = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Text(item.uuid, style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Link button ───────────────────────────────────────────────
                Button(
                    onClick = {
                        scope.launch {
                            isLinking = true
                            try {
                                val targetUuid = (resolvedUuid ?: input).trim()
                                val targetType = resolvedType ?: run {
                                    snackbarHostState.showSnackbar("Could not resolve resource type")
                                    return@launch
                                }
                                val ok = performLink(resource, currentType, targetUuid, targetType, direction)
                                if (ok) {
                                    onLinked()
                                } else {
                                    snackbarHostState.showSnackbar("Link failed — check the ID and try again")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Network error: ${e.message}")
                            } finally {
                                isLinking = false
                            }
                        }
                    },
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
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Link")
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }
    }
}

private fun buildLinkDescription(currentType: String, targetType: String): String? = when {
    currentType == "sample" && targetType == "dataset" -> "This sample will be linked to the dataset"
    currentType == "dataset" && targetType == "sample" -> "The sample will be linked to this dataset"
    else -> null
}

private suspend fun performLink(
    current: CrucibleResource,
    currentType: String,
    targetUuid: String,
    targetType: String,
    direction: Direction
): Boolean {
    val api = ApiClient.service
    val resp = when {
        currentType == "sample" && targetType == "sample" -> {
            if (direction == Direction.THEY_ARE_PARENT)
                api.linkSamples(parentUuid = targetUuid, childUuid = current.uniqueId)
            else
                api.linkSamples(parentUuid = current.uniqueId, childUuid = targetUuid)
        }
        currentType == "dataset" && targetType == "dataset" -> {
            if (direction == Direction.THEY_ARE_PARENT)
                api.linkDatasets(parentUuid = targetUuid, childUuid = current.uniqueId)
            else
                api.linkDatasets(parentUuid = current.uniqueId, childUuid = targetUuid)
        }
        currentType == "sample" && targetType == "dataset" ->
            api.linkDatasetSample(datasetUuid = targetUuid, sampleUuid = current.uniqueId)
        currentType == "dataset" && targetType == "sample" ->
            api.linkDatasetSample(datasetUuid = current.uniqueId, sampleUuid = targetUuid)
        else -> return false
    }
    return resp.isSuccessful
}
