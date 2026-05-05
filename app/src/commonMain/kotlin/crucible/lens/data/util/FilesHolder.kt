package crucible.lens.data.util

object FilesHolder {
    var files: List<Pair<ByteArray, Boolean>> = emptyList()
    var isDirty: Boolean = false

    fun put(files: List<Pair<ByteArray, Boolean>>) {
        this.files = files
        isDirty = false
    }

    fun take(): List<Pair<ByteArray, Boolean>> {
        isDirty = false
        val result = files
        files = emptyList()
        return result
    }
}
