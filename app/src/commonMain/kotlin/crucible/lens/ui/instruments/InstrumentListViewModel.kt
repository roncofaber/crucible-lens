package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstrumentListViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Instrument>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Instrument>>> = _loadState.asStateFlow()

    private val _status = MutableStateFlow(InstrumentStatus.Active)
    val status: StateFlow<InstrumentStatus> = _status.asStateFlow()

    private var loadJob: Job? = null

    init { load() }

    fun selectStatus(status: InstrumentStatus) {
        if (_status.value == status) return
        _status.value = status
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val selectedStatus = _status.value
        loadJob = viewModelScope.launch {
            if (forceRefresh) {
                val current = (_loadState.value as? LoadState.Success)?.data ?: emptyList()
                _loadState.value = LoadState.Success(current, isRefreshing = true)
            } else {
                _loadState.value = LoadState.Loading
            }
            try {
                when (val resp = repository.fetchInstruments(forceRefresh, selectedStatus)) {
                    is ApiResult.Success -> _loadState.value = LoadState.Success(resp.data)
                    is ApiResult.Error -> _loadState.value = LoadState.Error("Failed to load instruments")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
