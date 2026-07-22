package crucible.lens.platform

import crucible.lens.data.model.CrucibleResource
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual fun copyToClipboard(context: PlatformContext, text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun openUrl(context: PlatformContext, url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
}

actual fun shareText(context: PlatformContext, text: String, subject: String) {
    val items = listOf(text)
    val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(controller, animated = true, completion = null)
}

actual fun showToast(context: PlatformContext, message: String) {
    // iOS has no native Toast — posted to ToastBus, rendered by ToastHost in NavGraph
    ToastBus.post(message)
}

actual fun currentIsoDateTime(): String {
    val now = Clock.System.now()
    return now.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
}

actual fun shareResource(
    context: PlatformContext,
    resource: CrucibleResource,
    shareText: String,
    subject: String,
    darkTheme: Boolean,
    bannerColorValue: Long
) = shareText(context, shareText, subject)
