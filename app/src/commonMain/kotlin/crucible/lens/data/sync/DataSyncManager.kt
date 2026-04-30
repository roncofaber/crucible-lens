package crucible.lens.data.sync

import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.util.fetchProjectData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Preloads all data into the in-memory cache so search works instantly
 * from any screen without making API calls. Call [syncAll] once the API
 * key becomes available and again on explicit refresh.
 *
 * fetchProjectData() is idempotent — it skips projects already cached.
 */
object DataSyncManager {

    suspend fun syncAll() {
        coroutineScope {
            // Load projects and instruments in parallel
            val projectsDeferred = async {
                (ApiClient.service.getProjects() as? ApiResult.Success)?.data
                    ?.also { CacheManager.cacheProjects(it) }
                    ?: CacheManager.getProjects()
                    ?: emptyList()
            }
            val instrumentsDeferred = async {
                (ApiClient.service.getInstruments() as? ApiResult.Success)?.data
                    ?.also { CacheManager.cacheInstruments(it) }
            }

            val projects = projectsDeferred.await()
            instrumentsDeferred.await()

            // Load samples + datasets for every project in batches
            projects.chunked(5).forEach { batch ->
                coroutineScope {
                    batch.map { project ->
                        async {
                            try { fetchProjectData(project.projectId) }
                            catch (_: Exception) { }
                        }
                    }.awaitAll()
                }
            }
        }
    }
}
