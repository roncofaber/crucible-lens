package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InstrumentManageState {
    object Loading : InstrumentManageState()
    data class Loaded(val instrument: Instrument) : InstrumentManageState()
    data class Error(val message: String) : InstrumentManageState()
}

sealed class InstrumentEditState {
    object Idle : InstrumentEditState()
    data class Editing(
        val name: String,
        val type: String,
        val manufacturer: String,
        val model: String,
        val location: String,
        val owner: String
    ) : InstrumentEditState()
    object Saving : InstrumentEditState()
    data class SaveError(val draft: Editing, val message: String) : InstrumentEditState()
}

class ManageInstrumentViewModel : ViewModel() {

    private val _state = MutableStateFlow<InstrumentManageState>(InstrumentManageState.Loading)
    val state: StateFlow<InstrumentManageState> = _state.asStateFlow()

    private val _editState = MutableStateFlow<InstrumentEditState>(InstrumentEditState.Idle)
    val editState: StateFlow<InstrumentEditState> = _editState.asStateFlow()

    private var instrumentId: String = ""

    fun init(instrumentId: String) {
        this.instrumentId = instrumentId
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = InstrumentManageState.Loading
            when (val result = ApiClient.service.getInstrument(instrumentId)) {
                is ApiResult.Success -> _state.value = InstrumentManageState.Loaded(result.data)
                is ApiResult.Error -> _state.value = InstrumentManageState.Error("Could not load instrument (${result.code})")
            }
        }
    }

    fun startEdit() {
        val instrument = (_state.value as? InstrumentManageState.Loaded)?.instrument ?: return
        _editState.value = InstrumentEditState.Editing(
            name = instrument.instrumentName ?: "",
            type = instrument.instrumentType ?: "",
            manufacturer = instrument.manufacturer ?: "",
            model = instrument.model ?: "",
            location = instrument.location ?: "",
            owner = instrument.owner ?: ""
        )
    }

    fun cancelEdit() { _editState.value = InstrumentEditState.Idle }

    fun onNameChanged(v: String) = updateDraft { it.copy(name = v) }
    fun onTypeChanged(v: String) = updateDraft { it.copy(type = v) }
    fun onManufacturerChanged(v: String) = updateDraft { it.copy(manufacturer = v) }
    fun onModelChanged(v: String) = updateDraft { it.copy(model = v) }
    fun onLocationChanged(v: String) = updateDraft { it.copy(location = v) }
    fun onOwnerChanged(v: String) = updateDraft { it.copy(owner = v) }

    fun save() {
        val draft = currentDraft() ?: return
        if (_editState.value is InstrumentEditState.Saving) return
        _editState.value = InstrumentEditState.Saving
        viewModelScope.launch {
            val result = ApiClient.service.updateInstrument(
                instrumentId,
                InstrumentUpdateRequest(
                    instrumentName = draft.name.trim().ifBlank { null },
                    instrumentType = draft.type.trim().ifBlank { null },
                    manufacturer = draft.manufacturer.trim().ifBlank { null },
                    model = draft.model.trim().ifBlank { null },
                    location = draft.location.trim().ifBlank { null },
                    owner = draft.owner.trim().ifBlank { null }
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    CacheManager.clearInstrumentsCache()
                    _state.value = InstrumentManageState.Loaded(result.data)
                    _editState.value = InstrumentEditState.Idle
                }
                is ApiResult.Error -> _editState.value = InstrumentEditState.SaveError(draft, "Save failed (${result.code})")
            }
        }
    }

    private fun currentDraft(): InstrumentEditState.Editing? = when (val s = _editState.value) {
        is InstrumentEditState.Editing -> s
        is InstrumentEditState.SaveError -> s.draft
        else -> null
    }

    private fun updateDraft(update: (InstrumentEditState.Editing) -> InstrumentEditState.Editing) {
        val draft = currentDraft() ?: return
        _editState.value = update(draft)
    }
}
