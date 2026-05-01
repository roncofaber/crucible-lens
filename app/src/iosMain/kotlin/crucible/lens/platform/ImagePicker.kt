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

@Composable
actual fun rememberCameraPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val callback = remember { CameraCallback(onResult) }
    return remember {
        {
            val cameraType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            if (!UIImagePickerController.isSourceTypeAvailable(cameraType)) {
                onResult(null)
                return@remember
            }
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: run { onResult(null); return@remember }
            try {
                val picker = UIImagePickerController()
                picker.sourceType = cameraType
                picker.allowsEditing = false
                picker.delegate = callback
                rootVC.presentViewController(picker, animated = true, completion = null)
            } catch (_: Throwable) {
                onResult(null)
            }
        }
    }
}

@Composable
actual fun rememberGalleryPicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val callback = remember { GalleryCallback(onResult) }
    return remember {
        {
            val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: return@remember
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = callback
            rootVC.presentViewController(picker, animated = true, completion = null)
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
    private val onResult: (ByteArray?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(image?.toJpegBytes())
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

private class GalleryCallback(
    private val onResult: (ByteArray?) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: run {
            onResult(null)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier(
            typeIdentifier = "public.image"
        ) { data, _ ->
            @OptIn(ExperimentalForeignApi::class)
            val bytes = data?.let { nsData ->
                val size = nsData.length.toInt()
                val arr = ByteArray(size)
                arr.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                }
                arr
            }
            onResult(bytes)
        }
    }
}
