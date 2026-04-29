package crucible.lens.platform

import androidx.compose.runtime.Composable

// TODO: implement with PHPickerViewController / UIImagePickerController via interop
@Composable
actual fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }

@Composable
actual fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): () -> Unit = { onResult(null) }
