package crucible.lens.ui.instruments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import crucible.lens.data.model.Instrument
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.detail.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageInstrumentScreen(
    viewModel: ManageInstrumentViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val editState by viewModel.editState.collectAsState()

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Instrument") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state is InstrumentManageState.Loaded && editState is InstrumentEditState.Idle) {
                        IconButton(onClick = { viewModel.startEdit() }) {
                            Icon(Icons.Default.Edit, "Edit instrument")
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                is InstrumentManageState.Loading -> LoadingContent(title = "Loading instrument")
                is InstrumentManageState.Error -> ErrorCard(
                    title = "Could not load instrument",
                    message = s.message,
                    onRetry = { viewModel.load() }
                )
                is InstrumentManageState.Loaded -> when (val es = editState) {
                    is InstrumentEditState.Idle -> InstrumentInfoCard(s.instrument)
                    is InstrumentEditState.Editing,
                    is InstrumentEditState.Saving,
                    is InstrumentEditState.SaveError -> {
                        val draft = when (es) {
                            is InstrumentEditState.Editing -> es
                            is InstrumentEditState.SaveError -> es.draft
                            else -> return@Column
                        }
                        InstrumentEditCard(
                            draft = draft,
                            isSaving = es is InstrumentEditState.Saving,
                            saveError = (es as? InstrumentEditState.SaveError)?.message,
                            onNameChanged = viewModel::onNameChanged,
                            onTypeChanged = viewModel::onTypeChanged,
                            onManufacturerChanged = viewModel::onManufacturerChanged,
                            onModelChanged = viewModel::onModelChanged,
                            onLocationChanged = viewModel::onLocationChanged,
                            onOwnerChanged = viewModel::onOwnerChanged,
                            onDescriptionChanged = viewModel::onDescriptionChanged,
                            onSave = { viewModel.save() },
                            onCancel = { viewModel.cancelEdit() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstrumentInfoCard(instrument: Instrument) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoRow(icon = Icons.Default.Biotech, label = "Name", value = instrument.instrumentName ?: "—")
            InfoRow(icon = Icons.Default.Category, label = "Type", value = instrument.instrumentType ?: "—")
            InfoRow(icon = Icons.Default.Factory, label = "Manufacturer", value = instrument.manufacturer ?: "—")
            InfoRow(icon = Icons.Default.Straighten, label = "Model", value = instrument.model ?: "—")
            InfoRow(icon = Icons.Default.Place, label = "Location", value = instrument.location ?: "—")
            InfoRow(icon = Icons.Default.Person, label = "Owner", value = instrument.owner ?: "—")
            if (!instrument.description.isNullOrBlank()) {
                InfoRow(icon = Icons.AutoMirrored.Filled.Notes, label = "Description", value = instrument.description)
            }
            if (!instrument.otherId.isNullOrBlank()) {
                InfoRow(icon = Icons.Default.Tag, label = "External ID", value = instrument.otherId + (instrument.otherIdSource?.let { " ($it)" } ?: ""))
            }
            InfoRow(icon = Icons.Default.Tag, label = "ID", value = instrument.uniqueId)
        }
    }
}

@Composable
private fun InstrumentEditCard(
    draft: InstrumentEditState.Editing,
    isSaving: Boolean,
    saveError: String?,
    onNameChanged: (String) -> Unit,
    onTypeChanged: (String) -> Unit,
    onManufacturerChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onLocationChanged: (String) -> Unit,
    onOwnerChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit Instrument", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (saveError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(saveError, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            OutlinedTextField(draft.name, onNameChanged, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.type, onTypeChanged, label = { Text("Type") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.manufacturer, onManufacturerChanged, label = { Text("Manufacturer") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.model, onModelChanged, label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.location, onLocationChanged, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.owner, onOwnerChanged, label = { Text("Owner") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            OutlinedTextField(draft.description, onDescriptionChanged, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), enabled = !isSaving, minLines = 2, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving) { Text("Cancel") }
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save")
                }
            }
        }
    }
}
