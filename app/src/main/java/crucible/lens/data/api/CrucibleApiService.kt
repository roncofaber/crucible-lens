package crucible.lens.data.api

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.DatasetCreateRequest
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.MetadataSearchResult
import crucible.lens.data.model.DatasetUpdateRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.ResourceType
import crucible.lens.data.model.Sample
import crucible.lens.data.model.SampleCreateRequest
import crucible.lens.data.model.SampleUpdateRequest
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.data.model.AccountResponse
import crucible.lens.data.model.UserLead
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CrucibleApiService {

    // ── Read ─────────────────────────────────────────────────────────────────

    @GET("account")
    suspend fun getAccount(): Response<AccountResponse>

    @GET("idtype/{uuid}")
    suspend fun getResourceType(@Path("uuid") uuid: String): Response<ResourceType>

    @GET("samples/{uuid}")
    suspend fun getSample(
        @Path("uuid") uuid: String,
        @Query("include_links") includeLinks: Boolean = true
    ): Response<Sample>

    @GET("datasets/{uuid}")
    suspend fun getDataset(
        @Path("uuid") uuid: String,
        @Query("include_links") includeLinks: Boolean = true,
        @Query("include_metadata") includeMetadata: Boolean = false
    ): Response<Dataset>

    @GET("datasets/{uuid}/thumbnails")
    suspend fun getThumbnails(@Path("uuid") uuid: String): Response<List<Thumbnail>>

    @GET("instruments")
    suspend fun getInstruments(): Response<List<Instrument>>

    @GET("instruments/{id}")
    suspend fun getInstrument(@Path("id") id: String): Response<Instrument>

    @GET("datasets")
    suspend fun getDatasetsByInstrument(
        @Query("instrument_name") instrumentName: String,
        @Query("limit") limit: Int = 100000
    ): Response<List<Dataset>>

    @GET("projects")
    suspend fun getProjects(): Response<List<Project>>

    @GET("samples")
    suspend fun getSamplesByProject(
        @Query("project_id") projectId: String,
        @Query("limit") limit: Int = 100000
    ): Response<List<Sample>>

    @GET("datasets")
    suspend fun getDatasetsByProject(
        @Query("project_id") projectId: String,
        @Query("include_metadata") includeMetadata: Boolean = false,
        @Query("limit") limit: Int = 100000
    ): Response<List<Dataset>>

    @GET("datasets")
    suspend fun getFilteredDatasets(
        @Query("project_id") projectId: String? = null,
        @Query("measurement") measurement: String? = null,
        @Query("instrument_name") instrumentName: String? = null,
        @Query("data_format") dataFormat: String? = null,
        @Query("session_name") sessionName: String? = null,
        @Query("owner_orcid") ownerOrcid: String? = null,
        @Query("creation_time_gte") creationTimeGte: String? = null,
        @Query("creation_time_lte") creationTimeLte: String? = null,
        @Query("limit") limit: Int = 100000
    ): Response<List<Dataset>>

    @GET("samples")
    suspend fun getFilteredSamples(
        @Query("project_id") projectId: String? = null,
        @Query("sample_type") sampleType: String? = null,
        @Query("owner_orcid") ownerOrcid: String? = null,
        @Query("creation_time_gte") creationTimeGte: String? = null,
        @Query("creation_time_lte") creationTimeLte: String? = null,
        @Query("limit") limit: Int = 100000
    ): Response<List<Sample>>

    // ── Write ────────────────────────────────────────────────────────────────

    @POST("samples")
    suspend fun createSample(@Body request: SampleCreateRequest): Response<Sample>

    @POST("datasets")
    suspend fun createDataset(@Body request: DatasetCreateRequest): Response<Dataset>

    @POST("datasets/{uuid}/thumbnails")
    suspend fun addThumbnail(
        @Path("uuid") uuid: String,
        @Body request: ThumbnailCreateRequest
    ): Response<Thumbnail>

    @POST("deletion_requests")
    suspend fun requestDeletion(
        @Query("resource_id") resourceId: String,
        @Query("reason") reason: String? = null
    ): Response<Unit>

    @GET("projects/{projectId}/users")
    suspend fun getProjectUsers(
        @Path("projectId") projectId: String
    ): Response<List<crucible.lens.data.model.UserLead>>

    @GET("scientific_metadata/search")
    suspend fun searchScientificMetadata(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50
    ): Response<List<MetadataSearchResult>>

    // ── Linking ──────────────────────────────────────────────────────────────

    @POST("samples/{parent}/children/{child}")
    suspend fun linkSamples(
        @Path("parent") parentUuid: String,
        @Path("child") childUuid: String
    ): Response<Unit>

    @POST("datasets/{parent}/children/{child}")
    suspend fun linkDatasets(
        @Path("parent") parentUuid: String,
        @Path("child") childUuid: String
    ): Response<Unit>

    @POST("datasets/{dataset}/samples/{sample}")
    suspend fun linkDatasetSample(
        @Path("dataset") datasetUuid: String,
        @Path("sample") sampleUuid: String
    ): Response<Unit>

    @PATCH("samples/{uuid}")
    suspend fun updateSample(
        @Path("uuid") uuid: String,
        @Body request: SampleUpdateRequest
    ): Response<Sample>

    @PATCH("datasets/{uuid}")
    suspend fun updateDataset(
        @Path("uuid") uuid: String,
        @Body request: DatasetUpdateRequest
    ): Response<Dataset>

    // ── Unlinking ─────────────────────────────────────────────────────────────

    @DELETE("samples/{parent}/children/{child}")
    suspend fun unlinkSamples(
        @Path("parent") parentUuid: String,
        @Path("child") childUuid: String
    ): Response<Unit>

    @DELETE("datasets/{parent}/children/{child}")
    suspend fun unlinkDatasets(
        @Path("parent") parentUuid: String,
        @Path("child") childUuid: String
    ): Response<Unit>

    @DELETE("datasets/{dataset}/samples/{sample}")
    suspend fun unlinkDatasetSample(
        @Path("dataset") datasetUuid: String,
        @Path("sample") sampleUuid: String
    ): Response<Unit>
}
