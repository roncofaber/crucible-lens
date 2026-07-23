package crucible.lens.ui.detail

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.sync.DataSyncManager
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

private const val MAX_CARD_STATE_ENTRIES = 50

class ResourceDetailViewModel(
    private val repository: CrucibleRepository,
    private val dataSyncManager: DataSyncManager
) : ViewModel() {

    // Tracks the active fetch/refresh so navigating to a new resource
    // cancels any in-flight request for the previous one.
    private var activeFetchJob: Job? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
            repository.invalidateThumbnails(uuid)
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
                is ResourceResult.Error -> _uiState.value = UiState.Error(result.message)
                is ResourceResult.Loading -> {}
            }
        }
    }

    private var syncJob: Job? = null
    // Remembered so refreshResource()'s finally block can resume sync with the same
    // hidden-project filter, without needing NavGraph to call startBackgroundSync() again.
    private var lastHiddenProjectIds: Set<String> = emptySet()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** [hiddenProjectIds] are skipped entirely — no network call until the user unhides them. */
    fun startBackgroundSync(hiddenProjectIds: Set<String> = emptySet()) {
        lastHiddenProjectIds = hiddenProjectIds
        _isSyncing.value = true
        syncJob = viewModelScope.launch {
            try { dataSyncManager.syncAll(hiddenProjectIds) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            finally { _isSyncing.value = false }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun refreshResource(uuid: String) {
        activeFetchJob?.cancel()
        // Pause background sync so the user-initiated refresh gets uncontested network access.
        // Sync resumes after the refresh completes.
        val syncWasActive = syncJob?.isActive == true
        syncJob?.cancel()

        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()
            repository.invalidateResource(trimmedUuid)
            repository.invalidateThumbnails(trimmedUuid)

            val current = _uiState.value
            val isPrimary = current is UiState.Success && current.uuid == trimmedUuid
            _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = true) else it }
            try {
                when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                    is ResourceResult.Success -> {
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
                if (syncWasActive) startBackgroundSync(lastHiddenProjectIds)
            }
        }
    }
}
