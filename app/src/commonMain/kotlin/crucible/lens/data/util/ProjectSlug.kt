package crucible.lens.data.util

private val RESOURCE_SLUG_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{2,24}$")
private val RESERVED_PROJECT_SLUGS = setOf("admin", "read_only", "public")

fun projectSlugValidationError(value: String): String? {
    val slug = value.trim()
    resourceSlugFormatValidationError(slug)?.let { return it }
    if (slug.lowercase() in RESERVED_PROJECT_SLUGS) {
        return "That project ID is reserved"
    }
    return null
}

fun instrumentSlugValidationError(value: String): String? = resourceSlugFormatValidationError(value.trim())

private fun resourceSlugFormatValidationError(value: String): String? =
    if (RESOURCE_SLUG_PATTERN.matches(value)) null
    else "Use 3 to 25 letters, numbers, underscores, or hyphens, starting with a letter or number"
