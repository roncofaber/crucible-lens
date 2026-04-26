package crucible.lens.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import crucible.lens.BuildConfig
import crucible.lens.R
import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.cache.PersistentProjectCache
import crucible.lens.data.model.Project
import crucible.lens.ui.common.OfflineBanner
import crucible.lens.ui.common.allLoadingMessages
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    graphExplorerUrl: String,
    isDarkTheme: Boolean,
    lastVisitedResource: String?,
    lastVisitedResourceName: String?,
    apiKey: String?,
    onScanClick: () -> Unit,
    onManualEntry: (String) -> Unit,
    onBrowseProjects: () -> Unit,
    onBrowseInstruments: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onHistory: () -> Unit = {},
    onSearch: () -> Unit = {},
    pinnedProjects: Set<String> = emptySet(),
    onProjectClick: (String) -> Unit = {},
    onCreateSample: () -> Unit = {},
    onCreateDataset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var clickCount by remember { mutableStateOf(0) }
    val context = LocalContext.current

    var allProjects by remember { mutableStateOf(CacheManager.getProjects() ?: emptyList()) }

    LaunchedEffect(Unit) {
        val persistentData = PersistentProjectCache.loadProjectData(context)
        if (persistentData != null && allProjects.isEmpty()) {
            allProjects = persistentData.map {
                Project(projectId = it.projectId, title = it.projectName)
            }
        }
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        val cached = CacheManager.getProjects()
        if (cached != null) {
            allProjects = cached
        } else {
            try {
                val response = ApiClient.service.getProjects()
                if (response.isSuccessful) {
                    response.body()?.let { projects ->
                        CacheManager.cacheProjects(projects)
                        allProjects = projects
                    }
                }
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(allProjects, pinnedProjects) {
        if (apiKey.isNullOrBlank() || allProjects.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(500)

        val prioritizedProjects = allProjects.sortedByDescending { it.projectId in pinnedProjects }
        val consecutiveFailures = AtomicInteger(0)
        val maxConsecutiveFailures = 5

        prioritizedProjects.chunked(3).forEach { batch ->
            if (consecutiveFailures.get() >= maxConsecutiveFailures) return@LaunchedEffect
            batch.forEach { project ->
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (CacheManager.getProjectSamples(project.projectId) != null &&
                        CacheManager.getProjectDatasets(project.projectId) != null) return@launch

                    var hadError = false
                    try {
                        val samplesResp = ApiClient.service.getSamplesByProject(project.projectId)
                        if (samplesResp.isSuccessful) {
                            consecutiveFailures.set(0)
                            samplesResp.body()?.let { CacheManager.cacheProjectSamples(project.projectId, it) }
                        }
                        val datasetsResp = ApiClient.service.getDatasetsByProject(project.projectId)
                        if (datasetsResp.isSuccessful) {
                            consecutiveFailures.set(0)
                            datasetsResp.body()?.let { CacheManager.cacheProjectDatasets(project.projectId, it) }
                        }
                    } catch (e: Exception) {
                        hadError = true
                    }
                    if (hadError) consecutiveFailures.incrementAndGet()
                }
            }
            kotlinx.coroutines.delay(150)
        }

        launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val samplesMap = mutableMapOf<String, List<crucible.lens.data.model.Sample>>()
                val datasetsMap = mutableMapOf<String, List<crucible.lens.data.model.Dataset>>()
                allProjects.forEach { project ->
                    CacheManager.getProjectSamples(project.projectId)?.let { samplesMap[project.projectId] = it }
                    CacheManager.getProjectDatasets(project.projectId)?.let { datasetsMap[project.projectId] = it }
                }
                PersistentProjectCache.saveProjectData(context, allProjects, samplesMap, datasetsMap)
            } catch (e: Exception) { }
        }
    }

    val pinnedList = remember(pinnedProjects, allProjects) {
        allProjects.filter { it.projectId in pinnedProjects }.take(3)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Crucible Lens",
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {
                                clickCount++
                                if (clickCount >= 7) { showEasterEggDialog = true; clickCount = 0 }
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(graphExplorerUrl)))
                        }) { Icon(Icons.Default.Language, contentDescription = "Open Web Explorer") }
                        IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = "History") }
                        IconButton(onClick = { showHelpDialog = true }) { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help") }
                        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                    }
                )
                OfflineBanner()
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeLogo(isDarkTheme = isDarkTheme)
                HomeSearchPill(onClick = onSearch)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                HomeBrowseSection(
                    onBrowseProjects = onBrowseProjects,
                    onBrowseInstruments = onBrowseInstruments,
                    onScanClick = onScanClick
                )
                HomeCreateSection(onCreateSample = onCreateSample, onCreateDataset = onCreateDataset)
                if (lastVisitedResource != null && lastVisitedResourceName != null) {
                    HomeLastVisited(
                        uuid = lastVisitedResource,
                        name = lastVisitedResourceName,
                        onClick = { onManualEntry(lastVisitedResource) }
                    )
                }
                HomePinnedProjects(pinnedList = pinnedList, onProjectClick = onProjectClick)
            }

            HomeFooter(graphExplorerUrl = graphExplorerUrl)
        }
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false }, onSettings = { showHelpDialog = false; onSettingsClick() })
    }
    if (showEasterEggDialog) {
        EasterEggDialog(onDismiss = { showEasterEggDialog = false })
    }
}

@Composable
private fun HomeLogo(isDarkTheme: Boolean) {
    val taglines = remember {
        listOf(
            "Your mobile window into the Molecular Foundry's data ecosystem.",
            "Because scientists deserve decent mobile apps, too.",
            "Point. Scan. Science.",
            "The Molecular Foundry in your pocket — data only tho, the lab stays there.",
            "For when you need your sample data but left your laptop behind.",
            "Turning QR codes into knowledge, one scan at a time.",
            "Making nanoscience slightly less paperwork-y.",
            "Data at your fingertips. Samples in the lab. Coffee in hand.",
            "Scan first, ask questions later...",
            "Where QR codes meet 'real' science.",
            "Bridging the gap between the glove box and the couch.",
            "Your lab notebook — fits in your pocket and won't absorb spills.",
            "Because even nanomaterials deserve good metadata.",
            "Track samples, not sticky notes.",
            "Less clipboard, more science.",
            "Samples have stories. Crucible helps tell them.",
            "What about the 11k project?"
        )
    }
    var tagline by remember { mutableStateOf(taglines[0]) }
    var lastTapTime by remember { mutableStateOf(0L) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(
                id = if (isDarkTheme) R.drawable.crucible_text_dark else R.drawable.crucible_text_light
            ),
            contentDescription = "Crucible",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 350L) {
                        tagline = taglines.filter { it != tagline }.random()
                        lastTapTime = 0L
                    } else {
                        lastTapTime = now
                    }
                }
        )
        AnimatedContent(
            targetState = tagline,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            transitionSpec = {
                fadeIn(tween(durationMillis = 500, delayMillis = 200)) togetherWith fadeOut(tween(durationMillis = 300))
            },
            label = "tagline"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeSearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text("Search samples, datasets...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeBrowseSection(
    onBrowseProjects: () -> Unit,
    onBrowseInstruments: () -> Unit,
    onScanClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Browse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onBrowseProjects,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Projects", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onBrowseInstruments,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Biotech, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Instruments", style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onScanClick,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.medium,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan QR", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HomeCreateSection(onCreateSample: () -> Unit, onCreateDataset: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Create", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCreateSample,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Sample", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onCreateDataset,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Dataset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Dataset", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HomeLastVisited(uuid: String, name: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Last Visited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = size.width * 0.8f,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee()
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomePinnedProjects(pinnedList: List<Project>, onProjectClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Pinned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (pinnedList.isNotEmpty()) {
            pinnedList.forEach { project ->
                Card(
                    onClick = { onProjectClick(project.projectId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = project.title ?: project.projectId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), modifier = Modifier.size(26.dp))
                    Text("No pinned projects", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("Tap the bookmark icon on a project to pin it here", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun HomeFooter(graphExplorerUrl: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(graphExplorerUrl)))
        }) {
            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Web Explorer", style = MaterialTheme.typography.labelLarge)
        }
        val footerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        val footerStyle = MaterialTheme.typography.labelSmall
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crucible Lens v${BuildConfig.VERSION_NAME} • by ", style = footerStyle, color = footerColor)
            Text(
                "Crucible Team",
                style = footerStyle,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://crucible.lbl.gov/")))
                }
            )
            Text(" • Molecular Foundry", style = footerStyle, color = footerColor)
        }
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit, onSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        },
        title = { Text("How to Use Crucible Lens", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HelpSection(Icons.Default.QrCodeScanner, "Scan QR Codes",
                    "Point your camera at any Crucible QR code to instantly load sample or dataset details. The app vibrates when a code is detected.")
                HelpSection(Icons.Default.Search, "Global Search",
                    "Search across all cached samples and datasets by name, type, keywords, metadata, and more. Available from the home screen and from any browse or detail screen.")
                HelpSection(Icons.Default.Folder, "Browse Projects",
                    "Explore all projects and their contents. Tap the bookmark icon to pin favorites — they appear on the home screen for quick access. Swipe a project left to archive it.")
                HelpSection(Icons.Default.Biotech, "Browse Instruments",
                    "View all registered instruments at the Molecular Foundry. Tap an instrument to see its details and the datasets collected with it.")
                HelpSection(Icons.Default.History, "History",
                    "The clock icon (top right) shows recently viewed resources so you can jump back to them instantly.")
                HelpSection(Icons.Default.Info, "Resource Details",
                    "From any sample or dataset card: copy the unique ID, display its QR code, share a link, or open it directly in the Graph Explorer.")
                HelpSection(Icons.Default.Language, "Web Explorer",
                    "Access the full Crucible web interface for advanced features and data exploration.")

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            Text("About Crucible", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            "Crucible is the Molecular Foundry's data management system for tracking samples, datasets, and experimental workflows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Need to configure your API key? ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Go to Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSettings))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it!") } }
    )
}

@Composable
private fun HelpSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EasterEggDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
        title = { Text("Loading Messages", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "All ${allLoadingMessages.size} things the app thinks about while you wait:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                allLoadingMessages.forEachIndexed { index, message ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Nice!") } }
    )
}
