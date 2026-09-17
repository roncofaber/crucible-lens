package crucible.lens.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

sealed class CrucibleResource {
    abstract val uniqueId: String
    abstract val name: String
    abstract val description: String?
    open val resourceType: String? get() = null
    open val capabilities: ResourceCapabilities? get() = null
}

@Serializable
data class Sample(
    @SerialName("unique_id") override val uniqueId: String,
    @SerialName("sample_name") val sampleName: String? = null,
    @SerialName("description") override val description: String? = null,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("project") val project: ProjectReference? = null,
    @SerialName("project_relation") val projectRelation: ProjectRelation? = null,
    @SerialName("public") val isPublic: Boolean? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("modification_time") val modificationTime: String? = null,
    @SerialName("resource_type") override val resourceType: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    @SerialName("datasets") val datasets: List<DatasetReference>? = null,
    @SerialName("deletion_request") val deletionRequest: JsonObject? = null,
    @SerialName("links") val links: List<ResourceLink>? = null,
    @SerialName("owner") val owner: User? = null,
    @SerialName("capabilities") override val capabilities: ResourceCapabilities? = null
) : CrucibleResource() {
    override val name: String get() = sampleName ?: uniqueId
}

@Serializable
data class InstrumentReference(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("instrument_name") val instrumentName: String
)

@Serializable
data class ProjectReference(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("title") val title: String? = null
)

@Serializable
enum class ProjectRelation {
    @SerialName("assigned") Assigned,
    @SerialName("shared") Shared
}

enum class ProjectScope(val apiValue: String) {
    Assigned("assigned"),
    Shared("shared"),
    All("all")
}

@Serializable
data class Dataset(
    @SerialName("unique_id") override val uniqueId: String,
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("description") override val description: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("instrument") val instrument: InstrumentReference? = null,
    @SerialName("project") val project: ProjectReference? = null,
    @SerialName("project_relation") val projectRelation: ProjectRelation? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("modification_time") val modificationTime: String? = null,
    @SerialName("public") val isPublic: Boolean? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("data_type") val dataType: String? = null,
    @SerialName("size") val size: Long? = null,
    @SerialName("resource_type") override val resourceType: String? = null,
    @SerialName("deletion_request") val deletionRequest: JsonObject? = null,
    @SerialName("links") val links: List<ResourceLink>? = null,
    @SerialName("owner") val owner: User? = null,
    @SerialName("capabilities") override val capabilities: ResourceCapabilities? = null
) : CrucibleResource() {
    override val name: String get() = datasetName ?: uniqueId
}

val Dataset.resolvedInstrumentName: String?
    get() = instrument?.instrumentName?.takeIf { it.isNotBlank() } ?: instrumentName

val Dataset.resolvedInstrumentReference: String?
    get() = instrument?.uniqueId ?: instrumentId

val Dataset.resolvedInstrumentId: String?
    get() = instrument?.instrumentId ?: instrumentId

val Dataset.resolvedProjectName: String?
    get() = project?.title?.takeIf { it.isNotBlank() } ?: project?.projectId ?: projectId

val Dataset.resolvedProjectReference: String?
    get() = project?.uniqueId ?: projectId

val Dataset.resolvedProjectId: String?
    get() = project?.projectId ?: projectId

val Sample.resolvedProjectName: String?
    get() = project?.title?.takeIf { it.isNotBlank() } ?: project?.projectId ?: projectId

val Sample.resolvedProjectReference: String?
    get() = project?.uniqueId ?: projectId

val Sample.resolvedProjectId: String?
    get() = project?.projectId ?: projectId

/** Shared accessor for date-based sorting — Sample and Dataset each declare their own field. */
fun CrucibleResource.creationTimeOrEmpty(): String = when (this) {
    is Sample -> creationTime ?: ""
    is Dataset -> creationTime ?: ""
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
    val id: Int = -1,
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
data class User(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = false,
    @SerialName("role") val role: String? = null,
    @SerialName("capabilities") val capabilities: AccountCapabilities? = null
)

@Serializable
data class AccountCapabilities(
    @SerialName("can_manage_service_accounts") val canManageServiceAccounts: Boolean,
    @SerialName("can_create_project") val canCreateProject: Boolean,
    @SerialName("can_register_instrument") val canRegisterInstrument: Boolean,
    @SerialName("can_create_sample") val canCreateSample: Boolean,
    @SerialName("can_create_dataset") val canCreateDataset: Boolean,
    @SerialName("can_create_for_others") val canCreateForOthers: Boolean
)

@Serializable
data class AccountResponse(
    @SerialName("user_unique_id") val userUniqueId: String? = null,
    @SerialName("user_info") val userInfo: User? = null
)

@Serializable
data class UserSearchResult(
    val username: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null
)

@Serializable
data class ProfileUpdateRequest(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String? = null,
    val username: String? = null
)

@Serializable
enum class ResourceGrantRole {
    @SerialName("viewer") Viewer,
    @SerialName("contributor") Contributor,
    @SerialName("editor") Editor,
    @SerialName("admin") Admin
}

@Serializable
data class ResourceCapabilities(
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_manage_access") val canManageAccess: Boolean = false,
    @SerialName("can_change_status") val canChangeStatus: Boolean = false,
    @SerialName("can_transfer") val canTransfer: Boolean = false,
    @SerialName("max_grant_role") val maxGrantRole: ResourceGrantRole? = null
)

@Serializable
enum class AccessPrincipalType {
    @SerialName("user") User,
    @SerialName("service_account") ServiceAccount,
    @SerialName("project") Project,
    @SerialName("instrument") Instrument,
    @SerialName("public") Public,
    @SerialName("system") System,
    @SerialName("unknown") Unknown
}

@Serializable
enum class AccessPermission {
    @SerialName("viewer") Viewer,
    @SerialName("contributor") Contributor,
    @SerialName("editor") Editor,
    @SerialName("admin") Admin,
    @SerialName("owner") Owner
}

enum class AccessPrincipalKind(val pathValue: String) {
    Users("users"),
    Projects("projects")
}

@Serializable
data class AccessGrant(
    @SerialName("principal_id") val principalId: String,
    @SerialName("principal_type") val principalType: AccessPrincipalType,
    @SerialName("permission") val permission: AccessPermission,
    @SerialName("slug") val slug: String? = null,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class AccessGrantWrite(
    @SerialName("permission") val permission: ResourceGrantRole
)

@Serializable
data class Project(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("title") val title: String? = null,
    @SerialName("organization") val organization: String? = null,
    @SerialName("project_lead_orcid") val projectLeadOrcid: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("lead") val lead: User? = null,
    @SerialName("creation_time") val createdAt: String? = null,
    @SerialName("modification_time") val modifiedAt: String? = null,
    @SerialName("capabilities") val capabilities: ResourceCapabilities? = null
)

@Serializable
data class InstrumentUpdateRequest(
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("instrument_type") val instrumentType: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("other_id") val otherId: String? = null,
    @SerialName("other_id_source") val otherIdSource: String? = null
)

@Serializable
data class InstrumentCreateRequest(
    @SerialName("instrument_id") val instrumentId: String,
    @SerialName("instrument_name") val instrumentName: String,
    @SerialName("location") val location: String,
    @SerialName("instrument_type") val instrumentType: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("other_id") val otherId: String? = null,
    @SerialName("other_id_source") val otherIdSource: String? = null
)

enum class InstrumentStatus(val apiValue: String, val label: String) {
    Active("active", "Active"),
    Maintenance("maintenance", "Maintenance"),
    Decommissioned("decommissioned", "Decommissioned");

    companion object {
        fun fromApi(value: String?): InstrumentStatus? = entries.firstOrNull { it.apiValue == value }
    }
}

@Serializable
data class ProjectUpdateRequest(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("organization") val organization: String? = null
)

@Serializable
data class ProjectCreateRequest(
    @SerialName("project_id") val projectId: String,
    @SerialName("title") val title: String,
    @SerialName("organization") val organization: String,
    @SerialName("project_lead_username") val projectLeadUsername: String? = null,
    @SerialName("status") val status: String = "active"
)

@Serializable
data class TransferOwnershipRequest(
    @SerialName("new_owner") val newOwner: String
)

@Serializable
data class TransferOwnershipResponse(
    @SerialName("resource_id") val resourceId: String,
    @SerialName("previous_owner") val previousOwner: User? = null,
    @SerialName("new_owner") val newOwner: User
)

@Serializable
data class JoinRequest(
    @SerialName("id") val id: Int,
    @SerialName("group_name") val groupName: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("reason") val reason: String? = null,
    @SerialName("status") val status: String,
    @SerialName("request_time") val requestTime: String? = null,
    @SerialName("review_time") val reviewTime: String? = null,
    @SerialName("reviewer_id") val reviewerId: String? = null,
    @SerialName("reviewer_notes") val reviewerNotes: String? = null
)

@Serializable
data class JoinRequestCreate(
    @SerialName("reason") val reason: String? = null
)

@Serializable
data class JoinRequestReview(
    @SerialName("status") val status: String,
    @SerialName("reviewer_notes") val reviewerNotes: String? = null
)

@Serializable
data class ResourceSearchResult(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("resource_type") val resourceType: String? = null,
    val name: String? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("modification_time") val modificationTime: String? = null,
    val rank: Float? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    // Not returned by /resources/metadata/search — that endpoint has no project_id. Populated
    // client-side in name-search mode, where the underlying Sample/Dataset already carries it,
    // so a result row can say which project it belongs to when searching across all of them.
    @SerialName("project_id") val projectId: String? = null,
    @Transient val projectLabel: String? = null
)

@Serializable
data class Instrument(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("instrument_type") val instrumentType: String? = null,
    @SerialName("manufacturer") val manufacturer: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("owner_orcid") val ownerOrcid: String? = null,
    @SerialName("owner") val owner: User? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("other_id") val otherId: String? = null,
    @SerialName("other_id_source") val otherIdSource: String? = null,
    @SerialName("creation_time") val createdAt: String? = null,
    @SerialName("modification_time") val modifiedAt: String? = null,
    @SerialName("resource_type") val resourceType: String? = null,
    @SerialName("scientific_metadata") val scientificMetadata: JsonObject? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("capabilities") val capabilities: ResourceCapabilities? = null
)

@Serializable
data class PaginatedResponse<T>(
    @SerialName("total") val total: Int? = null,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("items") val items: List<T>
)

// ── Write request bodies ──────────────────────────────────────────────────────

@Serializable
data class SampleCreateRequest(
    @SerialName("sample_name") val sampleName: String,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("project_mfid") val projectMfid: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("public") val public: Boolean = false
)

@Serializable
data class DatasetCreateRequest(
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("project_mfid") val projectMfid: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("instrument_name") val instrumentName: String? = null,
    @SerialName("instrument_id") val instrumentId: String? = null,
    @SerialName("instrument_mfid") val instrumentMfid: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("public") val public: Boolean = false,
    @SerialName("data_type") val dataType: String? = null
)

@Serializable
data class ThumbnailCreateRequest(
    @SerialName("thumbnail_name") val thumbnailName: String,
    @SerialName("thumbnail_b64str") val thumbnailB64str: String
)

@Serializable
data class ThumbnailUpdateRequest(
    @SerialName("thumbnail_name") val thumbnailName: String? = null,
    @SerialName("thumbnail_b64str") val thumbnailB64str: String? = null
)

@Serializable
enum class PlatformRole {
    @SerialName("none") None,
    @SerialName("contributor") Contributor,
    @SerialName("support") Support,
    @SerialName("admin") Admin
}

@Serializable
data class ServiceAccountSummary(
    @SerialName("unique_id") val uniqueId: String,
    val username: String,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = true,
    @SerialName("platform_role") val platformRole: PlatformRole
)

@Serializable
data class ServiceAccountKeyStatus(
    val valid: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class ServiceAccountDetail(
    @SerialName("unique_id") val uniqueId: String,
    val username: String,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String? = null,
    @SerialName("is_service_account") val isServiceAccount: Boolean = true,
    @SerialName("platform_role") val platformRole: PlatformRole,
    @SerialName("api_key_status") val apiKeyStatus: ServiceAccountKeyStatus? = null
)

@Serializable
data class ServiceAccountCredential(
    @SerialName("unique_id") val uniqueId: String,
    val username: String,
    @SerialName("is_service_account") val isServiceAccount: Boolean = true,
    @SerialName("api_key") val apiKey: String
)

@Serializable
data class ServiceAccountCreateRequest(val username: String)

@Serializable
data class ServiceAccountRoleUpdateRequest(
    @SerialName("platform_role") val platformRole: PlatformRole
)

@Serializable
data class SampleUpdateRequest(
    @SerialName("sample_name") val sampleName: String? = null,
    @SerialName("sample_type") val sampleType: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("public") val public: Boolean? = null
)

@Serializable
data class DatasetUpdateRequest(
    @SerialName("dataset_name") val datasetName: String? = null,
    @SerialName("measurement") val measurement: String? = null,
    @SerialName("data_format") val dataFormat: String? = null,
    @SerialName("session_name") val sessionName: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("public") val public: Boolean? = null,
    @SerialName("data_type") val dataType: String? = null
)

@Serializable
data class ReassignProjectRequest(
    @SerialName("project_id") val projectId: String
)

@Serializable
data class ReassignProjectResponse(
    @SerialName("resource_id") val resourceId: String,
    @SerialName("previous_project_id") val previousProjectId: String? = null,
    @SerialName("new_project_id") val newProjectId: String
)

@Serializable
data class HealthStatus(
    @SerialName("status") val status: String,
    @SerialName("build") val build: HealthBuildInfo,
    @SerialName("database") val database: HealthDatabaseStatus
)

@Serializable
data class HealthBuildInfo(
    @SerialName("api_version") val apiVersion: String,
    @SerialName("git_commit") val gitCommit: String? = null,
    @SerialName("branch") val branch: String? = null
)

@Serializable
data class HealthDatabaseStatus(
    @SerialName("status") val status: String,
    @SerialName("latency_ms") val latencyMs: Double? = null,
    @SerialName("schema_revisions") val schemaRevisions: List<String> = emptyList()
)

@Serializable
enum class DatasetFacetField(val apiValue: String) {
    @SerialName("session") Session("session"),
    @SerialName("measurement") Measurement("measurement"),
    @SerialName("data_format") DataFormat("data_format"),
    @SerialName("owner") Owner("owner"),
    @SerialName("instrument") Instrument("instrument"),
    @SerialName("project") Project("project")
}

@Serializable
enum class SampleFacetField(val apiValue: String) {
    @SerialName("sample_type") SampleType("sample_type"),
    @SerialName("owner") Owner("owner"),
    @SerialName("project") Project("project")
}

@Serializable
data class FacetBucket(
    @SerialName("value") val value: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("count") val count: Int
)

@Serializable
data class FacetResponse(
    @SerialName("field") val field: String,
    @SerialName("limit") val limit: Int,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("items") val items: List<FacetBucket>
)

@Serializable
data class DatasetInstrumentAssignRequest(
    @SerialName("instrument_mfid") val instrumentMfid: String
)

@Serializable
data class DatasetInstrumentAssignment(
    @SerialName("dataset_mfid") val datasetMfid: String,
    @SerialName("instrument") val instrument: InstrumentReference,
    @SerialName("previous_instrument") val previousInstrument: InstrumentReference? = null,
    @SerialName("previous_instrument_name") val previousInstrumentName: String? = null
)

// ── GCS resumable upload models ──────────────────────────────────────────────

@Serializable
data class UploadInitiateRequest(
    val filename: String,
    val size: Long,
    @SerialName("sha256_hash") val sha256Hash: String
)

@Serializable
data class UploadInitiateResponse(
    @SerialName("upload_id") val uploadId: String? = null,
    @SerialName("resumable_uri") val resumableUri: String? = null,
    @SerialName("chunk_size_hint") val chunkSizeHint: Int = 8 * 1024 * 1024,
    @SerialName("existing_file") val existingFile: AssociatedFile? = null
)

@Serializable
data class UploadCompleteRequest(
    @SerialName("upload_id") val uploadId: String,
    @SerialName("sha256_hash") val sha256Hash: String
)

@Serializable
data class AssociatedFile(
    val mfid: String,
    val filename: String,
    @SerialName("storage_path") val storagePath: String? = null,
    val size: Long? = null,
    @SerialName("sha256_hash") val sha256Hash: String? = null
)

@Serializable
data class FileDownloadLinkResponse(
    val url: String,
    @SerialName("expires_in") val expiresIn: Int = 3600
)
