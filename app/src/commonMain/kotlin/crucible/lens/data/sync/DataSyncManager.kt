package crucible.lens.data.sync

import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import kotlinx.coroutines.CancellationException
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
class DataSyncManager(private val repository: CrucibleRepository) {

    /**
     * [hiddenProjectIds] are skipped entirely — no network call is made for them until the
     * user unhides them (at which point the next syncAll()/preload naturally picks them up).
     */
    suspend fun syncAll(hiddenProjectIds: Set<String> = emptySet()) {
        coroutineScope {
            // Always attempt a fresh network fetch; fall back to whatever is
            // already cached if the network call fails.
            val projectsDeferred = async { repository.fetchProjects(forceRefresh = true) }
            val instrumentsDeferred = async { repository.fetchInstruments(forceRefresh = true) }

            val projects = (projectsDeferred.await() as? ApiResult.Success)?.data
                ?: (repository.fetchProjects(forceRefresh = false) as? ApiResult.Success)?.data
                ?: emptyList()
            instrumentsDeferred.await()

            // Load samples + datasets for every non-hidden project in batches
            projects.filter { it.projectId !in hiddenProjectIds }.chunked(5).forEach { batch ->
                coroutineScope {
                    batch.map { project ->
                        async {
                            try { repository.fetchProjectData(project.projectId) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { }
                        }
                    }.awaitAll()
                }
            }
        }
    }
}
