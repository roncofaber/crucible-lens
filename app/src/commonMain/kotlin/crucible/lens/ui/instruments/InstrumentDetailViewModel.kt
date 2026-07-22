package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstrumentDetailViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _instrument = MutableStateFlow<Instrument?>(null)
    val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()

    private val _datasetsState = MutableStateFlow<LoadState<List<Dataset>>>(LoadState.Loading)
    val datasetsState: StateFlow<LoadState<List<Dataset>>> = _datasetsState.asStateFlow()

    private var currentInstrumentId: String? = null

    fun load(instrumentId: String, forceRefresh: Boolean = false) {
        if (instrumentId == currentInstrumentId && !forceRefresh &&
            _datasetsState.value is LoadState.Success) return
        currentInstrumentId = instrumentId
        viewModelScope.launch {
            if (forceRefresh) {
                val current = (_datasetsState.value as? LoadState.Success)?.data ?: emptyList()
                _datasetsState.value = LoadState.Success(current, isRefreshing = true)
            } else {
                _datasetsState.value = LoadState.Loading
            }
            try {
                val resolvedInstrument = if (!forceRefresh) {
                    cacheManager.getInstruments()?.find { it.uniqueId == instrumentId }
                        ?: (apiClient.service.getInstrument(instrumentId) as? ApiResult.Success)?.data
                } else {
                    (apiClient.service.getInstrument(instrumentId) as? ApiResult.Success)?.data
                }
                if (resolvedInstrument == null) {
                    _datasetsState.value = LoadState.Error("Instrument not found")
                    return@launch
                }
                _instrument.value = resolvedInstrument
                val instrName = resolvedInstrument.instrumentName ?: resolvedInstrument.uniqueId
                if (!forceRefresh) {
                    val cached = cacheManager.getInstrumentDatasets(instrName)
                    if (cached != null) {
                        _datasetsState.value = LoadState.Success(cached, fromCache = true)
                        return@launch
                    }
                }
                when (val resp = apiClient.service.getDatasetsByInstrument(instrName)) {
                    is ApiResult.Success -> {
                        cacheManager.cacheInstrumentDatasets(instrName, resp.data)
                        _datasetsState.value = LoadState.Success(resp.data)
                    }
                    is ApiResult.Error -> _datasetsState.value = LoadState.Error("Failed to load datasets")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _datasetsState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
