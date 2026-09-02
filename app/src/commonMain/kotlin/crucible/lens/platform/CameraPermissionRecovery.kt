package crucible.lens.platform

internal enum class CameraPermissionRecoveryAction {
    RequestPermission,
    OpenSettings
}

internal fun cameraPermissionRecoveryAction(
    hasRequestedPermission: Boolean,
    shouldShowRationale: Boolean
): CameraPermissionRecoveryAction = when {
    !hasRequestedPermission -> CameraPermissionRecoveryAction.RequestPermission
    shouldShowRationale -> CameraPermissionRecoveryAction.RequestPermission
    else -> CameraPermissionRecoveryAction.OpenSettings
}
