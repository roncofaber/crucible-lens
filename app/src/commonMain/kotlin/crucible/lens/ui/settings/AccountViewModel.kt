package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.User
import crucible.lens.data.preferences.AppPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── State types ───────────────────────────────────────────────────────────────

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Loaded(val user: User) : ProfileUiState()
    object NotLoggedIn : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

enum class SaveErrorReason { UsernameTaken, Generic }

sealed class UsernameCheckState {
    object Idle : UsernameCheckState()
    object Checking : UsernameCheckState()
    object Available : UsernameCheckState()
    object Taken : UsernameCheckState()
    object Own : UsernameCheckState()
    object CheckError : UsernameCheckState()
}

sealed class EditUiState {
    object Idle : EditUiState()
    data class Editing(
        val firstName: String,
        val lastName: String,
        val email: String,
        val username: String,
        val usernameCheck: UsernameCheckState = UsernameCheckState.Idle
    ) : EditUiState()
    object Saving : EditUiState()
    data class SaveError(val draft: Editing, val reason: SaveErrorReason) : EditUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AccountViewModel(private val prefs: AppPreferences) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _editState = MutableStateFlow<EditUiState>(EditUiState.Idle)
    val editState: StateFlow<EditUiState> = _editState.asStateFlow()

    private var usernameCheckJob: Job? = null

    fun loadProfile() {
        viewModelScope.launch {
            // Instant: show cached profile from DataStore
            val cached = prefs.userProfile.first()
            if (cached != null) {
                _profileState.value = ProfileUiState.Loaded(cached)
            } else {
                val apiKey = prefs.apiKey.first()
                if (apiKey.isNullOrBlank()) {
                    _profileState.value = ProfileUiState.NotLoggedIn
                    return@launch
                }
                _profileState.value = ProfileUiState.Loading
            }
            // Background: refresh from API
            fetchProfileFromApi()
        }
    }

    private suspend fun fetchProfileFromApi() {
        val apiKey = prefs.apiKey.first()
        if (apiKey.isNullOrBlank()) {
            _profileState.value = ProfileUiState.NotLoggedIn
            return
        }
        when (val result = ApiClient.service.getProfile()) {
            is ApiResult.Success -> {
                val user = result.data
                prefs.saveUserProfile(user)
                _profileState.value = ProfileUiState.Loaded(user)
            }
            is ApiResult.Error -> {
                if (_profileState.value !is ProfileUiState.Loaded) {
                    _profileState.value = ProfileUiState.Error("Could not load profile (${result.code})")
                }
            }
        }
    }

    fun retryLoad() {
        _profileState.value = ProfileUiState.Loading
        viewModelScope.launch { fetchProfileFromApi() }
    }

    fun startEdit() {
        val user = (_profileState.value as? ProfileUiState.Loaded)?.user ?: return
        _editState.value = EditUiState.Editing(
            firstName = user.firstName ?: "",
            lastName = user.lastName ?: "",
            email = user.email ?: "",
            username = user.username ?: ""
        )
    }

    fun cancelEdit() {
        usernameCheckJob?.cancel()
        _editState.value = EditUiState.Idle
    }

    fun onFirstNameChanged(value: String) = updateDraft { it.copy(firstName = value) }
    fun onLastNameChanged(value: String) = updateDraft { it.copy(lastName = value) }
    fun onEmailChanged(value: String) = updateDraft { it.copy(email = value) }

    fun onUsernameChanged(value: String) {
        val currentUser = (_profileState.value as? ProfileUiState.Loaded)?.user
        updateDraft { it.copy(username = value, usernameCheck = UsernameCheckState.Idle) }
        usernameCheckJob?.cancel()
        if (value.isBlank()) return
        // Skip check if it matches the user's own current username
        if (value.lowercase() == currentUser?.username?.lowercase()) {
            updateDraft { it.copy(usernameCheck = UsernameCheckState.Own) }
            return
        }
        // Validate format client-side before hitting the API
        val pattern = Regex("^[a-z][a-z0-9_-]{2,31}$")
        if (!pattern.matches(value.lowercase())) return
        updateDraft { it.copy(usernameCheck = UsernameCheckState.Checking) }
        usernameCheckJob = viewModelScope.launch {
            delay(500)
            val currentUniqueId = currentUser?.uniqueId ?: ""
            when (val result = ApiClient.service.checkUsernameAvailability(value, currentUniqueId)) {
                is ApiResult.Success -> updateDraft {
                    it.copy(usernameCheck = if (result.data) UsernameCheckState.Available else UsernameCheckState.Taken)
                }
                is ApiResult.Error -> updateDraft { it.copy(usernameCheck = UsernameCheckState.CheckError) }
            }
        }
    }

    fun saveProfile() {
        val draft = currentDraft() ?: return
        if (_editState.value is EditUiState.Saving) return
        _editState.value = EditUiState.Saving
        viewModelScope.launch {
            when (val result = ApiClient.service.updateProfile(
                firstName = draft.firstName.trim().ifBlank { null },
                lastName = draft.lastName.trim().ifBlank { null },
                email = draft.email.trim().ifBlank { null },
                username = draft.username.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> {
                    val updatedUser = result.data
                    prefs.saveUserProfile(updatedUser)
                    _profileState.value = ProfileUiState.Loaded(updatedUser)
                    _editState.value = EditUiState.Idle
                }
                is ApiResult.Error -> {
                    val reason = if (result.code == 409) SaveErrorReason.UsernameTaken else SaveErrorReason.Generic
                    _editState.value = EditUiState.SaveError(draft, reason)
                }
            }
        }
    }

    fun dismissSaveError() {
        val saveError = _editState.value as? EditUiState.SaveError ?: return
        _editState.value = saveError.draft
    }

    fun signOut() {
        viewModelScope.launch {
            prefs.clearApiKey()
            prefs.clearUserProfile()
            ApiClient.setApiKey("")
            CacheManager.clearAll()
            _profileState.value = ProfileUiState.NotLoggedIn
            _editState.value = EditUiState.Idle
        }
    }

    private fun currentDraft(): EditUiState.Editing? = when (val s = _editState.value) {
        is EditUiState.Editing -> s
        is EditUiState.SaveError -> s.draft
        else -> null
    }

    private fun updateDraft(update: (EditUiState.Editing) -> EditUiState.Editing) {
        val draft = currentDraft() ?: return
        _editState.value = update(draft)
    }
}
