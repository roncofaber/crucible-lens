package crucible.lens.ui.common

import crucible.lens.platform.PlatformContext
import crucible.lens.platform.showToast

/**
 * Central notification function for action feedback (pin, archive, sort, etc.).
 * All transient user-facing messages should go through here so that a future
 * "suppress action feedback" preference can be wired in a single place.
 */
fun showFeedback(context: PlatformContext, message: String) {
    showToast(context, message)
}
