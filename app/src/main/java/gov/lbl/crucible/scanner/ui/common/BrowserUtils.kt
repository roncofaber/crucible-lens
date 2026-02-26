package gov.lbl.crucible.scanner.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opens [url] in the system's default browser, explicitly bypassing the app's own
 * deep-link intent filter. This is done by resolving the default browser package via
 * a neutral "https://" intent (which the app doesn't handle) and targeting it directly.
 */
fun openUrlInBrowser(context: Context, url: String) {
    val browserPackage = context.packageManager
        .resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (browserPackage != null) intent.setPackage(browserPackage)
    context.startActivity(intent)
}
