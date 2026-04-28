package crucible.lens.ui.common

import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl

/**
 * Opens [url] in the system's default browser. On Android, this opens in the default browser.
 * On iOS, this opens in Safari or the app's default browser.
 */
fun openUrlInBrowser(url: String) {
    val context = getPlatformContext()
    openUrl(context, url)
}
