package crucible.lens.data.util

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.Project
import crucible.lens.data.model.Sample
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Project.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return (title?.lowercase()?.contains(q) == true) ||
        projectId.lowercase().contains(q) ||
        (organization?.lowercase()?.contains(q) == true) ||
        (lead?.username?.lowercase()?.contains(q) == true) ||
        (lead?.email?.lowercase()?.contains(q) == true) ||
        (status?.lowercase()?.contains(q) == true)
}

fun Sample.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (sampleType?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (keywords?.any { it.lowercase().contains(q) } == true)
}

fun Dataset.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return name.lowercase().contains(q) ||
        (measurement?.lowercase()?.contains(q) == true) ||
        (instrumentName?.lowercase()?.contains(q) == true) ||
        (sessionName?.lowercase()?.contains(q) == true) ||
        (projectId?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q) ||
        (timestamp?.lowercase()?.contains(q) == true) ||
        (dataFormat?.lowercase()?.contains(q) == true) ||
        (ownerOrcid?.lowercase()?.contains(q) == true) ||
        (sourceFolder?.lowercase()?.contains(q) == true) ||
        (fileToUpload?.lowercase()?.contains(q) == true) ||
        (sha256Hash?.lowercase()?.contains(q) == true) ||
        (keywords?.any { it.lowercase().contains(q) } == true) ||
        (scientificMetadata?.matchesSearch(q) == true)
}

fun Instrument.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    return (instrumentName?.lowercase()?.contains(q) == true) ||
        (instrumentType?.lowercase()?.contains(q) == true) ||
        (manufacturer?.lowercase()?.contains(q) == true) ||
        (model?.lowercase()?.contains(q) == true) ||
        (location?.lowercase()?.contains(q) == true) ||
        (owner?.lowercase()?.contains(q) == true) ||
        uniqueId.lowercase().contains(q)
}

fun JsonObject.matchesSearch(query: String): Boolean =
    entries.any { (key, value) ->
        key.lowercase().contains(query) ||
        runCatching { value.jsonPrimitive.content.lowercase().contains(query) }.getOrDefault(false)
    }
