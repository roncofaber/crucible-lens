package crucible.lens.ui.common

import crucible.lens.platform.PlatformContext
import crucible.lens.platform.openUrl

fun openUrlInBrowser(context: PlatformContext, url: String) {
    openUrl(context, url)
}
