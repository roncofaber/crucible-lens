package crucible.lens.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.InstrumentCreateRequest
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.instrumentSlugValidationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreateInstrumentFormState(
    val name: String = "",
    val instrumentId: String = "",
    val instrumentIdEditedManually: Boolean = false,
    val location: String = "",
    val type: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val description: String = "",
    val otherId: String = "",
    val otherIdSource: String = ""
) {
    val instrumentIdError: String?
        get() = instrumentId.takeIf { it.isNotBlank() }?.let(::instrumentSlugValidationError)

    val canCreate: Boolean
        get() = name.isNotBlank() && location.isNotBlank() && instrumentId.isNotBlank() && instrumentIdError == null

    val hasUnsavedChanges: Boolean
        get() = listOf(name, instrumentId, location, type, manufacturer, model, description, otherId, otherIdSource)
            .any { it.isNotBlank() }
}

internal fun instrumentSlugFromName(value: String): String =
    value.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(25)

class CreateInstrumentViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {
    private val _formState = MutableStateFlow(CreateInstrumentFormState())
    val formState: StateFlow<CreateInstrumentFormState> = _formState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    fun onNameChanged(value: String) {
        _formState.value = _formState.value.let { state ->
            state.copy(
                name = value,
                instrumentId = if (state.instrumentIdEditedManually) state.instrumentId else instrumentSlugFromName(value)
            )
        }
    }

    fun onInstrumentIdChanged(value: String) {
        _formState.value = _formState.value.copy(instrumentId = value, instrumentIdEditedManually = true)
    }

    fun onLocationChanged(value: String) = update { it.copy(location = value) }
    fun onTypeChanged(value: String) = update { it.copy(type = value) }
    fun onManufacturerChanged(value: String) = update { it.copy(manufacturer = value) }
    fun onModelChanged(value: String) = update { it.copy(model = value) }
    fun onDescriptionChanged(value: String) = update { it.copy(description = value) }
    fun onOtherIdChanged(value: String) = update { it.copy(otherId = value) }
    fun onOtherIdSourceChanged(value: String) = update { it.copy(otherIdSource = value) }

    fun create() {
        val draft = _formState.value
        if (_saveState.value is SaveState.Saving || !draft.canCreate) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            try {
                when (val result = repository.createInstrument(
                    InstrumentCreateRequest(
                        instrumentId = draft.instrumentId.trim(),
                        instrumentName = draft.name.trim(),
                        location = draft.location.trim(),
                        instrumentType = draft.type.trim().ifBlank { null },
                        manufacturer = draft.manufacturer.trim().ifBlank { null },
                        model = draft.model.trim().ifBlank { null },
                        description = draft.description.trim().ifBlank { null },
                        otherId = draft.otherId.trim().ifBlank { null },
                        otherIdSource = draft.otherIdSource.trim().ifBlank { null }
                    )
                )) {
                    is ApiResult.Success -> _saveState.value = SaveState.Success(result.data.uniqueId)
                    is ApiResult.Error -> _saveState.value = SaveState.Error(createInstrumentError(result.code))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _saveState.value = SaveState.Error("Connection error - check your network")
            }
        }
    }

    fun resetSaveState() {
        if (_saveState.value !is SaveState.Saving) _saveState.value = SaveState.Idle
    }

    private fun update(transform: (CreateInstrumentFormState) -> CreateInstrumentFormState) {
        _formState.value = transform(_formState.value)
    }
}

internal fun createInstrumentError(code: Int): String = when (code) {
    403 -> "Service accounts cannot register instruments"
    409 -> "That instrument ID is already in use"
    422 -> "Check the required fields and instrument ID"
    else -> "Could not register instrument ($code)"
}
