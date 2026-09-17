package crucible.lens.data.api

import crucible.lens.data.model.ProjectScope

enum class ResourceVisibility(val apiValue: String) {
    Public("public"),
    Private("private")
}

enum class ResourceAffiliation(val apiValue: String) {
    Owner("owner")
}

enum class PageDirection(val apiValue: String) {
    Ascending("asc"),
    Descending("desc")
}

data class ResourceCollectionQuery(
    val projectId: String? = null,
    val projectMfid: String? = null,
    val projectScope: ProjectScope? = null,
    val ownerId: String? = null,
    val creationTimeGte: String? = null,
    val creationTimeLte: String? = null,
    val visibility: ResourceVisibility? = null,
    val affiliation: ResourceAffiliation? = null,
    val projectMfidIsNull: Boolean? = null
)

data class SampleCollectionQuery(
    val resource: ResourceCollectionQuery = ResourceCollectionQuery(),
    val sampleType: String? = null,
    val sampleTypeIsNull: Boolean? = null
)

data class DatasetCollectionQuery(
    val resource: ResourceCollectionQuery = ResourceCollectionQuery(),
    val measurement: String? = null,
    val instrumentMfid: String? = null,
    val instrumentName: String? = null,
    val dataFormat: String? = null,
    val sessionName: String? = null,
    val measurementIsNull: Boolean? = null,
    val instrumentMfidIsNull: Boolean? = null,
    val dataFormatIsNull: Boolean? = null,
    val sessionNameIsNull: Boolean? = null
)

data class SampleSiblingQuery(
    val anchorMfid: String,
    val projectId: String,
    val direction: PageDirection,
    val sampleType: String? = null,
    val ownerId: String? = null,
    val limit: Int = 40
)

data class DatasetSiblingQuery(
    val anchorMfid: String,
    val projectId: String,
    val direction: PageDirection,
    val measurement: String? = null,
    val instrumentMfid: String? = null,
    val instrumentName: String? = null,
    val dataFormat: String? = null,
    val sessionName: String? = null,
    val ownerId: String? = null,
    val limit: Int = 40
)
