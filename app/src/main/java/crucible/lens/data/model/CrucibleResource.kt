package crucible.lens.data.model

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
    @Json(name = "owner_orcid") val ownerOrcid: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "datasets") val datasets: List<DatasetReference>? = null,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "date_created") val createdAt: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "owner_user_id") val ownerUserId: Int? = null,
    @Json(name = "creation_time") val creationTime: String? = null,
    @Json(name = "modification_time") val modificationTime: String? = null,
    @Json(name = "id") val internalId: Int? = null,
    @Json(name = "resource_type") val resourceType: String? = null,
    @Json(name = "deletion_request") val deletionRequest: Map<String, Any?>? = null,
    @Json(name = "links") val links: List<ResourceLink>? = null
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
    @Json(name = "scientific_metadata") val scientificMetadata: Map<String, Any?>? = null,
    @Json(name = "keywords") val keywords: List<String>? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "creation_time") val creationTime: String? = null,
    @Json(name = "modification_time") val modificationTime: String? = null,
    @Json(name = "public") val isPublic: Boolean? = null,
    @Json(name = "source_folder") val sourceFolder: String? = null,
    @Json(name = "session_name") val sessionName: String? = null,
    @Json(name = "data_type") val dataType: String? = null,
    @Json(name = "file_to_upload") val fileToUpload: String? = null,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "sha256_hash_file_to_upload") val sha256Hash: String? = null,
    @Json(name = "resource_type") val resourceType: String? = null,
    @Json(name = "deletion_request") val deletionRequest: Map<String, Any?>? = null,
    @Json(name = "links") val links: List<ResourceLink>? = null
) : CrucibleResource()

@JsonClass(generateAdapter = true)
data class ResourceLink(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "resource_type") val resourceType: String,
    @Json(name = "name") val name: String,
    @Json(name = "relationship") val relationship: String  // "parent", "child", "associated"
)

@JsonClass(generateAdapter = true)
data class DatasetReference(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "dataset_name") val datasetName: String? = null,
    @Json(name = "measurement") val measurement: String? = null,
    @Json(name = "id") val internalId: Int? = null
)

@JsonClass(generateAdapter = true)
data class SampleReference(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "sample_name") val sampleName: String? = null
)

@JsonClass(generateAdapter = true)
data class Thumbnail(
    @Json(name = "thumbnail_b64str") val thumbnailB64: String,
    @Json(name = "dataset_id") val datasetId: Int? = null
)

@JsonClass(generateAdapter = true)
data class ResourceType(
    @Json(name = "resource_type") val objectType: String? = null,
    @Json(name = "object_type") val objectTypeLegacy: String? = null
) {
    val resolvedType: String? get() = objectType ?: objectTypeLegacy
}

@JsonClass(generateAdapter = true)
data class GraphNode(
    @Json(name = "id") val id: String,
    @Json(name = "sample_name") val sampleName: String? = null,
    @Json(name = "dataset_name") val datasetName: String? = null
)

@JsonClass(generateAdapter = true)
data class GraphLink(
    @Json(name = "source") val source: String,
    @Json(name = "target") val target: String
)

@JsonClass(generateAdapter = true)
data class GraphResponse(
    @Json(name = "nodes") val nodes: List<GraphNode> = emptyList(),
    // NetworkX < 3.4 uses "links", >= 3.4 uses "edges" — accept both
    @Json(name = "links") val links: List<GraphLink> = emptyList(),
    @Json(name = "edges") val edges: List<GraphLink> = emptyList()
) {
    val allLinks: List<GraphLink> get() = links.ifEmpty { edges }
}

@JsonClass(generateAdapter = true)
data class UserLead(
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "unique_id") val uniqueId: String? = null,
    @Json(name = "is_service_account") val isServiceAccount: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AccountResponse(
    @Json(name = "user_unique_id") val userUniqueId: String? = null,
    @Json(name = "user_info") val userInfo: UserLead? = null
)

@JsonClass(generateAdapter = true)
data class Project(
    @Json(name = "project_id") val projectId: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "organization") val organization: String? = null,
    @Json(name = "project_lead_orcid") val projectLeadOrcid: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "lead") val lead: UserLead? = null
)

@JsonClass(generateAdapter = true)
data class MetadataSearchResult(
    @Json(name = "dataset_mfid") val datasetMfid: String,
    @Json(name = "scientific_metadata") val scientificMetadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class Instrument(
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "instrument_name") val instrumentName: String? = null,
    @Json(name = "instrument_type") val instrumentType: String? = null,
    @Json(name = "manufacturer") val manufacturer: String? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "owner") val owner: String? = null,
    @Json(name = "location") val location: String? = null,
    @Json(name = "creation_time") val createdAt: String? = null,
    @Json(name = "modification_time") val modifiedAt: String? = null,
    @Json(name = "resource_type") val resourceType: String? = null
)

// ── Write request bodies ──────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SampleCreateRequest(
    @Json(name = "sample_name") val sampleName: String,
    @Json(name = "sample_type") val sampleType: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetCreateRequest(
    @Json(name = "dataset_name") val datasetName: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "measurement") val measurement: String? = null,
    @Json(name = "instrument_name") val instrumentName: String? = null,
    @Json(name = "data_format") val dataFormat: String? = null,
    @Json(name = "session_name") val sessionName: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "public") val public: Boolean = false,
    @Json(name = "data_type") val dataType: String? = null,
    @Json(name = "scientific_metadata") val scientificMetadata: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class ThumbnailCreateRequest(
    @Json(name = "thumbnail_name") val thumbnailName: String,
    @Json(name = "thumbnail_b64str") val thumbnailB64str: String
)

@JsonClass(generateAdapter = true)
data class SampleUpdateRequest(
    @Json(name = "sample_name") val sampleName: String? = null,
    @Json(name = "sample_type") val sampleType: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "project_id") val projectId: String? = null
)

@JsonClass(generateAdapter = true)
data class DatasetUpdateRequest(
    @Json(name = "dataset_name") val datasetName: String? = null,
    @Json(name = "measurement") val measurement: String? = null,
    @Json(name = "instrument_name") val instrumentName: String? = null,
    @Json(name = "data_format") val dataFormat: String? = null,
    @Json(name = "session_name") val sessionName: String? = null,
    @Json(name = "timestamp") val timestamp: String? = null,
    @Json(name = "project_id") val projectId: String? = null,
    @Json(name = "public") val public: Boolean? = null,
    @Json(name = "data_type") val dataType: String? = null,
    @Json(name = "scientific_metadata") val scientificMetadata: Map<String, String>? = null
)
