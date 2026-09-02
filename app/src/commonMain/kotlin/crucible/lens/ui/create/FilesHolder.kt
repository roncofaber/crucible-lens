package crucible.lens.ui.create

import crucible.lens.data.upload.DatasetFileAttachment

object FilesHolder {
    var files: List<DatasetFileAttachment> = emptyList()
    var isDirty: Boolean = false

    fun put(files: List<DatasetFileAttachment>) {
        this.files = files
        isDirty = false
    }

    fun take(): List<DatasetFileAttachment> {
        isDirty = false
        val result = files
        files = emptyList()
        return result
    }
}
