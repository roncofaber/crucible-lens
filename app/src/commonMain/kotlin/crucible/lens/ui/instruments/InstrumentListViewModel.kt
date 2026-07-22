package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Instrument
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstrumentListViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Instrument>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Instrument>>> = _loadState.asStateFlow()

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                val current = (_loadState.value as? LoadState.Success)?.data ?: emptyList()
                _loadState.value = LoadState.Success(current, isRefreshing = true)
            } else {
                _loadState.value = LoadState.Loading
            }
            try {
                if (!forceRefresh) {
                    val cached = cacheManager.getInstruments()
                    if (cached != null) { _loadState.value = LoadState.Success(cached); return@launch }
                }
                when (val resp = apiClient.service.getInstruments()) {
                    is ApiResult.Success -> {
                        cacheManager.cacheInstruments(resp.data)
                        _loadState.value = LoadState.Success(resp.data)
                    }
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
