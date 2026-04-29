package crucible.lens.data.util

enum class SortField(val label: String) {
    NAME("Name"), MFID("MFID"), DATE("Date")
}

data class SortState(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true
)

fun <T> List<T>.applySortState(
    sortState: SortState,
    name: T.() -> String,
    mfid: T.() -> String,
    date: T.() -> String
): List<T> {
    val selector: (T) -> String = when (sortState.field) {
        SortField.NAME -> name
        SortField.MFID -> mfid
        SortField.DATE -> date
    }
    return if (sortState.ascending) sortedBy { selector(it) }
           else sortedByDescending { selector(it) }
}
