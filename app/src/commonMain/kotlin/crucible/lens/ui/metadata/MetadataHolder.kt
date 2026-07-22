package crucible.lens.ui.metadata

import kotlinx.serialization.json.JsonObject

object MetadataHolder {
    var metadata: JsonObject? = null
    var isDirty: Boolean = false

    fun put(metadata: JsonObject?) {
        this.metadata = metadata
        this.isDirty = false
    }

    fun take(): JsonObject? {
        isDirty = false
        return metadata
    }
}
