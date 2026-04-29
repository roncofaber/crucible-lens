package crucible.lens.ui.create

import androidx.compose.runtime.Composable

@Composable
expect fun CreateDatasetScreen(
    initialProjectId: String?,
    onBack: () -> Unit,
    onCreated: (uuid: String) -> Unit
)
