@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package crucible.lens.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.AppTopBar
import crucible.lens.ui.common.DiscardChangesDialog
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateInstrumentScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onHome: () -> Unit = {}
) {
    val viewModel: CreateInstrumentViewModel = koinViewModel()
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val isSaving = saveState is SaveState.Saving
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun navigate(action: () -> Unit) {
        if (isSaving) return
        if (form.hasUnsavedChanges) pendingNavigation = action else action()
    }

    BackHandler(enabled = form.hasUnsavedChanges) {
        if (!isSaving) pendingNavigation = onBack
    }

    pendingNavigation?.let { action ->
        DiscardChangesDialog(
            onConfirm = { pendingNavigation = null; action() },
            onDismiss = { pendingNavigation = null }
        )
    }

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                viewModel.resetSaveState()
                onCreated(state.uuid)
            }
            is SaveState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetSaveState()
            }
            else -> Unit
        }
    }

    AppScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Register Instrument",
                onBack = { navigate(onBack) },
                actions = {
                    IconButton(onClick = { navigate(onHome) }, enabled = !isSaving) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Display name *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Instrument) }
            )
            OutlinedTextField(
                value = form.instrumentId,
                onValueChange = viewModel::onInstrumentIdChanged,
                label = { Text("Instrument ID *") },
                supportingText = {
                    Text(form.instrumentIdError ?: "Used in links and integrations. It can be renamed later.")
                },
                isError = form.instrumentIdError != null,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Tag) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii
                )
            )
            OutlinedTextField(
                value = form.location,
                onValueChange = viewModel::onLocationChanged,
                label = { Text("Location *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.LocationAlt) }
            )

            Text(
                "You will be registered as the owner. Ownership can be transferred from Manage Instrument.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Details", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = form.type,
                onValueChange = viewModel::onTypeChanged,
                label = { Text("Instrument type") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Category) }
            )
            OutlinedTextField(
                value = form.manufacturer,
                onValueChange = viewModel::onManufacturerChanged,
                label = { Text("Manufacturer") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.Factory) }
            )
            OutlinedTextField(
                value = form.model,
                onValueChange = viewModel::onModelChanged,
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true,
                leadingIcon = { AppIcon(AppIcons.InstrumentModel) }
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                minLines = 3,
                leadingIcon = { AppIcon(AppIcons.Description) }
            )
            OutlinedTextField(
                value = form.otherId,
                onValueChange = viewModel::onOtherIdChanged,
                label = { Text("External ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true
            )
            OutlinedTextField(
                value = form.otherIdSource,
                onValueChange = viewModel::onOtherIdSourceChanged,
                label = { Text("ID source") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                singleLine = true
            )

            Button(
                onClick = viewModel::create,
                enabled = form.canCreate && !isSaving,
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
                    Spacer(Modifier.width(8.dp))
                    Text("Register Instrument")
                }
            }
        }
    }
}
