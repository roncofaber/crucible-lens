package crucible.lens.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberCameraPicker(onResult: (CameraPickerResult) -> Unit): () -> Unit {
    val currentOnResult = androidx.compose.runtime.rememberUpdatedState(onResult)
    val callback = remember { CameraCallback { currentOnResult.value(it) } }
    val launchCamera = remember(callback) {
        fun launch() {
            val cameraType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            if (!UIImagePickerController.isSourceTypeAvailable(cameraType)) {
                currentOnResult.value(CameraPickerResult.Unavailable)
                return
            }
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootVC == null) {
                currentOnResult.value(CameraPickerResult.Failure("The camera could not be opened"))
                return
            }
            try {
                val picker = UIImagePickerController()
                picker.sourceType = cameraType
                picker.allowsEditing = false
                picker.delegate = callback
                rootVC.presentViewController(picker, animated = true, completion = null)
            } catch (_: Throwable) {
                currentOnResult.value(CameraPickerResult.Failure("The camera could not be opened"))
            }
        }
        ::launch
    }
    return remember(launchCamera) { { launchCamera() } }
}

@Composable
actual fun rememberImagePicker(onResult: (ImagePickerResult) -> Unit): () -> Unit {
    val currentOnResult = androidx.compose.runtime.rememberUpdatedState(onResult)
    val callback = remember { ImageCallback { currentOnResult.value(it) } }
    return remember(callback) {
        {
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootVC == null) {
                currentOnResult.value(ImagePickerResult.Failure("The image picker could not be opened"))
                return@remember
            }
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            try {
                val picker = PHPickerViewController(configuration = config)
                picker.delegate = callback
                rootVC.presentViewController(picker, animated = true, completion = null)
            } catch (_: Throwable) {
                currentOnResult.value(ImagePickerResult.Failure("The image picker could not be opened"))
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toJpegBytes(): ByteArray? {
    val data: NSData = UIImageJPEGRepresentation(this, 0.85) ?: return null
    val size = data.length.toInt()
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

private class CameraCallback(
    private val onResult: (CameraPickerResult) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val bytes = image?.toJpegBytes()
        onResult(
            if (bytes == null || bytes.isEmpty()) CameraPickerResult.Failure("The captured image could not be read")
            else CameraPickerResult.Success(bytes)
        )
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(CameraPickerResult.Cancelled)
    }
}

private class ImageCallback(
    private val onResult: (ImagePickerResult) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: run {
            onResult(ImagePickerResult.Cancelled)
            return
        }
        val filename = normalizePickedImageFilename(result.itemProvider.suggestedName)
        result.itemProvider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image"
        ) { data, error ->
            @OptIn(ExperimentalForeignApi::class)
            val bytes = data?.let { nsData ->
                val size = nsData.length.toInt()
                val arr = ByteArray(size)
                arr.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                }
                arr
            }
            dispatch_async(dispatch_get_main_queue()) {
                onResult(
                    if (error != null || bytes == null || bytes.isEmpty()) {
                        ImagePickerResult.Failure("The selected image could not be read")
                    } else {
                        ImagePickerResult.Success(bytes, filename)
                    }
                )
            }
        }
    }
}
