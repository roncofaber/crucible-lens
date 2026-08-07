@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.create

import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.DiscardChangesDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.data.util.userDisplayName
import crucible.lens.ui.common.ResolvedPicker
import crucible.lens.ui.common.SearchPickerField
import crucible.lens.ui.common.UserChipLeading
import crucible.lens.ui.common.UserPickerItemContent
import org.koin.compose.viewmodel.koinViewModel

/**
 * Creates a personal/ad-hoc project. Per the server's current (deliberately open) design, any
 * authenticated user can create a project naming any existing user as its lead - there is no
 * approval step. [projectId] is effectively immutable afterward (no rename route), which is why
 * it is called out with a hint rather than just a placeholder.
 */
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onCreated: (projectId: String) -> Unit,
    onHome: () -> Unit = {}
) {
    val createViewModel: CreateProjectViewModel = koinViewModel()
    val form by createViewModel.formState.collectAsState()
    val saveState by createViewModel.saveState.collectAsState()
    val isSaving = saveState is SaveState.Saving
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveState) {
        when (val s = saveState) {
            is SaveState.Success -> { createViewModel.resetState(); onCreated(s.uuid) }
            is SaveState.Error -> { snackbarHostState.showSnackbar(s.message); createViewModel.resetState() }
            else -> {}
        }
    }

    val hasUnsavedChanges = form.projectId.isNotBlank() || form.title.isNotBlank() ||
        form.organization.isNotBlank() || form.leadUsername.isNotBlank()
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    pendingNavigation?.let { action ->
        DiscardChangesDialog(
            onConfirm = { pendingNavigation = null; action() },
            onDismiss = { pendingNavigation = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "New Project",
                onBack = { if (hasUnsavedChanges) pendingNavigation = onBack else onBack() },
                actions = {
                    IconButton(onClick = { if (hasUnsavedChanges) pendingNavigation = onHome else onHome() }) {
                        AppIcon(AppIcons.Home)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Basic Info", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = form.title,
                onValueChange = createViewModel::onTitleChanged,
                label = { Text("Project name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                leadingIcon = { AppIcon(AppIcons.Notes) }
            )
            OutlinedTextField(
                value = form.projectId,
                onValueChange = createViewModel::onProjectIdChanged,
                label = { Text("Project ID *") },
                supportingText = { Text("The permanent handle for this project - it can't be changed later") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                leadingIcon = { AppIcon(AppIcons.Project) }
            )
            OutlinedTextField(
                value = form.organization,
                onValueChange = createViewModel::onOrganizationChanged,
                label = { Text("Organization *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving,
                leadingIcon = { AppIcon(AppIcons.Business) }
            )
            SearchPickerField(
                query = form.leadUsername,
                onQueryChange = createViewModel::onLeadUsernameChanged,
                isSearching = form.isLeadSearching,
                results = form.leadSearch,
                onSelect = createViewModel::selectLeadUser,
                label = "Project lead *",
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                resolution = ResolvedPicker(
                    keyOf = { it.username },
                    resolvedLabel = { userDisplayName(it) },
                    onClear = { createViewModel.onLeadUsernameChanged("") },
                    resolvedLeading = { user -> UserChipLeading(user) }
                ),
                itemContent = { user -> UserPickerItemContent(user) }
            )

            Button(
                onClick = createViewModel::create,
                enabled = form.title.isNotBlank() && form.projectId.isNotBlank() && form.organization.isNotBlank() &&
                    form.leadUsername.isNotBlank() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    AppIcon(AppIcons.Add, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Project")
                }
            }
        }
    }
}
