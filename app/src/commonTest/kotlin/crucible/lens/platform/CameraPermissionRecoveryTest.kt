package crucible.lens.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraPermissionRecoveryTest {
    @Test
    fun requestsPermissionBeforeFirstAttempt() {
        assertEquals(
            CameraPermissionRecoveryAction.RequestPermission,
            cameraPermissionRecoveryAction(
                hasRequestedPermission = false,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun retriesWhenAndroidRecommendsRationale() {
        assertEquals(
            CameraPermissionRecoveryAction.RequestPermission,
            cameraPermissionRecoveryAction(
                hasRequestedPermission = true,
                shouldShowRationale = true
            )
        )
    }

    @Test
    fun opensSettingsWhenPermissionCannotBeRequestedAgain() {
        assertEquals(
            CameraPermissionRecoveryAction.OpenSettings,
            cameraPermissionRecoveryAction(
                hasRequestedPermission = true,
                shouldShowRationale = false
            )
        )
    }
}
