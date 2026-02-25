package gov.lbl.crucible.scanner.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Base64
import android.util.Log
import gov.lbl.crucible.scanner.data.model.CrucibleResource
import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.DatasetReference
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.model.SampleReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceDetailScreen(
    resource: CrucibleResource,
    thumbnails: List<String>,
    graphExplorerUrl: String,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (resource is Sample) "Sample" else "Dataset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        // Refresh button
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Share button
                        IconButton(
                            onClick = {
                            val projectId = when (resource) {
                                is Sample -> resource.projectId
                                is Dataset -> resource.projectId
                                else -> null
                            }

                            if (projectId != null) {
                                val resourceType = when (resource) {
                                    is Sample -> "sample-graph"
                                    is Dataset -> "dataset"
                                    else -> null
                                }

                                if (resourceType != null) {
                                    val url = "$graphExplorerUrl/$projectId/$resourceType/${resource.uniqueId}"
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Check out this ${if (resource is Sample) "sample" else "dataset"} in Crucible: $url")
                                        putExtra(Intent.EXTRA_SUBJECT, resource.name)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                                }
                            }
                        },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Open in Graph Explorer button
                        IconButton(
                            onClick = {
                            val projectId = when (resource) {
                                is Sample -> resource.projectId
                                is Dataset -> resource.projectId
                                else -> null
                            }

                            if (projectId != null) {
                                val resourceType = when (resource) {
                                    is Sample -> "sample-graph"
                                    is Dataset -> "dataset"
                                    else -> null
                                }

                                if (resourceType != null) {
                                    val url = "$graphExplorerUrl/$projectId/$resourceType/${resource.uniqueId}"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            }
                        },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = "Open in Graph Explorer",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Home button
                        IconButton(
                            onClick = onHome,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Card
            item {
                BasicInfoCard(resource)
            }

            // Thumbnails (if dataset)
            item {
                if (thumbnails.isNotEmpty()) {
                    ThumbnailsSection(thumbnails)
                } else if (resource is Dataset) {
                    Card {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "No images available for this dataset",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Type-specific content
            when (resource) {
                is Sample -> {
                    item {
                        SampleDetailsCard(resource)
                    }
                }
                is Dataset -> {
                    item {
                        DatasetDetailsCard(resource)
                    }
                }
            }

            // Relationship cards - Order: Linked samples, Parent datasets, Child datasets
            when (resource) {
                is Dataset -> {
                    // 1. Linked samples
                    if (!resource.samples.isNullOrEmpty()) {
                        item {
                            LinkedSamplesCard(
                                samples = resource.samples.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    // 2. Parent datasets
                    if (!resource.parentDatasets.isNullOrEmpty()) {
                        item {
                            ParentDatasetsCard(
                                parents = resource.parentDatasets.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    // 3. Child datasets
                    if (!resource.childDatasets.isNullOrEmpty()) {
                        item {
                            ChildDatasetsCard(
                                children = resource.childDatasets.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    // 4. Scientific metadata
                    if (!resource.scientificMetadata.isNullOrEmpty()) {
                        item {
                            ScientificMetadataCard(resource.scientificMetadata)
                        }
                    }
                    // 5. Keywords
                    if (!resource.keywords.isNullOrEmpty()) {
                        item {
                            KeywordsCard(resource.keywords ?: emptyList())
                        }
                    }
                }
                is Sample -> {
                    if (!resource.parentSamples.isNullOrEmpty()) {
                        item {
                            ParentSamplesCard(
                                parents = resource.parentSamples.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    if (!resource.childSamples.isNullOrEmpty()) {
                        item {
                            ChildSamplesCard(
                                children = resource.childSamples.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    if (!resource.datasets.isNullOrEmpty()) {
                        item {
                            LinkedDatasetsCard(
                                datasets = resource.datasets.orEmpty()
                                    .distinctBy { it.uniqueId }
                                    .sortedBy { it.uniqueId },
                                onNavigateToResource = onNavigateToResource
                            )
                        }
                    }
                    // Keywords for samples
                    if (!resource.keywords.isNullOrEmpty()) {
                        item {
                            KeywordsCard(resource.keywords ?: emptyList())
                        }
                    }
                }
                else -> {}
            }

            // MFID Card
            item {
                MfidCard(resource.uniqueId)
            }
        }
    }
}

@Composable
private fun ResourceTypeBadge(resource: CrucibleResource) {
    val (icon, label, color) = when (resource) {
        is Sample -> Triple(Icons.Default.Science, "Sample", MaterialTheme.colorScheme.primary)
        is Dataset -> Triple(Icons.Default.DataObject, "Dataset", MaterialTheme.colorScheme.secondary)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BasicInfoCard(resource: CrucibleResource) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = resource.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            val description = resource.description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThumbnailsSection(thumbnails: List<String>) {
    thumbnails.forEachIndexed { index, thumbnail ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            var imageState by remember { mutableStateOf<String?>(null) }

            // Extract base64 data from data URI (format: "data:image/png;base64,<base64_string>")
            val base64Data = remember(thumbnail) {
                try {
                    if (thumbnail.startsWith("data:image/")) {
                        val base64String = thumbnail.substringAfter("base64,")
                        Base64.decode(base64String, Base64.DEFAULT)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ResourceDetailScreen", "Failed to decode thumbnail ${index + 1}", e)
                    null
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                contentAlignment = Alignment.Center
            ) {
                if (base64Data != null) {
                    AsyncImage(
                        model = base64Data,
                        contentDescription = "Dataset image ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .padding(8.dp),
                        contentScale = ContentScale.Fit,
                        onLoading = {
                            imageState = "loading"
                        },
                        onSuccess = {
                            imageState = "success"
                        },
                        onError = {
                            imageState = "error: ${it.result.throwable?.message}"
                        }
                    )
                } else {
                    imageState = "error: Failed to decode base64"
                }

                // Show loading/error overlay
                when {
                    imageState == "loading" -> {
                        CircularProgressIndicator()
                    }
                    imageState?.startsWith("error") == true -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Failed to load image",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleDetailsCard(sample: Sample) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sample Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(icon = Icons.Default.Category, label = "Type", value = sample.sampleType ?: "None")
            InfoRow(icon = Icons.Default.Folder, label = "Project", value = sample.projectId ?: "None")
            InfoRow(icon = Icons.Default.CalendarToday, label = "Created", value = sample.createdAt ?: "None")
        }
    }
}

@Composable
private fun DatasetDetailsCard(dataset: Dataset) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dataset Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { advanced = !advanced },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        if (advanced) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (advanced) "Basic" else "Advanced")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Basic fields
            InfoRow(icon = Icons.Default.Science, label = "Measurement", value = dataset.measurement ?: "None")
            InfoRow(icon = Icons.Default.Build, label = "Instrument", value = dataset.instrumentName ?: "None")
            InfoRow(icon = Icons.Default.Folder, label = "Project", value = dataset.projectId ?: "None")
            InfoRow(icon = Icons.Default.CalendarToday, label = "Created", value = dataset.createdAt ?: "None")
            InfoRow(
                icon = when (dataset.isPublic) {
                    true  -> Icons.Default.Public
                    false -> Icons.Default.Lock
                    null  -> Icons.Default.HelpOutline
                },
                label = "Visibility",
                value = when (dataset.isPublic) {
                    true  -> "Public"
                    false -> "Private"
                    null  -> "None"
                }
            )

            // Advanced fields
            if (advanced) {
                InfoRow(icon = Icons.Default.Description, label = "Format", value = dataset.dataFormat ?: "None")
                InfoRow(icon = Icons.Default.Tag, label = "Instrument ID", value = dataset.instrumentId?.toString() ?: "None")
                InfoRow(icon = Icons.Default.PlayCircle, label = "Session", value = dataset.sessionName ?: "None")
                InfoRow(icon = Icons.Default.FolderOpen, label = "Source Folder", value = dataset.sourceFolder?.takeIf { it.isNotBlank() } ?: "None")
                InfoRow(icon = Icons.Default.AttachFile, label = "File", value = dataset.fileToUpload ?: "None")
                InfoRow(icon = Icons.Default.Storage, label = "Size", value = dataset.size?.let { formatFileSize(it) } ?: "None")
                if (dataset.jsonLink != null) {
                    ClickableInfoRow(
                        icon = Icons.Default.Link,
                        label = "JSON Link",
                        value = dataset.jsonLink,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dataset.jsonLink)))
                        }
                    )
                } else {
                    InfoRow(icon = Icons.Default.Link, label = "JSON Link", value = "None")
                }
                if (dataset.ownerOrcid != null) {
                    ClickableInfoRow(
                        icon = Icons.Default.Person,
                        label = "Owner ORCID",
                        value = dataset.ownerOrcid,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orcid.org/${dataset.ownerOrcid}")))
                        }
                    )
                } else {
                    InfoRow(icon = Icons.Default.Person, label = "Owner ORCID", value = "None")
                }
                InfoRow(icon = Icons.Default.AccountCircle, label = "Owner User ID", value = dataset.ownerUserId?.toString() ?: "None")
                InfoRow(icon = Icons.Default.Security, label = "SHA-256", value = dataset.sha256Hash ?: "None")
                InfoRow(icon = Icons.Default.Fingerprint, label = "Unique ID", value = dataset.uniqueId)
                InfoRow(icon = Icons.Default.Numbers, label = "Internal ID", value = dataset.internalId?.toString() ?: "None")
            }
        }
    }
}

@Composable
private fun ScientificMetadataCard(metadata: Map<String, Any?>) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scientific Metadata",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                MetadataTree(metadata, indentLevel = 0)
            }
        }
    }
}

@Composable
private fun MetadataTree(data: Map<String, Any?>, indentLevel: Int) {
    val entries = data.entries.toList()
    for ((index, entry) in entries.withIndex()) {
        val (entryKey, entryValue) = entry
        key(entryKey) {
            when (entryValue) {
                is Map<*, *> -> {
                    var expanded by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 0.dp, vertical = 4.dp)
                                .absolutePadding(left = (indentLevel * 16).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatKey(entryKey),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (expanded) {
                            @Suppress("UNCHECKED_CAST")
                            MetadataTree(entryValue as Map<String, Any?>, indentLevel + 1)
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 2.dp)
                            .absolutePadding(left = (indentLevel * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = formatKey(entryKey),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(0.35f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatValue(entryValue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
            }

            if (index < entries.size - 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .absolutePadding(left = (indentLevel * 16).dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatKey(key: String): String {
    return key.replace("_", " ")
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}

private fun formatValue(value: Any?): String {
    return when (value) {
        null -> "—"
        is Number -> {
            val num = value.toDouble()
            if (num == num.toLong().toDouble()) {
                num.toLong().toString()
            } else {
                "%.4f".format(num)
            }
        }
        is Boolean -> if (value) "Yes" else "No"
        is List<*> -> {
            if (value.isEmpty()) {
                "[]"
            } else if (value.all { it is Number || it is String || it is Boolean }) {
                value.joinToString(", ")
            } else {
                value.joinToString("\n") { formatValue(it) }
            }
        }
        is Map<*, *> -> {
            // This shouldn't be called for nested maps since we handle them separately
            // But just in case, format as JSON-like
            "{...}"
        }
        else -> value.toString()
    }
}

@Composable
private fun KeywordsCard(keywords: List<String>) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keywords",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    AssistChip(
                        onClick = { },
                        label = { Text(keyword) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentDatasetsCard(
    parents: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent Datasets (${parents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (parent in parents) {
                    ListItem(
                        headlineContent = {
                            Text(parent.datasetName ?: parent.uniqueId.take(13))
                        },
                        supportingContent = parent.measurement?.let { measurement ->
                            @Composable { Text(measurement) }
                        },
                        leadingContent = {
                            Icon(Icons.Default.DataObject, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(parent.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChildDatasetsCard(
    children: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Child Datasets (${children.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (child in children) {
                    ListItem(
                        headlineContent = {
                            Text(child.datasetName ?: child.uniqueId.take(13))
                        },
                        supportingContent = child.measurement?.let { measurement ->
                            @Composable { Text(measurement) }
                        },
                        leadingContent = {
                            Icon(Icons.Default.DataObject, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(child.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LinkedSamplesCard(
    samples: List<SampleReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Linked Samples (${samples.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (sample in samples) {
                    ListItem(
                        headlineContent = {
                            Text(sample.sampleName ?: sample.uniqueId.take(13))
                        },
                        leadingContent = {
                            Icon(Icons.Default.BubbleChart, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(sample.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LinkedDatasetsCard(
    datasets: List<DatasetReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Dataset, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Linked Datasets (${datasets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (dataset in datasets) {
                    ListItem(
                        headlineContent = {
                            Text(dataset.datasetName ?: dataset.uniqueId.take(13))
                        },
                        supportingContent = dataset.measurement?.let { measurement ->
                            @Composable { Text(measurement) }
                        },
                        leadingContent = {
                            Icon(Icons.Default.DataObject, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(dataset.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ParentSamplesCard(
    parents: List<SampleReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parent Samples (${parents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (parent in parents) {
                    ListItem(
                        headlineContent = {
                            Text(parent.sampleName ?: parent.uniqueId.take(13))
                        },
                        leadingContent = {
                            Icon(Icons.Default.BubbleChart, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(parent.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChildSamplesCard(
    children: List<SampleReference>,
    onNavigateToResource: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Child Samples (${children.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                for (child in children) {
                    ListItem(
                        headlineContent = {
                            Text(child.sampleName ?: child.uniqueId.take(13))
                        },
                        leadingContent = {
                            Icon(Icons.Default.BubbleChart, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Navigate")
                        },
                        modifier = Modifier.clickable {
                            onNavigateToResource(child.uniqueId)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MfidCard(mfid: String) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.clickable {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("MFID", mfid)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "MFID copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MFID",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mfid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.3f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )
    }
}

@Composable
private fun ClickableInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.3f)
        )
        Row(
            modifier = Modifier.weight(0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = "Open link",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
