package crucible.lens.platform

expect fun copyToClipboard(context: PlatformContext, text: String, label: String = "text")
expect fun openUrl(context: PlatformContext, url: String)
expect fun shareText(context: PlatformContext, text: String, subject: String = "")
expect fun showToast(context: PlatformContext, message: String)
expect fun currentIsoDateTime(): String
