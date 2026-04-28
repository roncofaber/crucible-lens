package crucible.lens.platform

actual fun copyToClipboard(context: PlatformContext, text: String, label: String) {
    // TODO: UIPasteboard.general.string = text
}

actual fun openUrl(context: PlatformContext, url: String) {
    // TODO: UIApplication.shared.open(URL(string: url)!)
}

actual fun shareText(context: PlatformContext, text: String, subject: String) {
    // TODO: UIActivityViewController
}

actual fun showToast(context: PlatformContext, message: String) {
    // TODO: use a Compose-native snackbar approach
}
