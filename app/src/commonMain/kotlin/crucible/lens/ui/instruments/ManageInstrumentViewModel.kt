package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import crucible.lens.data.model.InstrumentStatus
import crucible.lens.data.model.InstrumentUpdateRequest
import crucible.lens.data.model.User
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.allowsEdit
import crucible.lens.data.util.allowsTransfer
import crucible.lens.data.util.instrumentSlugValidationError
import crucible.lens.ui.common.OwnershipTransferDraft
import crucible.lens.ui.common.OwnershipTransferState
import crucible.lens.ui.common.ResourceRenameState
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InstrumentManageState {
    object Loading : InstrumentManageState()
    data class Loaded(val instrument: Instrument, val isOwner: Boolean) : InstrumentManageState() {
        val canEdit: Boolean get() = instrument.capabilities.allowsEdit(true)
        val canManageAccess: Boolean get() = instrument.capabilities?.canManageAccess == true
        val canChangeStatus: Boolean get() = instrument.capabilities?.canChangeStatus == true
        val canTransfer: Boolean get() = instrument.capabilities.allowsTransfer(isOwner)
    }
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
        val description: String,
        val otherId: String,
        val otherIdSource: String
    ) : InstrumentEditState()
    object Saving : InstrumentEditState()
    data class SaveError(val draft: Editing, val message: String) : InstrumentEditState()
}

sealed class InstrumentStatusState {
    data object Idle : InstrumentStatusState()
    data class Selecting(
        val current: InstrumentStatus?,
        val selected: InstrumentStatus?,
        val error: String? = null
    ) : InstrumentStatusState()
    data class Confirming(val status: InstrumentStatus) : InstrumentStatusState()
    data class Saving(val status: InstrumentStatus) : InstrumentStatusState()
    data class Success(val status: InstrumentStatus) : InstrumentStatusState()
}

sealed class ServiceAccountAddState {
    data object Idle : ServiceAccountAddState()
    data class Editing(
        val query: String = "",
        val results: List<User> = emptyList(),
        val isSearching: Boolean = false,
        val error: String? = null
    ) : ServiceAccountAddState()
    data class Resolved(val query: String, val account: User, val error: String? = null) : ServiceAccountAddState()
    data class Adding(val query: String, val account: User) : ServiceAccountAddState()
    data class Success(val account: User) : ServiceAccountAddState()
}

sealed class ServiceAccountRemovalState {
    data object Idle : ServiceAccountRemovalState()
    data class Confirming(val account: User, val error: String? = null) : ServiceAccountRemovalState()
    data class Removing(val account: User) : ServiceAccountRemovalState()
    data class Success(val account: User) : ServiceAccountRemovalState()
}

class ManageInstrumentViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _state = MutableStateFlow<InstrumentManageState>(InstrumentManageState.Loading)
    val state: StateFlow<InstrumentManageState> = _state.asStateFlow()

    private val _editState = MutableStateFlow<InstrumentEditState>(InstrumentEditState.Idle)
    val editState: StateFlow<InstrumentEditState> = _editState.asStateFlow()

    private val _renameState = MutableStateFlow<ResourceRenameState>(ResourceRenameState.Idle)
    val renameState: StateFlow<ResourceRenameState> = _renameState.asStateFlow()

    private val _ownershipTransferState = MutableStateFlow<OwnershipTransferState>(OwnershipTransferState.Idle)
    val ownershipTransferState: StateFlow<OwnershipTransferState> = _ownershipTransferState.asStateFlow()

    private val _statusState = MutableStateFlow<InstrumentStatusState>(InstrumentStatusState.Idle)
    val statusState: StateFlow<InstrumentStatusState> = _statusState.asStateFlow()

    private val _serviceAccountsState = MutableStateFlow<LoadState<List<User>>>(LoadState.Loading)
    val serviceAccountsState: StateFlow<LoadState<List<User>>> = _serviceAccountsState.asStateFlow()

    private val _serviceAccountAddState = MutableStateFlow<ServiceAccountAddState>(ServiceAccountAddState.Idle)
    val serviceAccountAddState: StateFlow<ServiceAccountAddState> = _serviceAccountAddState.asStateFlow()

    private val _serviceAccountRemovalState = MutableStateFlow<ServiceAccountRemovalState>(ServiceAccountRemovalState.Idle)
    val serviceAccountRemovalState: StateFlow<ServiceAccountRemovalState> = _serviceAccountRemovalState.asStateFlow()

    private var instrumentId: String = ""
    private var currentUserId: String? = null
    private var ownershipSearchJob: Job? = null
    private var serviceAccountSearchJob: Job? = null

    fun init(instrumentId: String, currentUserId: String?) {
        this.instrumentId = instrumentId
        this.currentUserId = currentUserId
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = InstrumentManageState.Loading
            when (val result = repository.fetchInstrument(instrumentId, forceRefresh = true)) {
                is ApiResult.Success -> {
                    setLoaded(result.data)
                    if ((state.value as? InstrumentManageState.Loaded)?.canManageAccess == true) {
                        loadServiceAccounts()
                    }
                }
                is ApiResult.Error -> _state.value = InstrumentManageState.Error("Could not load instrument (${result.code})")
            }
        }
    }

    fun startEdit() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canEdit) return
        val instrument = loaded.instrument
        _editState.value = InstrumentEditState.Editing(
            name = instrument.instrumentName ?: "",
            type = instrument.instrumentType ?: "",
            manufacturer = instrument.manufacturer ?: "",
            model = instrument.model ?: "",
            location = instrument.location ?: "",
            description = instrument.description ?: "",
            otherId = instrument.otherId ?: "",
            otherIdSource = instrument.otherIdSource ?: ""
        )
    }

    fun cancelEdit() { _editState.value = InstrumentEditState.Idle }

    fun onNameChanged(v: String) = updateDraft { it.copy(name = v) }
    fun onTypeChanged(v: String) = updateDraft { it.copy(type = v) }
    fun onManufacturerChanged(v: String) = updateDraft { it.copy(manufacturer = v) }
    fun onModelChanged(v: String) = updateDraft { it.copy(model = v) }
    fun onLocationChanged(v: String) = updateDraft { it.copy(location = v) }
    fun onDescriptionChanged(v: String) = updateDraft { it.copy(description = v) }
    fun onOtherIdChanged(v: String) = updateDraft { it.copy(otherId = v) }
    fun onOtherIdSourceChanged(v: String) = updateDraft { it.copy(otherIdSource = v) }

    fun save() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canEdit) return
        val draft = currentDraft() ?: return
        if (_editState.value is InstrumentEditState.Saving) return
        if (draft.name.isBlank() || draft.location.isBlank()) {
            _editState.value = InstrumentEditState.SaveError(draft, "Name and location are required")
            return
        }
        _editState.value = InstrumentEditState.Saving
        viewModelScope.launch {
            val result = apiClient.service.updateInstrument(
                instrumentId,
                InstrumentUpdateRequest(
                    instrumentName = draft.name.trim().ifBlank { null },
                    instrumentType = draft.type.trim().ifBlank { null },
                    manufacturer = draft.manufacturer.trim().ifBlank { null },
                    model = draft.model.trim().ifBlank { null },
                    location = draft.location.trim().ifBlank { null },
                    description = draft.description.trim().ifBlank { null },
                    otherId = draft.otherId.trim().ifBlank { null },
                    otherIdSource = draft.otherIdSource.trim().ifBlank { null }
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    repository.invalidateInstruments()
                    repository.invalidateInstrument(instrumentId)
                    val previous = (_state.value as? InstrumentManageState.Loaded)?.instrument
                    setLoaded(
                        result.data.copy(
                            owner = result.data.owner ?: previous?.owner,
                            capabilities = result.data.capabilities ?: previous?.capabilities
                        )
                    )
                    _editState.value = InstrumentEditState.Idle
                }
                is ApiResult.Error -> _editState.value = InstrumentEditState.SaveError(draft, editError(result.code))
            }
        }
    }

    fun showRename() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canEdit) return
        val instrument = loaded.instrument
        _renameState.value = ResourceRenameState.Editing(instrument.instrumentId.orEmpty())
    }

    fun updateRename(value: String) {
        val state = _renameState.value as? ResourceRenameState.Editing ?: return
        _renameState.value = state.copy(value = value, error = null)
    }

    fun dismissRename() {
        if (_renameState.value !is ResourceRenameState.Saving) _renameState.value = ResourceRenameState.Idle
    }

    fun rename() {
        val state = _renameState.value as? ResourceRenameState.Editing ?: return
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canEdit) return
        val instrument = loaded.instrument
        val newSlug = state.value.trim()
        val error = instrumentSlugValidationError(newSlug)
        if (error != null) {
            _renameState.value = state.copy(error = error)
            return
        }
        if (newSlug == instrument.instrumentId) {
            _renameState.value = ResourceRenameState.Idle
            return
        }
        _renameState.value = ResourceRenameState.Saving(newSlug)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.updateInstrument(instrumentId, InstrumentUpdateRequest(instrumentId = newSlug))) {
                    is ApiResult.Success -> {
                        repository.invalidateInstruments()
                        repository.invalidateInstrument(instrumentId)
                        setLoaded(
                            result.data.copy(
                                owner = result.data.owner ?: instrument.owner,
                                capabilities = result.data.capabilities ?: instrument.capabilities
                            )
                        )
                        _renameState.value = ResourceRenameState.Success(result.data.instrumentId ?: newSlug)
                    }
                    is ApiResult.Error -> _renameState.value = ResourceRenameState.Editing(newSlug, renameError(result.code))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _renameState.value = ResourceRenameState.Editing(newSlug, "Connection error. Try again")
            }
        }
    }

    fun consumeRenameSuccess() {
        if (_renameState.value is ResourceRenameState.Success) _renameState.value = ResourceRenameState.Idle
    }

    fun showStatusSelector() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canChangeStatus) return
        val current = InstrumentStatus.fromApi(loaded.instrument.status)
        _statusState.value = InstrumentStatusState.Selecting(current = current, selected = current)
    }

    fun selectStatus(status: InstrumentStatus) {
        val state = _statusState.value as? InstrumentStatusState.Selecting ?: return
        _statusState.value = state.copy(selected = status, error = null)
    }

    fun requestStatusChange() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canChangeStatus) return
        val state = _statusState.value as? InstrumentStatusState.Selecting ?: return
        val status = state.selected ?: return
        if (status == state.current) {
            _statusState.value = InstrumentStatusState.Idle
        } else if (status == InstrumentStatus.Decommissioned) {
            _statusState.value = InstrumentStatusState.Confirming(status)
        } else {
            updateStatus(status)
        }
    }

    fun confirmStatusChange() {
        val status = (_statusState.value as? InstrumentStatusState.Confirming)?.status ?: return
        updateStatus(status)
    }

    fun cancelStatusConfirmation() {
        val status = (_statusState.value as? InstrumentStatusState.Confirming)?.status ?: return
        val instrument = (_state.value as? InstrumentManageState.Loaded)?.instrument ?: return
        _statusState.value = InstrumentStatusState.Selecting(
            current = InstrumentStatus.fromApi(instrument.status),
            selected = status
        )
    }

    fun dismissStatusChange() {
        if (_statusState.value !is InstrumentStatusState.Saving) {
            _statusState.value = InstrumentStatusState.Idle
        }
    }

    fun consumeStatusSuccess() {
        if (_statusState.value is InstrumentStatusState.Success) {
            _statusState.value = InstrumentStatusState.Idle
        }
    }

    fun loadServiceAccounts() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canManageAccess) return
        viewModelScope.launch {
            _serviceAccountsState.value = LoadState.Loading
            try {
                when (val result = apiClient.service.getInstrumentServiceAccounts(instrumentId)) {
                    is ApiResult.Success -> _serviceAccountsState.value = LoadState.Success(result.data)
                    is ApiResult.Error -> _serviceAccountsState.value = LoadState.Error("Could not load service accounts (${result.code})")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _serviceAccountsState.value = LoadState.Error("Connection error. Check your network and try again")
            }
        }
    }

    fun showAddServiceAccount() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canManageAccess) return
        _serviceAccountAddState.value = ServiceAccountAddState.Editing()
    }

    fun updateServiceAccountQuery(query: String) {
        if (_serviceAccountAddState.value is ServiceAccountAddState.Adding) return
        serviceAccountSearchJob?.cancel()
        val trimmed = query.trim()
        _serviceAccountAddState.value = ServiceAccountAddState.Editing(query = query, isSearching = trimmed.length >= 3)
        if (trimmed.length < 3) return
        serviceAccountSearchJob = viewModelScope.launch {
            delay(300)
            try {
                when (val result = apiClient.service.searchUsers(trimmed, isServiceAccount = true)) {
                    is ApiResult.Success -> _serviceAccountAddState.value = ServiceAccountAddState.Editing(
                        query = query,
                        results = result.data,
                        error = if (result.data.isEmpty()) "No matching service accounts" else null
                    )
                    is ApiResult.Error -> _serviceAccountAddState.value = ServiceAccountAddState.Editing(
                        query,
                        error = "Search failed (${result.code})"
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _serviceAccountAddState.value = ServiceAccountAddState.Editing(query, error = "Connection error. Try again")
            }
        }
    }

    fun selectServiceAccount(account: User) {
        val query = account.username ?: account.uniqueId ?: return
        val existing = (_serviceAccountsState.value as? LoadState.Success)?.data.orEmpty()
        _serviceAccountAddState.value = ServiceAccountAddState.Resolved(
            query,
            account,
            if (existing.any { it.uniqueId == account.uniqueId }) "This service account is already an operator" else null
        )
    }

    fun addServiceAccount() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canManageAccess) return
        val state = _serviceAccountAddState.value as? ServiceAccountAddState.Resolved ?: return
        if (state.error != null) return
        val accountId = state.account.uniqueId ?: return
        _serviceAccountAddState.value = ServiceAccountAddState.Adding(state.query, state.account)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.addInstrumentServiceAccount(instrumentId, accountId)) {
                    is ApiResult.Success -> {
                        _serviceAccountsState.value = LoadState.Success(result.data)
                        _serviceAccountAddState.value = ServiceAccountAddState.Success(state.account)
                    }
                    is ApiResult.Error -> _serviceAccountAddState.value = ServiceAccountAddState.Resolved(
                        state.query,
                        state.account,
                        serviceAccountMutationError(result.code, "add")
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _serviceAccountAddState.value = ServiceAccountAddState.Resolved(state.query, state.account, "Connection error. Try again")
            }
        }
    }

    fun dismissAddServiceAccount() {
        if (_serviceAccountAddState.value !is ServiceAccountAddState.Adding) {
            serviceAccountSearchJob?.cancel()
            _serviceAccountAddState.value = ServiceAccountAddState.Idle
        }
    }

    fun consumeServiceAccountAddSuccess() {
        if (_serviceAccountAddState.value is ServiceAccountAddState.Success) {
            _serviceAccountAddState.value = ServiceAccountAddState.Idle
        }
    }

    fun confirmRemoveServiceAccount(account: User) {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canManageAccess || account.uniqueId == null) return
        _serviceAccountRemovalState.value = ServiceAccountRemovalState.Confirming(account)
    }

    fun removeServiceAccount() {
        val state = _serviceAccountRemovalState.value as? ServiceAccountRemovalState.Confirming ?: return
        val accountId = state.account.uniqueId ?: return
        _serviceAccountRemovalState.value = ServiceAccountRemovalState.Removing(state.account)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.removeInstrumentServiceAccount(instrumentId, accountId)) {
                    is ApiResult.Success -> {
                        _serviceAccountsState.value = LoadState.Success(result.data)
                        _serviceAccountRemovalState.value = ServiceAccountRemovalState.Success(state.account)
                    }
                    is ApiResult.Error -> _serviceAccountRemovalState.value = ServiceAccountRemovalState.Confirming(
                        state.account,
                        serviceAccountMutationError(result.code, "remove")
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _serviceAccountRemovalState.value = ServiceAccountRemovalState.Confirming(state.account, "Connection error. Try again")
            }
        }
    }

    fun dismissRemoveServiceAccount() {
        if (_serviceAccountRemovalState.value !is ServiceAccountRemovalState.Removing) {
            _serviceAccountRemovalState.value = ServiceAccountRemovalState.Idle
        }
    }

    fun consumeServiceAccountRemovalSuccess() {
        if (_serviceAccountRemovalState.value is ServiceAccountRemovalState.Success) {
            _serviceAccountRemovalState.value = ServiceAccountRemovalState.Idle
        }
    }

    fun showOwnershipTransfer() {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canTransfer) return
        _ownershipTransferState.value = OwnershipTransferState.Selecting()
    }

    fun dismissOwnershipTransfer() {
        when (_ownershipTransferState.value) {
            is OwnershipTransferState.Previewing, is OwnershipTransferState.Transferring -> return
            else -> {
                ownershipSearchJob?.cancel()
                _ownershipTransferState.value = OwnershipTransferState.Idle
            }
        }
    }

    fun searchOwnershipCandidates(query: String) {
        val state = _ownershipTransferState.value as? OwnershipTransferState.Selecting ?: return
        ownershipSearchJob?.cancel()
        val draft = state.draft.copy(
            query = query,
            results = emptyList(),
            isSearching = query.length >= 3,
            searchError = null,
            actionError = null
        )
        _ownershipTransferState.value = OwnershipTransferState.Selecting(draft)
        if (query.length < 3) return
        ownershipSearchJob = viewModelScope.launch {
            delay(350)
            try {
                when (val result = apiClient.service.searchUsers(query)) {
                    is ApiResult.Success -> updateOwnershipDraft {
                        val ownerId = (_state.value as? InstrumentManageState.Loaded)?.instrument?.ownerOrcid
                        it.copy(results = result.data.filter { user -> user.uniqueId != null && user.uniqueId != ownerId }, isSearching = false)
                    }
                    is ApiResult.Error -> updateOwnershipDraft { it.copy(isSearching = false, searchError = "Search failed (${result.code})") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateOwnershipDraft { it.copy(isSearching = false, searchError = "Connection error. Try again") }
            }
        }
    }

    fun previewOwnershipTransfer(user: crucible.lens.data.model.User) {
        val newOwner = user.uniqueId ?: return
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canTransfer) return
        val draft = (_ownershipTransferState.value as? OwnershipTransferState.Selecting)?.draft ?: return
        ownershipSearchJob?.cancel()
        _ownershipTransferState.value = OwnershipTransferState.Previewing(draft)
        viewModelScope.launch {
            when (val result = apiClient.service.transferResourceOwnership(instrumentId, newOwner)) {
                is ApiResult.Success -> _ownershipTransferState.value = OwnershipTransferState.PreviewReady(result.data)
                is ApiResult.Error -> _ownershipTransferState.value = OwnershipTransferState.Selecting(
                    draft.copy(actionError = ownershipTransferError(result.code))
                )
            }
        }
    }

    fun confirmOwnershipTransfer() {
        val preview = (_ownershipTransferState.value as? OwnershipTransferState.PreviewReady)?.preview ?: return
        val newOwnerId = preview.newOwner.uniqueId ?: return
        _ownershipTransferState.value = OwnershipTransferState.Transferring(preview)
        viewModelScope.launch {
            when (val result = apiClient.service.transferResourceOwnership(instrumentId, newOwnerId, confirm = true)) {
                is ApiResult.Success -> {
                    repository.invalidateInstruments()
                    repository.invalidateInstrument(instrumentId)
                    val loaded = _state.value as? InstrumentManageState.Loaded
                    if (loaded != null) {
                        setLoaded(
                            loaded.instrument.copy(
                                ownerOrcid = result.data.newOwner.uniqueId,
                                owner = result.data.newOwner,
                                capabilities = loaded.instrument.capabilities.takeUnless { loaded.isOwner }
                            )
                        )
                    }
                    _ownershipTransferState.value = OwnershipTransferState.Success(result.data.newOwner)
                }
                is ApiResult.Error -> _ownershipTransferState.value = OwnershipTransferState.PreviewReady(
                    preview,
                    ownershipTransferError(result.code)
                )
            }
        }
    }

    fun consumeOwnershipTransferSuccess() {
        if (_ownershipTransferState.value is OwnershipTransferState.Success) {
            _ownershipTransferState.value = OwnershipTransferState.Idle
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

    private fun updateOwnershipDraft(update: (OwnershipTransferDraft) -> OwnershipTransferDraft) {
        val state = _ownershipTransferState.value as? OwnershipTransferState.Selecting ?: return
        _ownershipTransferState.value = OwnershipTransferState.Selecting(update(state.draft))
    }

    private fun updateStatus(status: InstrumentStatus) {
        val loaded = _state.value as? InstrumentManageState.Loaded ?: return
        if (!loaded.canChangeStatus) return
        val current = InstrumentStatus.fromApi(loaded.instrument.status)
        _statusState.value = InstrumentStatusState.Saving(status)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.updateInstrumentStatus(instrumentId, status)) {
                    is ApiResult.Success -> {
                        val previous = (_state.value as? InstrumentManageState.Loaded)?.instrument ?: loaded.instrument
                        val instrument = result.data.copy(
                            owner = result.data.owner ?: previous.owner,
                            status = result.data.status ?: previous.status,
                            capabilities = result.data.capabilities ?: previous.capabilities
                        )
                        repository.invalidateInstruments()
                        repository.cacheInstrumentDetail(instrument)
                        setLoaded(instrument)
                        _statusState.value = InstrumentStatusState.Success(status)
                    }
                    is ApiResult.Error -> _statusState.value = InstrumentStatusState.Selecting(
                        current = current,
                        selected = status,
                        error = statusError(result.code)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _statusState.value = InstrumentStatusState.Selecting(
                    current = current,
                    selected = status,
                    error = "Connection error. Try again"
                )
            }
        }
    }

    private fun setLoaded(instrument: Instrument) {
        _state.value = InstrumentManageState.Loaded(instrument, instrument.ownerOrcid == currentUserId)
    }

    private fun renameError(code: Int): String = when (code) {
        403 -> "You do not have permission to rename this instrument"
        409 -> "That instrument ID is already in use"
        422 -> "Use a valid instrument ID"
        else -> "Rename failed ($code)"
    }

    private fun editError(code: Int): String = when (code) {
        403 -> "You do not have permission to edit this instrument"
        422 -> "One or more instrument fields are invalid"
        else -> "Save failed ($code)"
    }

    private fun statusError(code: Int): String = when (code) {
        403 -> "You do not have permission to change this instrument's status"
        404 -> "This instrument is no longer available"
        422 -> "That instrument status is not supported"
        else -> "Status change failed ($code)"
    }

    private fun serviceAccountMutationError(code: Int, action: String): String = when (code) {
        403 -> "You do not have permission to manage instrument operators"
        404 -> if (action == "add") "Service account not found" else "This service account is no longer bound"
        else -> "Could not $action service account ($code)"
    }

    private fun ownershipTransferError(code: Int): String = when (code) {
        403 -> "You do not have permission to transfer ownership"
        404 -> "That account could not be found"
        409 -> "Ownership cannot be transferred to that account"
        else -> "Transfer failed ($code)"
    }
}
