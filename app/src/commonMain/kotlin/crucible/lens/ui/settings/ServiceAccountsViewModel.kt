package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.PlatformRole
import crucible.lens.data.model.ServiceAccountCredential
import crucible.lens.data.model.ServiceAccountDetail
import crucible.lens.data.model.ServiceAccountRoleUpdateRequest
import crucible.lens.data.model.ServiceAccountSummary
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceAccountsUiState(
    val accounts: LoadState<List<ServiceAccountSummary>> = LoadState.Loading,
    val selected: ServiceAccountDetail? = null,
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,
    val isSavingRole: Boolean = false,
    val isRotatingKey: Boolean = false,
    val mutationError: String? = null,
    val credential: ServiceAccountCredential? = null
)

class ServiceAccountsViewModel(private val apiClient: ApiClient) : ViewModel() {
    private val _state = MutableStateFlow(ServiceAccountsUiState())
    val state: StateFlow<ServiceAccountsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(accounts = LoadState.Loading) }
        viewModelScope.launch {
            when (val result = apiClient.service.getServiceAccounts()) {
                is ApiResult.Success -> _state.update { it.copy(accounts = LoadState.Success(result.data)) }
                is ApiResult.Error -> _state.update { it.copy(accounts = LoadState.Error("Could not load service accounts (${result.code})")) }
            }
        }
    }

    fun open(account: ServiceAccountSummary) {
        _state.update { it.copy(isLoadingDetail = true, detailError = null, mutationError = null) }
        viewModelScope.launch {
            when (val result = apiClient.service.getServiceAccountDetail(account.uniqueId)) {
                is ApiResult.Success -> _state.update { it.copy(selected = result.data, isLoadingDetail = false) }
                is ApiResult.Error -> _state.update { it.copy(isLoadingDetail = false, detailError = "Could not load account (${result.code})") }
            }
        }
    }

    fun dismissDetail() {
        if (!_state.value.isSavingRole && !_state.value.isRotatingKey) {
            _state.update { it.copy(selected = null, detailError = null, mutationError = null) }
        }
    }

    fun create(username: String) {
        if (username.isBlank() || _state.value.isCreating) return
        _state.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.createServiceAccount(username.trim())) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isCreating = false, credential = result.data) }
                        load()
                    }
                    is ApiResult.Error -> _state.update { it.copy(isCreating = false, createError = "Create failed (${result.code})") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update { it.copy(isCreating = false, createError = "Connection error. Try again") }
            }
        }
    }

    fun clearCreateError() {
        _state.update { it.copy(createError = null) }
    }

    fun updateRole(role: PlatformRole) {
        val account = _state.value.selected ?: return
        if (role == PlatformRole.Support || _state.value.isSavingRole) return
        _state.update { it.copy(isSavingRole = true, mutationError = null) }
        viewModelScope.launch {
            when (val result = apiClient.service.updateServiceAccountRole(account.uniqueId, ServiceAccountRoleUpdateRequest(role))) {
                is ApiResult.Success -> {
                    _state.update { it.copy(selected = result.data, isSavingRole = false) }
                    load()
                }
                is ApiResult.Error -> _state.update { it.copy(isSavingRole = false, mutationError = "Role update failed (${result.code})") }
            }
        }
    }

    fun rotateKey() {
        val account = _state.value.selected ?: return
        if (_state.value.isRotatingKey) return
        _state.update { it.copy(isRotatingKey = true, mutationError = null) }
        viewModelScope.launch {
            when (val result = apiClient.service.rotateServiceAccountKey(account.uniqueId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isRotatingKey = false, credential = result.data, selected = null) }
                    load()
                }
                is ApiResult.Error -> _state.update { it.copy(isRotatingKey = false, mutationError = "Key rotation failed (${result.code})") }
            }
        }
    }

    fun dismissCredential() {
        _state.update { it.copy(credential = null) }
    }
}
