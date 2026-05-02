package crucible.lens.data.util

object MetadataHolder {
    var entries: List<Pair<String, String>> = emptyList()
    var isDirty: Boolean = false
    var resourceContext: String = ""

    fun put(entries: List<Pair<String, String>>, resourceContext: String = "") {
        this.entries = entries
        this.resourceContext = resourceContext
        this.isDirty = false
    }

    fun take(): List<Pair<String, String>> {
        isDirty = false
        return entries
    }
}
