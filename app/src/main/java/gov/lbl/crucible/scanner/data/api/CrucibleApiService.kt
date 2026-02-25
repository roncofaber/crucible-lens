package gov.lbl.crucible.scanner.data.api

import gov.lbl.crucible.scanner.data.model.Dataset
import gov.lbl.crucible.scanner.data.model.Project
import gov.lbl.crucible.scanner.data.model.ResourceType
import gov.lbl.crucible.scanner.data.model.Sample
import gov.lbl.crucible.scanner.data.model.Thumbnail
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CrucibleApiService {

    @GET("idtype/{uuid}")
    suspend fun getResourceType(@Path("uuid") uuid: String): Response<ResourceType>

    @GET("samples/{uuid}")
    suspend fun getSample(@Path("uuid") uuid: String): Response<Sample>

    @GET("datasets/{uuid}")
    suspend fun getDataset(@Path("uuid") uuid: String): Response<Dataset>

    @GET("datasets/{uuid}/scientific_metadata")
    suspend fun getScientificMetadata(@Path("uuid") uuid: String): Response<Map<String, Any>>

    @GET("datasets/{uuid}/thumbnails")
    suspend fun getThumbnails(@Path("uuid") uuid: String): Response<List<Thumbnail>>

    @GET("datasets/{uuid}/parents")
    suspend fun getParentDatasets(@Path("uuid") uuid: String): Response<List<Dataset>>

    @GET("datasets/{uuid}/children")
    suspend fun getChildDatasets(@Path("uuid") uuid: String): Response<List<Dataset>>

    @GET("datasets/{uuid}/samples")
    suspend fun getDatasetSamples(@Path("uuid") uuid: String): Response<List<Sample>>

    @GET("samples/{uuid}/parents")
    suspend fun getParentSamples(@Path("uuid") uuid: String): Response<List<Sample>>

    @GET("samples/{uuid}/children")
    suspend fun getChildSamples(@Path("uuid") uuid: String): Response<List<Sample>>

    @GET("projects")
    suspend fun getProjects(): Response<List<Project>>

    @GET("samples")
    suspend fun getSamplesByProject(@Query("project_id") projectId: String): Response<List<Sample>>

    @GET("datasets")
    suspend fun getDatasetsByProject(@Query("project_id") projectId: String): Response<List<Dataset>>
}
