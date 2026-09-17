package crucible.lens.ui.detail.components
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.ConfirmationDialog

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailUpdateRequest
import crucible.lens.platform.ImagePickerResult
import crucible.lens.platform.PlatformBase64
import crucible.lens.platform.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ThumbnailsSection(
    uuid: String,
    thumbnails: List<Thumbnail>,
    deletingThumbnailIds: Set<Int> = emptySet(),
    deleteErrors: Map<Int, String> = emptyMap(),
    updatingThumbnailIds: Set<Int> = emptySet(),
    updateErrors: Map<Int, String> = emptyMap(),
    canEdit: Boolean = false,
    onDelete: (thumbnailId: Int) -> Unit = {},
    onUpdate: (thumbnailId: Int, request: ThumbnailUpdateRequest) -> Unit = { _, _ -> }
) {
    thumbnails.forEachIndexed { index, thumbnail ->
        var showDeleteDialog by remember(thumbnail.id) { mutableStateOf(false) }
        var showEditDialog by remember(thumbnail.id) { mutableStateOf(false) }
        var wasUpdating by remember(thumbnail.id) { mutableStateOf(false) }
        val isDeleting = thumbnail.id in deletingThumbnailIds
        val isUpdating = thumbnail.id in updatingThumbnailIds
        val deleteError = deleteErrors[thumbnail.id]
        val updateError = updateErrors[thumbnail.id]

        if (showEditDialog && thumbnail.id >= 0) {
            EditThumbnailDialog(
                thumbnail = thumbnail,
                isUpdating = isUpdating,
                error = updateError,
                onSave = { request -> onUpdate(thumbnail.id, request) },
                onDismiss = { if (!isUpdating) showEditDialog = false }
            )
            LaunchedEffect(isUpdating, updateError) {
                if (wasUpdating && !isUpdating && updateError == null) showEditDialog = false
                wasUpdating = isUpdating
            }
        }

        if (showDeleteDialog && thumbnail.id >= 0) {
            ConfirmationDialog(
                icon = AppIcons.RequestDeletion,
                title = "Delete thumbnail?",
                text = "This thumbnail will be permanently removed from the dataset.",
                confirmLabel = "Delete",
                isDestructive = true,
                onConfirm = { showDeleteDialog = false; onDelete(thumbnail.id) },
                onDismiss = { showDeleteDialog = false }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            var imageState by remember { mutableStateOf<String?>(null) }

            var base64Data by remember(uuid, index) { mutableStateOf<ByteArray?>(null) }
            LaunchedEffect(uuid, index) {
                base64Data = try {
                    withContext(Dispatchers.Default) { PlatformBase64.decode(thumbnail.thumbnailB64) }
                } catch (e: Exception) {
                    imageState = "error: ${e.message ?: "Failed to decode image"}"
                    null
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .then(
                        if (canEdit && thumbnail.id >= 0)
                            Modifier.combinedClickable(
                                enabled = !isDeleting && !isUpdating,
                                onClick = {},
                                onLongClick = { showDeleteDialog = true }
                            )
                        else Modifier
                    ),
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
                        onLoading = { imageState = "loading" },
                        onSuccess = { imageState = null },
                        onError = { imageState = "error: ${it.result.throwable.message}" }
                    )
                }

                when {
                    isDeleting || isUpdating || imageState == "loading" -> CircularProgressIndicator()
                    imageState?.startsWith("error") == true -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(AppIcons.ErrorOutline, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                        Text(
                            imageState?.removePrefix("error: ")?.ifBlank { "Failed to load image" } ?: "Failed to load image",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (canEdit && thumbnail.id >= 0) {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = !isDeleting && !isUpdating,
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) { AppIcon(AppIcons.Edit) }
                }

                if (canEdit && thumbnail.id >= 0 && deleteError == null) {
                    Text(
                        when {
                            isDeleting -> "Deleting"
                            isUpdating -> "Updating"
                            else -> "Hold to delete"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }
            }

            if (canEdit && deleteError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        deleteError,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { onDelete(thumbnail.id) }, enabled = !isDeleting && !isUpdating) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditThumbnailDialog(
    thumbnail: Thumbnail,
    isUpdating: Boolean,
    error: String?,
    onSave: (ThumbnailUpdateRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(thumbnail.id) { mutableStateOf(thumbnail.thumbnailName.orEmpty()) }
    var replacement by remember(thumbnail.id) { mutableStateOf<String?>(null) }
    var selectedFilename by remember(thumbnail.id) { mutableStateOf<String?>(null) }
    var pickerError by remember(thumbnail.id) { mutableStateOf<String?>(null) }
    val pickImage = rememberImagePicker { result ->
        when (result) {
            is ImagePickerResult.Success -> {
                replacement = PlatformBase64.encode(result.bytes)
                selectedFilename = result.filename
                pickerError = null
            }
            is ImagePickerResult.Failure -> pickerError = result.message
            ImagePickerResult.Cancelled -> Unit
        }
    }
    val changed = name != thumbnail.thumbnailName.orEmpty() || replacement != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit thumbnail") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isUpdating,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = pickImage, enabled = !isUpdating, modifier = Modifier.fillMaxWidth()) {
                    AppIcon(AppIcons.FileImage)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedFilename ?: "Replace image")
                }
                (pickerError ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ThumbnailUpdateRequest(thumbnailName = name, thumbnailB64str = replacement))
                },
                enabled = changed && !isUpdating
            ) {
                if (isUpdating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isUpdating) { Text("Cancel") } }
    )
}
