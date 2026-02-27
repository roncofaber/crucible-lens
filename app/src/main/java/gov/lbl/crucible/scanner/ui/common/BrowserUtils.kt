package gov.lbl.crucible.scanner.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opens [url] in the system's default browser, bypassing the app's own deep-link
 * intent filter. The probe URL uses a host that our filter doesn't cover, so
 * resolveActivity reliably returns the actual default browser (requires the <queries>
 * block in AndroidManifest.xml to work on Android 11+).
 */
fun openUrlInBrowser(context: Context, url: String) {
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
    val browserPackage = context.packageManager
        .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.takeIf { it != context.packageName }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (browserPackage != null) intent.setPackage(browserPackage)
    context.startActivity(intent)
}
