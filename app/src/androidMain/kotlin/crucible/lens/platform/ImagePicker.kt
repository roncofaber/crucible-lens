package crucible.lens.platform

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberImagePicker(onResult: (ImagePickerResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            currentOnResult.value(ImagePickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                currentOnResult.value(ImagePickerResult.Failure("The selected image could not be read"))
                return@rememberLauncherForActivityResult
            }
            val providerFilename = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val filename = normalizePickedImageFilename(providerFilename)
            currentOnResult.value(ImagePickerResult.Success(bytes, filename))
        } catch (_: Exception) {
            currentOnResult.value(ImagePickerResult.Failure("The selected image could not be read"))
        }
    }
    return { launcher.launch("image/*") }
}

@Composable
actual fun rememberCameraPicker(onResult: (CameraPickerResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentOnResult = rememberUpdatedState(onResult)
    var tmpUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tmpFile by remember { mutableStateOf<File?>(null) }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }

    fun clearTemporaryFile() {
        tmpFile?.delete()
        tmpFile = null
        tmpUri = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        try {
            if (!success) {
                currentOnResult.value(CameraPickerResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            val uri = tmpUri
            val bytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
            currentOnResult.value(
                if (bytes == null || bytes.isEmpty()) CameraPickerResult.Failure("The captured image could not be read")
                else CameraPickerResult.Success(bytes)
            )
        } catch (_: Exception) {
            currentOnResult.value(CameraPickerResult.Failure("The captured image could not be read"))
        } finally {
            clearTemporaryFile()
        }
    }

    val launchCamera = {
        clearTemporaryFile()
        try {
            val file = File.createTempFile("thumb_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tmpFile = file
            tmpUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            clearTemporaryFile()
            currentOnResult.value(CameraPickerResult.Failure("The camera could not be opened"))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true
            val recoveryAction = cameraPermissionRecoveryAction(hasRequestedPermission, shouldShowRationale)
            currentOnResult.value(
                CameraPickerResult.PermissionDenied(
                    requiresSettings = recoveryAction == CameraPermissionRecoveryAction.OpenSettings
                )
            )
        }
    }

    return {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            launchCamera()
        } else {
            hasRequestedPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
