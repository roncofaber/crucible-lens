package crucible.lens.platform

import androidx.compose.runtime.Composable

sealed interface CameraPickerResult {
    data class Success(val bytes: ByteArray) : CameraPickerResult
    data object Cancelled : CameraPickerResult
    data class PermissionDenied(val requiresSettings: Boolean) : CameraPickerResult
    data object Unavailable : CameraPickerResult
    data class Failure(val message: String) : CameraPickerResult
}

sealed interface ImagePickerResult {
    data class Success(val bytes: ByteArray, val filename: String) : ImagePickerResult
    data object Cancelled : ImagePickerResult
    data class Failure(val message: String) : ImagePickerResult
}

@Composable
expect fun rememberImagePicker(onResult: (ImagePickerResult) -> Unit): () -> Unit

@Composable
expect fun rememberCameraPicker(onResult: (CameraPickerResult) -> Unit): () -> Unit
