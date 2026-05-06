package crucible.lens.ui.metadata

import kotlinx.serialization.json.JsonObject

object MetadataHolder {
    var metadata: JsonObject? = null
    var isDirty: Boolean = false
    var resourceContext: String = ""

    fun put(metadata: JsonObject?, resourceContext: String = "") {
        this.metadata = metadata
        this.resourceContext = resourceContext
        this.isDirty = false
    }

    fun take(): JsonObject? {
        isDirty = false
        return metadata
    }
}
