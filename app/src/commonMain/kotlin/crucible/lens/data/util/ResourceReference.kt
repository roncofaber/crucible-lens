package crucible.lens.data.util

private val mfidPattern = Regex("^[0-9a-hjkmnp-tv-z]{26}$")

fun isMfidReference(reference: String): Boolean = reference.matches(mfidPattern)
