@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.projects
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.sortForPicker
import crucible.lens.data.util.suggestedProjectIds
import crucible.lens.ui.common.AppScaffold
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SyncPickerScreen(
    isFirstRun: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val prefs: AppPreferences = koinInject()
    val repository: CrucibleRepository = koinInject()

    val syncedProjects by prefs.syncedProjects.collectAsStateWithLifecycle()
    val userOrcid by prefs.userOrcid.collectAsStateWithLifecycle()
    val pinnedProjects by prefs.pinnedProjects.collectAsStateWithLifecycle()
    val projectsFlow = repository.observeProjects()
    val projects by projectsFlow.collectAsStateWithLifecycle(initialValue = repository.getCachedProjects() ?: emptyList())

    var localSelection by remember(syncedProjects) { mutableStateOf(syncedProjects) }
    val scope = rememberCoroutineScope()

    val suggested = suggestedProjectIds(projects ?: emptyList(), userOrcid, pinnedProjects)
    val sortedProjects = sortForPicker(projects ?: emptyList(), suggested)

    fun saveAndClose() {
        scope.launch {
            prefs.setSyncedProjects(localSelection)
            if (isFirstRun) {
                prefs.saveSyncSetupComplete(true)
            }
            onDone()
        }
    }

    fun handleBack() {
        if (isFirstRun) {
            scope.launch {
                prefs.saveSyncSetupComplete(true)
                onBack()
            }
        } else {
            onBack()
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = "Synced projects",
                onBack = ::handleBack
            )
        },
        bottomBar = {
            if (isFirstRun) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = ::saveAndClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .height(40.dp)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isFirstRun) {
                Text(
                    "Choose which projects to sync for offline access. You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (suggested.isNotEmpty()) {
                TextButton(
                    onClick = {
                        localSelection = suggested
                        if (!isFirstRun) {
                            scope.launch {
                                prefs.setSyncedProjects(suggested)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("Select suggested (${suggested.size})")
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedProjects.forEach { project ->
                    ProjectSyncRow(
                        project = project,
                        isSelected = project.projectId in localSelection,
                        onToggle = { isSelected ->
                            localSelection = if (isSelected) {
                                localSelection + project.projectId
                            } else {
                                localSelection - project.projectId
                            }
                            if (!isFirstRun) {
                                scope.launch {
                                    prefs.setSyncedProjects(localSelection)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSyncRow(
    project: crucible.lens.data.model.Project,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                project.title ?: project.projectId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                project.projectId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isSelected,
            onCheckedChange = onToggle
        )
    }
}
