package crucible.lens.platform

internal fun normalizePickedImageFilename(candidate: String?): String {
    val filename = candidate
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.filterNot { it.isISOControl() }
        ?.trim()
        ?.take(255)
    return filename?.takeUnless { it.isBlank() || it == "." || it == ".." } ?: "selected_image"
}
