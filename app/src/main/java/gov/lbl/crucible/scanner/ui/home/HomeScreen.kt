package gov.lbl.crucible.scanner.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import gov.lbl.crucible.scanner.BuildConfig
import gov.lbl.crucible.scanner.R
import gov.lbl.crucible.scanner.data.api.ApiClient
import gov.lbl.crucible.scanner.data.cache.CacheManager
import gov.lbl.crucible.scanner.ui.common.allLoadingMessages
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    onSettingsClick: () -> Unit,
    onHistory: () -> Unit = {},
    onSearch: () -> Unit = {},
    pinnedProjects: Set<String> = emptySet(),
    onProjectClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var clickCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Preload projects data in background if API key is available
    LaunchedEffect(apiKey) {
        if (!apiKey.isNullOrBlank()) {
            // Only load if not already cached
            if (CacheManager.getProjects() == null) {
                scope.launch {
                    try {
                        val response = ApiClient.service.getProjects()
                        if (response.isSuccessful) {
                            response.body()?.let { projects ->
                                CacheManager.cacheProjects(projects)
                            }
                        }
                    } catch (e: Exception) {
                        // Silently fail - user can still load manually
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Crucible Lens",
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            clickCount++
                            if (clickCount >= 7) {
                                showEasterEggDialog = true
                                clickCount = 0
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Crucible Logo Text
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

            Image(
                painter = painterResource(
                    id = if (isDarkTheme) R.drawable.crucible_text_dark else R.drawable.crucible_text_logo
                ),
                contentDescription = "Crucible",
                modifier = Modifier
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

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = tagline, label = "tagline") { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Last Visited Resource Button
            if (lastVisitedResource != null && lastVisitedResourceName != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TextButton(
                    onClick = { onManualEntry(lastVisitedResource) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Last visited: ",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = lastVisitedResourceName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Search Button
            OutlinedButton(
                onClick = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Search", style = MaterialTheme.typography.titleMedium)
            }

            // Browse Projects + Scan QR Code side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBrowseProjects,
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Projects", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Scan QR Code", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Pinned Projects (up to 3)
            val allProjects = remember(pinnedProjects) { CacheManager.getProjects() ?: emptyList() }
            val pinnedList = remember(pinnedProjects, allProjects) {
                allProjects.filter { it.projectId in pinnedProjects }.take(3)
            }
            if (pinnedList.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Pinned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pinnedList.forEach { project ->
                        Card(
                            onClick = { onProjectClick(project.projectId) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = project.projectName ?: project.projectId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Web Explorer Button (smaller)
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(graphExplorerUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Open Web Explorer",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Footer with version and credits
            val footerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val footerStyle = MaterialTheme.typography.labelSmall
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crucible Lens v${BuildConfig.VERSION_NAME} • by ",
                    style = footerStyle,
                    color = footerColor
                )
                Text(
                    text = "@roncofaber",
                    style = footerStyle,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/roncofaber"))
                        context.startActivity(intent)
                    }
                )
                Text(
                    text = " • Molecular Foundry",
                    style = footerStyle,
                    color = footerColor
                )
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        HelpDialog(
            onDismiss = { showHelpDialog = false },
            onSettings = { showHelpDialog = false; onSettingsClick() }
        )
    }

    // Easter Egg Dialog
    if (showEasterEggDialog) {
        EasterEggDialog(onDismiss = { showEasterEggDialog = false })
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit, onSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "How to Use Crucible Lens",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HelpSection(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan QR Codes",
                    description = "Point your camera at any Crucible QR code to instantly load sample or dataset details. The app vibrates when a code is detected."
                )

                HelpSection(
                    icon = Icons.Default.Search,
                    title = "Global Search",
                    description = "Search across all cached samples and datasets by name, type, keywords, metadata, and more. Available from the home screen and from any browse or detail screen."
                )

                HelpSection(
                    icon = Icons.Default.Folder,
                    title = "Browse Projects",
                    description = "Explore all projects and their contents. Tap the bookmark icon to pin favorites — they appear on the home screen for quick access. Swipe a project left to archive it."
                )

                HelpSection(
                    icon = Icons.Default.History,
                    title = "History",
                    description = "The clock icon (top right) shows recently viewed resources so you can jump back to them instantly."
                )

                HelpSection(
                    icon = Icons.Default.Info,
                    title = "Resource Details",
                    description = "From any sample or dataset card: copy the unique ID, display its QR code, share a link, or open it directly in the Graph Explorer."
                )

                HelpSection(
                    icon = Icons.Default.Language,
                    title = "Web Explorer",
                    description = "Access the full Crucible web interface for advanced features and data exploration."
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "About Crucible",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "Crucible is the Molecular Foundry's data management system for tracking samples, datasets, and experimental workflows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Need to configure your API key? ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Go to Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onSettings)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it!")
            }
        }
    )
}

@Composable
private fun HelpSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EasterEggDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Loading Messages",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "All ${allLoadingMessages.size} things the app thinks about while you wait:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                allLoadingMessages.forEachIndexed { index, message ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Nice!")
            }
        }
    )
}
