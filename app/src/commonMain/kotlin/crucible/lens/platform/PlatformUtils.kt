package crucible.lens.platform

import crucible.lens.data.model.CrucibleResource

expect fun copyToClipboard(context: PlatformContext, text: String, label: String = "text")
expect fun openUrl(context: PlatformContext, url: String)
expect fun shareText(context: PlatformContext, text: String, subject: String = "")
expect fun showToast(context: PlatformContext, message: String)
expect fun currentIsoDateTime(): String

/**
 * Shares a resource with a generated card image on Android and plain text on iOS.
 * [bannerColorValue] is the Compose Color.value (ULong packed) cast to Long.
 */
expect fun shareResource(
    context: PlatformContext,
    resource: CrucibleResource,
    shareText: String,
    subject: String,
    darkTheme: Boolean,
    bannerColorValue: Long
)
