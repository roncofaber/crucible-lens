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
import crucible.lens.data.api.ApiClient
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.preferences.HistoryItem
import crucible.lens.ui.scanner.QRCodeScannerView
import kotlinx.coroutines.launch

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

    var uuidInput by remember { mutableStateOf("") }
    var resolvedType by remember { mutableStateOf<String?>(null) }
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
    val isSameType = resolvedType == currentType

    // Resolve target type whenever UUID changes to a plausible length
    LaunchedEffect(uuidInput) {
        if (uuidInput.length < 10) { resolvedType = null; return@LaunchedEffect }
        isResolving = true
        resolvedType = try {
            val resp = ApiClient.service.getResourceType(uuidInput.trim())
            if (resp.isSuccessful) resp.body()?.resolvedType?.lowercase() else null
        } catch (_: Exception) { null }
        isResolving = false
    }

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
                                uuidInput = code
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

                // ── UUID input ────────────────────────────────────────────────
                OutlinedTextField(
                    value = uuidInput,
                    onValueChange = { uuidInput = it },
                    label = { Text("Resource ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    trailingIcon = {
                        if (isResolving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (uuidInput.isNotBlank()) {
                            IconButton(onClick = { uuidInput = ""; resolvedType = null }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    supportingText = resolvedType?.let {
                        { Text("Detected: ${it.replaceFirstChar { c -> c.uppercase() }}") }
                    }
                )

                // ── Scan button ───────────────────────────────────────────────
                if (!scanning) {
                    OutlinedButton(
                        onClick = {
                            if (hasCameraPermission) scanning = true
                            else permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code")
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
                                "They are my parent" else "They are my child",
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
                                text = { Text("They are my parent") },
                                onClick = { direction = Direction.THEY_ARE_PARENT; directionExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("They are my child") },
                                onClick = { direction = Direction.THEY_ARE_CHILD; directionExpanded = false }
                            )
                        }
                    }
                }

                // ── Link summary ──────────────────────────────────────────────
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
                                uuidInput = item.uuid
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
                                    Text(item.name, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1)
                                    Text(item.uuid, style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1)
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
                                val targetUuid = uuidInput.trim()
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
                    enabled = uuidInput.isNotBlank() && resolvedType != null && !isLinking,
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
