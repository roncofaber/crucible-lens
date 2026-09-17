package crucible.lens.ui.detail

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ThumbnailUpdateRequest
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.sync.DataSyncManager
import crucible.lens.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val uuid: String, val isRefreshing: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class DeletionRequestSubmissionState {
    object Idle : DeletionRequestSubmissionState()
    object Submitting : DeletionRequestSubmissionState()
    data class Submitted(val resourceId: String) : DeletionRequestSubmissionState()
    data class Error(val message: String) : DeletionRequestSubmissionState()
}

enum class AssociatedFileAction { DOWNLOAD, SHARE }

data class AssociatedFileActionKey(
    val datasetUuid: String,
    val mfid: String,
    val action: AssociatedFileAction
)

sealed class AssociatedFileActionState {
    data object Resolving : AssociatedFileActionState()
    data class Ready(val url: String) : AssociatedFileActionState()
    data class Error(val message: String) : AssociatedFileActionState()
}

enum class ThumbnailMutationType { DELETE, UPDATE }

data class ThumbnailMutationKey(
    val datasetUuid: String,
    val thumbnailId: Int,
    val type: ThumbnailMutationType
)

sealed class ThumbnailMutationState {
    data object Running : ThumbnailMutationState()
    data class Error(val message: String) : ThumbnailMutationState()
}

internal fun thumbnailMutationIds(
    states: Map<ThumbnailMutationKey, ThumbnailMutationState>,
    datasetUuid: String,
    type: ThumbnailMutationType
): Set<Int> = states.entries
    .filter { (key, state) -> key.datasetUuid == datasetUuid && key.type == type && state is ThumbnailMutationState.Running }
    .mapTo(mutableSetOf()) { it.key.thumbnailId }

internal fun thumbnailMutationErrors(
    states: Map<ThumbnailMutationKey, ThumbnailMutationState>,
    datasetUuid: String,
    type: ThumbnailMutationType
): Map<Int, String> = states.mapNotNull { (key, state) ->
    val error = state as? ThumbnailMutationState.Error
    if (key.datasetUuid == datasetUuid && key.type == type && error != null) key.thumbnailId to error.message else null
}.toMap()

private const val MAX_CARD_STATE_ENTRIES = 50

class ResourceDetailViewModel(
    private val repository: CrucibleRepository,
    private val dataSyncManager: DataSyncManager,
    private val apiClient: ApiClient
) : ViewModel() {

    // Tracks the active fetch/refresh so navigating to a new resource
    // cancels any in-flight request for the previous one.
    private var activeFetchJob: Job? = null
    private var deletionRequestSubmissionJob: Job? = null
    private val associatedFileActionJobs = mutableMapOf<AssociatedFileActionKey, Job>()
    private val thumbnailMutationJobs = mutableMapOf<ThumbnailMutationKey, Job>()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deletionRequestSubmissionState = MutableStateFlow<DeletionRequestSubmissionState>(DeletionRequestSubmissionState.Idle)
    val deletionRequestSubmissionState: StateFlow<DeletionRequestSubmissionState> = _deletionRequestSubmissionState.asStateFlow()

    private val _associatedFileActionStates = MutableStateFlow<Map<AssociatedFileActionKey, AssociatedFileActionState>>(emptyMap())
    val associatedFileActionStates: StateFlow<Map<AssociatedFileActionKey, AssociatedFileActionState>> =
        _associatedFileActionStates.asStateFlow()

    private val _thumbnailMutationStates = MutableStateFlow<Map<ThumbnailMutationKey, ThumbnailMutationState>>(emptyMap())
    val thumbnailMutationStates: StateFlow<Map<ThumbnailMutationKey, ThumbnailMutationState>> =
        _thumbnailMutationStates.asStateFlow()

    /** Persists expanded/collapsed state of detail screen cards across navigation. */
    private val resourceCardState = mutableStateMapOf<String, SnapshotStateMap<String, Boolean>>()
    private val resourceCardStateOrder = ArrayDeque<String>()

    fun getCardState(resourceId: String, key: String): Boolean =
        resourceCardState[resourceId]?.get(key) ?: false

    fun setCardState(resourceId: String, key: String, value: Boolean) {
        if (!resourceCardState.containsKey(resourceId)) {
            resourceCardStateOrder.addLast(resourceId)
            if (resourceCardStateOrder.size > MAX_CARD_STATE_ENTRIES) {
                resourceCardState.remove(resourceCardStateOrder.removeFirst())
            }
        }
        resourceCardState.getOrPut(resourceId) { mutableStateMapOf() }[key] = value
    }

    fun refreshThumbnails(uuid: String) {
        viewModelScope.launch {
            repository.fetchThumbnails(uuid, forceRefresh = true)
        }
    }

    fun fetchResource(uuid: String) {
        activeFetchJob?.cancel()
        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            // Show cached version immediately for snappy navigation (Success emits as soon
            // as ANY cached data exists for this uuid — the screen observes the repository
            // directly for the actual resource content), but always fetch fresh data so the
            // detail view has links and full metadata.
            val hasCached = repository.getCachedResource(trimmedUuid) != null
            _uiState.value = if (hasCached) UiState.Success(trimmedUuid, isRefreshing = true) else UiState.Loading

            when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                is ResourceResult.Success -> {
                    _uiState.value = UiState.Success(trimmedUuid)
                }
                is ResourceResult.Error -> {
                    _uiState.value = if (hasCached) UiState.Success(trimmedUuid) else UiState.Error(result.message)
                }
                is ResourceResult.Loading -> {}
            }
        }
    }

    private var syncJob: Job? = null
    // Remembered so refreshResource()'s finally block can resume sync with the same
    // synced-project filter, without needing NavGraph to call startBackgroundSync() again.
    private var lastSyncedProjectIds: Set<String> = emptySet()
    private var lastCurrentUserOrcid: String? = null
    private var lastSyncContext: PlatformContext? = null
    private var lastAccountId: String? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * Only [syncedProjectIds] are preloaded. Everything else fetches on demand when opened.
     * [currentUserOrcid] scopes the pending-join-request-count preload to projects the caller
     * leads — see [DataSyncManager.syncAll].
     */
    fun startBackgroundSync(
        context: PlatformContext,
        accountId: String,
        syncedProjectIds: Set<String> = emptySet(),
        currentUserOrcid: String? = null
    ) {
        syncJob?.cancel()
        lastSyncContext = context
        lastAccountId = accountId
        lastSyncedProjectIds = syncedProjectIds
        lastCurrentUserOrcid = currentUserOrcid
        _isSyncing.value = true
        syncJob = viewModelScope.launch {
            try { dataSyncManager.syncAll(context, accountId, syncedProjectIds, currentUserOrcid) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            finally { _isSyncing.value = false }
        }
    }

    fun stopBackgroundSync() {
        syncJob?.cancel()
        syncJob = null
        lastSyncedProjectIds = emptySet()
        lastCurrentUserOrcid = null
        lastSyncContext = null
        lastAccountId = null
        _isSyncing.value = false
    }

    fun reset() {
        deletionRequestSubmissionJob?.cancel()
        deletionRequestSubmissionJob = null
        associatedFileActionJobs.values.forEach { it.cancel() }
        associatedFileActionJobs.clear()
        thumbnailMutationJobs.values.forEach { it.cancel() }
        thumbnailMutationJobs.clear()
        _associatedFileActionStates.value = emptyMap()
        _thumbnailMutationStates.value = emptyMap()
        _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Idle
        _uiState.value = UiState.Idle
    }

    fun resolveAssociatedFileAction(key: AssociatedFileActionKey) {
        if (_associatedFileActionStates.value[key] is AssociatedFileActionState.Resolving) return
        associatedFileActionJobs[key]?.cancel()
        _associatedFileActionStates.update {
            updateAssociatedFileActionState(it, key, AssociatedFileActionState.Resolving)
        }
        associatedFileActionJobs[key] = viewModelScope.launch {
            try {
                when (val result = repository.fetchFileUrl(key.mfid)) {
                    is ApiResult.Success -> _associatedFileActionStates.update {
                        updateAssociatedFileActionState(it, key, AssociatedFileActionState.Ready(result.data))
                    }
                    is ApiResult.Error -> _associatedFileActionStates.update {
                        updateAssociatedFileActionState(
                            it,
                            key,
                            AssociatedFileActionState.Error(associatedFileUrlError(result.code))
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _associatedFileActionStates.update {
                    updateAssociatedFileActionState(
                        it,
                        key,
                        AssociatedFileActionState.Error("Connection error. Check your network and try again")
                    )
                }
            }
        }
    }

    fun clearAssociatedFileAction(key: AssociatedFileActionKey) {
        associatedFileActionJobs.remove(key)?.cancel()
        _associatedFileActionStates.update { updateAssociatedFileActionState(it, key, null) }
    }

    fun deleteThumbnail(datasetUuid: String, thumbnailId: Int) {
        val key = ThumbnailMutationKey(datasetUuid, thumbnailId, ThumbnailMutationType.DELETE)
        launchThumbnailMutation(key) {
            when (val result = apiClient.service.deleteThumbnail(datasetUuid, thumbnailId)) {
                is ApiResult.Success -> if (result.data) null else "The server did not confirm deletion"
                is ApiResult.Error -> "Delete failed (${result.code})"
            }
        }
    }

    fun updateThumbnail(datasetUuid: String, thumbnailId: Int, request: ThumbnailUpdateRequest) {
        val key = ThumbnailMutationKey(datasetUuid, thumbnailId, ThumbnailMutationType.UPDATE)
        launchThumbnailMutation(key) {
            when (val result = apiClient.service.updateThumbnail(datasetUuid, thumbnailId, request)) {
                is ApiResult.Success -> null
                is ApiResult.Error -> "Update failed (${result.code})"
            }
        }
    }

    private fun launchThumbnailMutation(
        key: ThumbnailMutationKey,
        operation: suspend () -> String?
    ) {
        val targetIsBusy = _thumbnailMutationStates.value.any { (activeKey, state) ->
            activeKey.datasetUuid == key.datasetUuid &&
                activeKey.thumbnailId == key.thumbnailId &&
                state is ThumbnailMutationState.Running
        }
        if (targetIsBusy) return
        _thumbnailMutationStates.update { it + (key to ThumbnailMutationState.Running) }
        thumbnailMutationJobs[key] = viewModelScope.launch {
            try {
                val error = operation()
                if (error == null) {
                    repository.fetchThumbnails(key.datasetUuid, forceRefresh = true)
                    _thumbnailMutationStates.update { it - key }
                } else {
                    _thumbnailMutationStates.update { it + (key to ThumbnailMutationState.Error(error)) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _thumbnailMutationStates.update {
                    it + (key to ThumbnailMutationState.Error("Connection error. Check your network and try again"))
                }
            } finally {
                thumbnailMutationJobs.remove(key)
            }
        }
    }

    fun submitDeletionRequest(resourceId: String, reason: String) {
        if (_deletionRequestSubmissionState.value is DeletionRequestSubmissionState.Submitting) return
        _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Submitting
        deletionRequestSubmissionJob = viewModelScope.launch {
            try {
                when (val result = apiClient.service.requestDeletion(
                    resourceId = resourceId,
                    reason = reason.trim().ifBlank { null }
                )) {
                    is ApiResult.Success -> {
                        _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Submitted(resourceId)
                    }
                    is ApiResult.Error -> {
                        _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Error(
                            if (result.code == 409) {
                                "A deletion request may already exist"
                            } else {
                                "Could not submit deletion request (${result.code})"
                            }
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Error(
                    "Connection error. Check your network and try again"
                )
            }
        }
    }

    fun clearDeletionRequestSubmission() {
        if (_deletionRequestSubmissionState.value is DeletionRequestSubmissionState.Submitting) return
        _deletionRequestSubmissionState.value = DeletionRequestSubmissionState.Idle
    }

    fun refreshResource(uuid: String) {
        activeFetchJob?.cancel()
        // Pause background sync so the user-initiated refresh gets uncontested network access.
        // Sync resumes after the refresh completes.
        val syncWasActive = syncJob?.isActive == true
        syncJob?.cancel()

        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            val current = _uiState.value
            val isPrimary = current is UiState.Success && current.uuid == trimmedUuid
            _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = true) else it }
            try {
                // forceRefresh (not invalidate-then-fetch) keeps serving the existing cached
                // resource/thumbnails to every observer until the fresh result lands, so
                // links/metadata/thumbnail-gated cards never collapse and pop back in mid-refresh.
                when (val result = repository.fetchResourceByUuid(trimmedUuid, forceRefresh = true)) {
                    is ResourceResult.Success -> {
                        if (result.resource is Dataset) {
                            repository.fetchThumbnails(trimmedUuid, forceRefresh = true)
                        }
                        // isPrimary distinguishes refreshing the currently-displayed resource
                        // from refreshing a sibling reached via the pager — either way the
                        // fresh data lands in the repository's cache and every page observing
                        // this uuid picks it up automatically; only the primary case updates
                        // this ViewModel's own uiState.
                        if (isPrimary) _uiState.value = UiState.Success(trimmedUuid)
                    }
                    is ResourceResult.Error -> if (isPrimary) _uiState.value = UiState.Error(result.message)
                    is ResourceResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Timeout or network failure — error state (if primary) was set above
            } finally {
                _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = false) else it }
                val context = lastSyncContext
                val accountId = lastAccountId
                if (syncWasActive && context != null && accountId != null) {
                    startBackgroundSync(context, accountId, lastSyncedProjectIds, lastCurrentUserOrcid)
                }
            }
        }
    }
}

internal fun updateAssociatedFileActionState(
    states: Map<AssociatedFileActionKey, AssociatedFileActionState>,
    key: AssociatedFileActionKey,
    state: AssociatedFileActionState?
): Map<AssociatedFileActionKey, AssociatedFileActionState> = if (state == null) states - key else states + (key to state)

internal fun associatedFileUrlError(code: Int): String = when (code) {
    401 -> "Sign in again to access this file"
    403 -> "You do not have permission to access this file"
    404 -> "File link not found"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not get file link ($code)"
}
