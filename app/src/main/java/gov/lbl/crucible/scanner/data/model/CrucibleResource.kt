package gov.lbl.crucible.scanner.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

sealed class CrucibleResource {
    abstract val uniqueId: String
    abstract val name: String
    abstract val description: String?
}

@JsonClass(generateAdapter = true)
data class Sample(
    @Json(name = "unique_id") override val uniqueId: String,
    @Json(name = "sample_name") override val name: String,
    @Json(name = "description") override val description: String? = null,
    @Json(name = "sample_type") val sampleType: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "datasets") val datasets: List<DatasetReference>? = null,
    @Json(name = "parent_samples") val parentSamples: List<SampleReference>? = null,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "creation_time") val createdAt: String? = null,
    @Json(name = "id") val internalId: Int? = null,
    var childSamples: List<SampleReference>? = null
) : CrucibleResource()

@JsonClass(generateAdapter = true)
data class Dataset(
    @Json(name = "unique_id") override val uniqueId: String,
    @Json(name = "dataset_name") override val name: String,
    @Json(name = "description") override val description: String? = null,
    @Json(name = "measurement") val measurement: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "instrument_name") val instrumentName: String? = null,
    @Json(name = "owner_orcid") val ownerOrcid: String? = null,
    @Json(name = "data_format") val dataFormat: String? = null,
    @Json(name = "samples") val samples: List<SampleReference>? = null,
    @Json(name = "scientific_metadata") val scientificMetadata: Map<String, Any?>? = null,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "creation_time") val createdAt: String? = null,
    @Json(name = "public") val isPublic: Boolean? = null,
    @Json(name = "source_folder") val sourceFolder: String? = null,
    @Json(name = "instrument_id") val instrumentId: Int? = null,
    @Json(name = "session_name") val sessionName: String? = null,
    @Json(name = "id") val internalId: Int? = null,
    @Json(name = "json_link") val jsonLink: String? = null,
    @Json(name = "file_to_upload") val fileToUpload: String? = null,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "owner_user_id") val ownerUserId: Int? = null,
    @Json(name = "sha256_hash_file_to_upload") val sha256Hash: String? = null,
    var parentDatasets: List<DatasetReference>? = null,
    var childDatasets: List<DatasetReference>? = null
) : CrucibleResource()

@JsonClass(generateAdapter = true)
data class DatasetReference(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "dataset_name") val datasetName: String? = null,
    @Json(name = "measurement") val measurement: String? = null
)

@JsonClass(generateAdapter = true)
data class SampleReference(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "sample_name") val sampleName: String? = null
)

@JsonClass(generateAdapter = true)
data class Thumbnail(
    @Json(name = "thumbnail_b64str") val thumbnailB64: String,
    @Json(name = "dataset_id") val datasetId: String? = null
)

@JsonClass(generateAdapter = true)
data class ResourceType(
    @Json(name = "object_type") val objectType: String
)

@JsonClass(generateAdapter = true)
data class Project(
    @Json(name = "project_id") val projectId: String,
    @Json(name = "project_name") val projectName: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "project_lead_email") val projectLeadEmail: String? = null
)
