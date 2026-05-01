package crucible.lens.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

sealed class CrucibleResource {
    abstract val uniqueId: String
    abstract val name: String
    abstract val description: String?
    // keywords are served by a separate endpoint; inline here for future API support
    open val keywords: List<String>? get() = null
}

@Serializable
data class Sample(
    @SerialName("unique_id") override val uniqueId: String,
    @SerialName("sample_name") val sampleName: String? = null,
    @SerialName("description") override val description: String? = null,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("modification_time") val modificationTime: String? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    @SerialName("datasets") val datasets: List<DatasetReference>? = null,
    @SerialName("deletion_request") val deletionRequest: JsonObject? = null,
    @SerialName("links") val links: List<ResourceLink>? = null
) : CrucibleResource() {
    override val name: String get() = sampleName ?: uniqueId
}

@Serializable
data class Dataset(
    @SerialName("unique_id") override val uniqueId: String,
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("description") override val description: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("modification_time") val modificationTime: String? = null,
    @SerialName("public") val isPublic: Boolean? = null,
    @SerialName("source_folder") val sourceFolder: String? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("data_type") val dataType: String? = null,
    @SerialName("file_to_upload") val fileToUpload: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("sha256_hash_file_to_upload") val sha256Hash: String? = null,
    @SerialName("ingestion_githash") val ingestionGithash: String? = null,
    @SerialName("ingestion_class") val ingestionClass: String? = null,
    @SerialName("keywords") override val keywords: List<String>? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    @SerialName("deletion_request") val deletionRequest: JsonObject? = null,
    @SerialName("links") val links: List<ResourceLink>? = null
) : CrucibleResource() {
    override val name: String get() = datasetName ?: uniqueId
}

@Serializable
data class ResourceLink(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("resource_type") val resourceType: String,
    @SerialName("name") val name: String? = null,
    @SerialName("relationship") val relationship: String = "associated"
)

@Serializable
data class DatasetReference(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("id") val internalId: Int? = null
)

@Serializable
data class Thumbnail(
    @SerialName("thumbnail_b64str") val thumbnailB64: String,
    @SerialName("thumbnail_name") val thumbnailName: String? = null,
    @SerialName("dataset_id") val datasetId: Int? = null
)

@Serializable
data class ResourceType(
    @SerialName("resource_type") val objectType: String? = null,
    @SerialName("object_type") val objectTypeLegacy: String? = null
) {
    val resolvedType: String? get() = objectType ?: objectTypeLegacy
}

@Serializable
data class GraphNode(
    @SerialName("id") val id: String,
    @SerialName("sample_name") val sampleName: String? = null,
    @SerialName("dataset_name") val datasetName: String? = null
)

@Serializable
data class GraphLink(
    @SerialName("source") val source: String,
    @SerialName("target") val target: String
)

@Serializable
data class UserLead(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = false
)

@Serializable
data class AccountResponse(
    @SerialName("user_unique_id") val userUniqueId: String? = null,
    @SerialName("user_info") val userInfo: UserLead? = null
)

@Serializable
data class Project(
    @SerialName("project_id") val projectId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("organization") val organization: String? = null,
    @SerialName("project_lead_orcid") val projectLeadOrcid: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("lead") val lead: UserLead? = null
)

@Serializable
data class MetadataSearchResult(
    @SerialName("dataset_mfid") val datasetMfid: String,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null
)

@Serializable
data class Instrument(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("instrument_type") val instrumentType: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("owner") val owner: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("creation_time") val createdAt: String? = null,
    @SerialName("modification_time") val modifiedAt: String? = null,
    @SerialName("resource_type") val resourceType: String? = null
)

@Serializable
data class PaginatedResponse<T>(
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int,
    @SerialName("items") val items: List<T>
)

// ── Write request bodies ──────────────────────────────────────────────────────

@Serializable
data class SampleCreateRequest(
    @SerialName("sample_name") val sampleName: String,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

@Serializable
data class DatasetCreateRequest(
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("public") val public: Boolean = false,
    @SerialName("data_type") val dataType: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: Map<String, String>? = null
)

@Serializable
data class ThumbnailCreateRequest(
    @SerialName("thumbnail_name") val thumbnailName: String,
    @SerialName("thumbnail_b64str") val thumbnailB64str: String
)

@Serializable
data class SampleUpdateRequest(
    @SerialName("sample_name") val sampleName: String? = null,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("project_id") val projectId: String? = null
)

@Serializable
data class DatasetUpdateRequest(
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("public") val public: Boolean? = null,
    @SerialName("data_type") val dataType: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: Map<String, String>? = null
)

@Serializable
data class HealthStatus(
    val status: String,                 // "ok" | "degraded"
    val db: String? = null,             // "ok" | "error"
    @SerialName("db_ms") val dbMs: Float? = null,
    val version: String? = null
)
