package crucible.lens.ui.navigation

import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import crucible.lens.data.util.isMfidReference

sealed interface DeepLinkTarget {
    data class Project(val projectReference: String) : DeepLinkTarget
    data class Resource(val resourceReference: String) : DeepLinkTarget
}

private val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val projectSlugPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{2,24}$")
private fun String.isResourceReference(): Boolean = isMfidReference(this) || matches(uuidPattern)
private fun String.isProjectReference(): Boolean = isResourceReference() || matches(projectSlugPattern)

fun parseDeepLink(url: String): DeepLinkTarget? {
    val parsedUrl = parseUrl(url) ?: return null
    if (
        parsedUrl.protocol != URLProtocol.HTTPS ||
        parsedUrl.port != URLProtocol.HTTPS.defaultPort ||
        !parsedUrl.host.equals("crucible.lbl.gov", ignoreCase = true)
    ) {
        return null
    }
    if (parsedUrl.user != null || parsedUrl.password != null) return null

    val segments = parsedUrl.segments
    if (segments.firstOrNull() != "explore") return null

    return when {
        segments.size == 2 && segments[1].isProjectReference() -> DeepLinkTarget.Project(segments[1])
        segments.size == 4 &&
            segments[1].isProjectReference() &&
            segments[2] in setOf("samples", "datasets") &&
            segments[3].isResourceReference() -> DeepLinkTarget.Resource(segments[3])
        else -> null
    }
}
