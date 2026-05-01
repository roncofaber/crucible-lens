package crucible.lens.data.util

/**
 * In-memory clipboard for metadata entries between the create/edit screens
 * and the full-screen MetadataEditorScreen.
 *
 * Pattern:
 *   1. Caller sets [entries] and navigates to MetadataEditorScreen.
 *   2. Editor updates [entries] and pops back.
 *   3. Caller reads [entries] via savedStateHandle signal.
 */
object MetadataHolder {
    var entries: List<Pair<String, String>> = emptyList()
    var isDirty: Boolean = false

    fun put(entries: List<Pair<String, String>>) {
        this.entries = entries
        this.isDirty = false
    }

    fun take(): List<Pair<String, String>> {
        isDirty = false
        return entries
    }
}
