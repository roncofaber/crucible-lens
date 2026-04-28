package crucible.lens

import androidx.compose.runtime.Composable

/**
 * Shared root composable for both Android and iOS.
 * On Android, MainActivity calls this via setContent.
 * On iOS, MainViewController calls this via ComposeUIViewController.
 *
 * Full NavGraph integration is a work in progress — the navigation
 * stack and platform-specific entry points (preferences, camera, etc.)
 * will be wired up as the migration progresses.
 */
@Composable
expect fun App()
