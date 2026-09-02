package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstrumentDatasetPaginationState(
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
) {
    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore
}

class InstrumentDetailViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _instrument = MutableStateFlow<Instrument?>(null)
    val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()

    private val _datasetsState = MutableStateFlow<LoadState<List<Dataset>>>(LoadState.Loading)
    val datasetsState: StateFlow<LoadState<List<Dataset>>> = _datasetsState.asStateFlow()

    private val _paginationState = MutableStateFlow(InstrumentDatasetPaginationState())
    val paginationState: StateFlow<InstrumentDatasetPaginationState> = _paginationState.asStateFlow()

    private var currentInstrumentId: String? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    fun load(instrumentId: String, forceRefresh: Boolean = false) {
        if (instrumentId == currentInstrumentId && !forceRefresh &&
            _datasetsState.value is LoadState.Success) return
        val previous = if (instrumentId == currentInstrumentId) {
            _datasetsState.value as? LoadState.Success
        } else {
            null
        }
        val previousPagination = if (instrumentId == currentInstrumentId) _paginationState.value else null
        currentInstrumentId = instrumentId
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadJob = viewModelScope.launch {
            if (forceRefresh) {
                _datasetsState.value = previous?.copy(isRefreshing = true, refreshError = null)
                    ?: LoadState.Loading
            } else {
                _datasetsState.value = LoadState.Loading
                _instrument.value = null
            }
            _paginationState.value = InstrumentDatasetPaginationState()
            try {
                val cachedInstrument = if (forceRefresh) null else {
                    repository.getCachedInstrument(instrumentId)
                }
                val resolvedInstrument = cachedInstrument ?: when (val result = repository.fetchInstrument(instrumentId, forceRefresh)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Error -> {
                        _datasetsState.value = instrumentDetailFailureState(previous, instrumentLoadError(result.code))
                        if (previous != null && previousPagination != null) _paginationState.value = previousPagination
                        return@launch
                    }
                }
                _instrument.value = resolvedInstrument
                val instrumentMfid = resolvedInstrument.uniqueId
                if (!forceRefresh) {
                    val cached = repository.getCachedInstrumentDatasets(instrumentMfid)
                    if (cached != null) {
                        _datasetsState.value = LoadState.Success(cached.datasets, fromCache = true)
                        _paginationState.value = InstrumentDatasetPaginationState(nextCursor = cached.nextCursor)
                        return@launch
                    }
                }
                when (val resp = repository.fetchInstrumentDatasets(instrumentMfid, forceRefresh = forceRefresh)) {
                    is ApiResult.Success -> {
                        _datasetsState.value = LoadState.Success(resp.data.datasets)
                        _paginationState.value = InstrumentDatasetPaginationState(nextCursor = resp.data.nextCursor)
                    }
                    is ApiResult.Error -> {
                        _datasetsState.value = instrumentDetailFailureState(previous, datasetLoadError(resp.code))
                        if (previous != null && previousPagination != null) _paginationState.value = previousPagination
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _datasetsState.value = instrumentDetailFailureState(
                    previous,
                    "Connection error - check your network"
                )
                if (previous != null && previousPagination != null) _paginationState.value = previousPagination
            }
        }
    }

    fun loadMore() {
        val instrumentMfid = _instrument.value?.uniqueId ?: return
        val cursor = _paginationState.value.nextCursor ?: return
        if (_paginationState.value.isLoadingMore) return
        _paginationState.value = _paginationState.value.copy(isLoadingMore = true, loadMoreError = null)
        loadMoreJob = viewModelScope.launch {
            when (val result = repository.fetchInstrumentDatasets(instrumentMfid, cursor = cursor)) {
                is ApiResult.Success -> {
                    _datasetsState.value = LoadState.Success(result.data.datasets)
                    _paginationState.value = InstrumentDatasetPaginationState(nextCursor = result.data.nextCursor)
                }
                is ApiResult.Error -> {
                    _paginationState.value = _paginationState.value.copy(
                        isLoadingMore = false,
                        loadMoreError = datasetLoadError(result.code)
                    )
                }
            }
        }
    }
}

internal fun instrumentLoadError(code: Int): String = when (code) {
    404 -> "Instrument not found"
    401 -> "Sign in again to view this instrument"
    403 -> "You do not have permission to view this instrument"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not load instrument ($code)"
}

internal fun datasetLoadError(code: Int): String = when (code) {
    401 -> "Sign in again to refresh datasets"
    403 -> "You do not have permission to view these datasets"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not load datasets ($code)"
}

internal fun <T> instrumentDetailFailureState(
    previous: LoadState.Success<T>?,
    message: String
): LoadState<T> = previous?.copy(isRefreshing = false, refreshError = message)
    ?: LoadState.Error(message)
