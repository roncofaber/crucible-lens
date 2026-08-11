@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.settings
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.ui.common.AppTopBar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent

/**
 * One-time, mandatory-but-skippable prompt shown (via `NavGraph`'s top-level gate) whenever the
 * signed-in user's profile is missing a username or email - regardless of whether they signed in
 * via ORCID or a pasted API key, and regardless of whether the account is brand new or has been
 * incomplete for a while. Reuses [AccountViewModel] wholesale (same edit state, same debounced
 * username check, same save call as [AccountScreen]'s own edit mode) rather than a second
 * profile-editing ViewModel. No back button - the only ways out are Save or "Skip for now".
 */
@Composable
fun CompleteProfileScreen(
    viewModel: AccountViewModel,
    onDone: () -> Unit
) {
    val profileState by viewModel.profileState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    // Plain val (not `by`), so the compiler can smart-cast it below - a delegated property's
    // getter isn't guaranteed to return the same value on repeated reads.
    val activeDraft: EditUiState.Editing? = viewModel.activeDraft.collectAsState().value
    var hasStartedEditing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    // Enter edit mode as soon as the profile is available - this screen's only purpose is editing.
    LaunchedEffect(profileState) {
        if (profileState is ProfileUiState.Loaded && !hasStartedEditing) {
            viewModel.startEdit()
            hasStartedEditing = true
        }
    }

    LaunchedEffect(editState) {
        // saveProfile() resets editState to Idle only on success - after we've actually started
        // editing, seeing Idle again means the save completed, not that editing never started.
        if (hasStartedEditing && editState is EditUiState.Idle) onDone()
    }

    val isSaving = editState is EditUiState.Saving
    val saveError = (editState as? EditUiState.SaveError)?.reason
    val usernameFormatValid = activeDraft?.usernameFormatValid ?: true
    val canSave = !isSaving && activeDraft != null &&
        activeDraft.username.isNotBlank() &&
        activeDraft.usernameCheck !is UsernameCheckState.Checking &&
        activeDraft.usernameCheck !is UsernameCheckState.Taken &&
        usernameFormatValid
    val orcid = (profileState as? ProfileUiState.Loaded)?.user?.uniqueId

    AppScaffold(
        topBar = { AppTopBar(title = "Complete Your Profile") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val ps = profileState) {
                is ProfileUiState.Idle, is ProfileUiState.Loading -> LoadingContent(title = "Loading your profile")
                is ProfileUiState.NotLoggedIn -> LaunchedEffect(Unit) { onDone() }
                is ProfileUiState.Error -> ErrorCard(
                    title = "Could not load profile",
                    message = ps.message,
                    onRetry = { viewModel.retryLoad() }
                )
                is ProfileUiState.Loaded -> {
                    Text(
                        "Add a username so others can find you and add you to projects.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activeDraft != null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ProfileEditFields(
                                draft = activeDraft,
                                orcid = orcid,
                                isSaving = isSaving,
                                saveError = saveError,
                                onFirstNameChanged = viewModel::onFirstNameChanged,
                                onLastNameChanged = viewModel::onLastNameChanged,
                                onEmailChanged = viewModel::onEmailChanged,
                                onUsernameChanged = viewModel::onUsernameChanged
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::saveProfile,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSave
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Text("Save")
                        }
                    }
                    TextButton(
                        onClick = {
                            ProfileCompletionGate.skippedThisLaunch = true
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Text("Skip for now")
                    }
                }
            }
        }
    }
}
