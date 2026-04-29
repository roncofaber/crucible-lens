package crucible.lens.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import dev.icerock.moko.media.compose.BindMediaPickerEffect
import dev.icerock.moko.media.compose.rememberMediaPickerControllerFactory
import dev.icerock.moko.media.picker.MediaSource
import kotlinx.coroutines.launch

@Composable
actual fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val factory = rememberMediaPickerControllerFactory()
    val controller = factory.createMediaPickerController()
    BindMediaPickerEffect(controller)
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            try {
                val image = controller.pickImage(MediaSource.GALLERY)
                onResult(image.toByteArray())
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }
}

@Composable
actual fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val factory = rememberMediaPickerControllerFactory()
    val controller = factory.createMediaPickerController()
    BindMediaPickerEffect(controller)
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            try {
                val image = controller.pickImage(MediaSource.CAMERA)
                onResult(image.toByteArray())
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }
}
