package crucible.lens.platform

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that opens the system gallery to pick an image.
 * On Android: uses ActivityResultContracts.GetContent
 * On iOS: uses PHPickerViewController (TODO: full implementation)
 */
@Composable
expect fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): () -> Unit

/**
 * Returns a launcher that opens the camera to capture an image.
 * On Android: uses ActivityResultContracts.TakePicture with FileProvider
 * On iOS: uses UIImagePickerController (TODO: full implementation)
 */
@Composable
expect fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): () -> Unit
