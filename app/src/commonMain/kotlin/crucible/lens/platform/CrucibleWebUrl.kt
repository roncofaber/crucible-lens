package crucible.lens.platform

fun buildCrucibleWebUrl(baseUrl: String, vararg pathSegments: String): String {
    if (baseUrl.isBlank()) return ""
    val normalizedBaseUrl = baseUrl.trimEnd('/')
    if (pathSegments.isEmpty()) return normalizedBaseUrl
    return pathSegments.joinToString(prefix = "$normalizedBaseUrl/", separator = "/") { it.trim('/') }
}
