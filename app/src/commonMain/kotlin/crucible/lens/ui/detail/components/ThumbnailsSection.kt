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
import crucible.lens.platform.PlatformBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ThumbnailsSection(
    uuid: String,
    thumbnails: List<Thumbnail>,
    onDelete: (thumbnailId: Int) -> Unit = {}
) {
    thumbnails.forEachIndexed { index, thumbnail ->
        var showDeleteDialog by remember { mutableStateOf(false) }

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
                        if (thumbnail.id >= 0)
                            Modifier.combinedClickable(onClick = {}, onLongClick = { showDeleteDialog = true })
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
                    imageState == "loading" -> CircularProgressIndicator()
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

                if (thumbnail.id >= 0) {
                    Text(
                        "Hold to delete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
