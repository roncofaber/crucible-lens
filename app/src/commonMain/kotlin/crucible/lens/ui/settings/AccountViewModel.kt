package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.User
import crucible.lens.data.preferences.AppPreferences
import crucible.lens.data.preferences.accountIdFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

sealed class JoinRequestsUiState {
    object Idle : JoinRequestsUiState()
    object Loading : JoinRequestsUiState()
    data class Loaded(val refreshError: String? = null) : JoinRequestsUiState()
    data class Error(val message: String) : JoinRequestsUiState()
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

// Shared by AccountScreen, ProfileEditFields, and CompleteProfileScreen - lowercase, must start
// with a letter, 3-24 chars of letters/digits/hyphens/underscores.
val USERNAME_PATTERN = Regex("^[a-z][a-z0-9_-]{2,23}$")

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

// Shared by AccountScreen and CompleteProfileScreen - true for a blank draft too, since "not
// filled in yet" isn't a format error to surface.
val EditUiState.Editing.usernameFormatValid: Boolean
    get() = username.isBlank() || USERNAME_PATTERN.matches(username.lowercase())

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AccountViewModel(
    private val prefs: AppPreferences,
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _editState = MutableStateFlow<EditUiState>(EditUiState.Idle)
    val editState: StateFlow<EditUiState> = _editState.asStateFlow()

    // The most recent Editing draft, kept alive through the brief Saving state so callers don't
    // lose the fields being submitted while a save is in flight - Saving itself carries no draft.
    private val _lastDraft = MutableStateFlow<EditUiState.Editing?>(null)

    // Editing -> itself; Saving -> the draft that's mid-submit; SaveError -> its draft; else null.
    // Single source of truth for both AccountScreen and CompleteProfileScreen, which otherwise
    // each re-derived this identically from editState.
    val activeDraft: StateFlow<EditUiState.Editing?> = editState.map { state ->
        when (state) {
            is EditUiState.Editing -> state
            is EditUiState.Saving -> _lastDraft.value
            is EditUiState.SaveError -> state.draft
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentApiKey: StateFlow<String?> = prefs.apiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val joinRequests: StateFlow<List<JoinRequest>> = repository.observeMyJoinRequests()
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.getCachedMyJoinRequests().orEmpty())

    private val _joinRequestsState = MutableStateFlow<JoinRequestsUiState>(
        if (repository.getCachedMyJoinRequests() != null) JoinRequestsUiState.Loaded() else JoinRequestsUiState.Idle
    )
    val joinRequestsState: StateFlow<JoinRequestsUiState> = _joinRequestsState.asStateFlow()

    // Reviewer identities (ORCID -> User) for requests already approved/rejected, resolved so
    // "Reviewed by" can show a name/username instead of a bare ORCID.
    private val _reviewerInfo = MutableStateFlow<Map<String, User>>(emptyMap())
    val reviewerInfo: StateFlow<Map<String, User>> = _reviewerInfo.asStateFlow()

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
            repository.invalidateMyJoinRequests()
            _joinRequestsState.value = JoinRequestsUiState.Idle
            _reviewerInfo.value = emptyMap()
            return
        }
        when (val result = apiClient.service.getProfile()) {
            is ApiResult.Success -> {
                val user = result.data
                val accountId = accountIdFor(user)
                if (accountId == null) {
                    _profileState.value = ProfileUiState.Error("The server profile has no stable account identifier")
                    return
                }
                prefs.activateAccount(accountId)
                prefs.saveUserProfile(user)
                _profileState.value = ProfileUiState.Loaded(user)
            }
            is ApiResult.Error -> {
                if (_profileState.value !is ProfileUiState.Loaded) {
                    _profileState.value = ProfileUiState.Error("Could not load profile (${result.code})")
                }
            }
        }
        fetchJoinRequests()
    }

    private suspend fun fetchJoinRequests() {
        val cached = repository.getCachedMyJoinRequests()
        if (cached == null) _joinRequestsState.value = JoinRequestsUiState.Loading
        val requests = when (val result = repository.fetchMyJoinRequests(forceRefresh = true)) {
            is ApiResult.Success -> {
                _joinRequestsState.value = JoinRequestsUiState.Loaded()
                result.data
            }
            is ApiResult.Error -> {
                _joinRequestsState.value = if (cached != null) {
                    JoinRequestsUiState.Loaded("Could not refresh join requests (${result.code})")
                } else {
                    JoinRequestsUiState.Error("Could not load join requests (${result.code})")
                }
                cached ?: return
            }
        }
        val reviewerIds = requests.mapNotNull { it.reviewerId }.distinct()
        if (reviewerIds.isEmpty()) {
            _reviewerInfo.value = emptyMap()
        } else {
            (apiClient.service.resolveUsers(orcids = reviewerIds) as? ApiResult.Success)?.data
                ?.mapNotNull { (orcid, user) -> user?.let { orcid to it } }
                ?.toMap()
                ?.let { _reviewerInfo.value = it }
        }
    }

    fun retryJoinRequests() {
        viewModelScope.launch { fetchJoinRequests() }
    }

    fun retryLoad() {
        _profileState.value = ProfileUiState.Loading
        viewModelScope.launch { fetchProfileFromApi() }
    }

    fun startEdit() {
        val user = (_profileState.value as? ProfileUiState.Loaded)?.user ?: return
        val draft = EditUiState.Editing(
            firstName = user.firstName ?: "",
            lastName = user.lastName ?: "",
            email = user.email ?: "",
            username = user.username ?: ""
        )
        _lastDraft.value = draft
        _editState.value = draft
    }

    fun cancelEdit() {
        usernameCheckJob?.cancel()
        _editState.value = EditUiState.Idle
        _lastDraft.value = null
    }

    fun onFirstNameChanged(value: String) = updateDraft { it.copy(firstName = value) }
    fun onLastNameChanged(value: String) = updateDraft { it.copy(lastName = value) }
    fun onEmailChanged(value: String) = updateDraft { it.copy(email = value) }

    fun onUsernameChanged(value: String) {
        val currentUser = (_profileState.value as? ProfileUiState.Loaded)?.user
        usernameCheckJob?.cancel()
        updateDraft { it.copy(username = value, usernameCheck = UsernameCheckState.Idle) }
        if (value.isBlank()) return
        // Skip check if it matches the user's own current username
        if (value.lowercase() == currentUser?.username?.lowercase()) {
            updateDraft { it.copy(usernameCheck = UsernameCheckState.Own) }
            return
        }
        // Validate format client-side before hitting the API
        if (!USERNAME_PATTERN.matches(value.lowercase())) return
        updateDraft { it.copy(usernameCheck = UsernameCheckState.Checking) }
        usernameCheckJob = viewModelScope.launch {
            delay(500)
            val currentUniqueId = currentUser?.uniqueId ?: ""
            when (val result = apiClient.service.checkUsernameAvailability(value, currentUniqueId)) {
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
            when (val result = apiClient.service.updateProfile(
                firstName = draft.firstName.trim().ifBlank { null },
                lastName = draft.lastName.trim().ifBlank { null },
                email = draft.email.trim().ifBlank { null },
                username = draft.username.trim().ifBlank { null }
            )) {
                is ApiResult.Success -> {
                    val previous = (_profileState.value as? ProfileUiState.Loaded)?.user
                    val updatedUser = result.data.copy(capabilities = previous?.capabilities)
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

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            prefs.deactivateAccount()
            prefs.saveApiKey(key)
            apiClient.setApiKey(key)
            repository.invalidateAll()
            _editState.value = EditUiState.Idle
            if (key.isBlank()) {
                _profileState.value = ProfileUiState.NotLoggedIn
                _joinRequestsState.value = JoinRequestsUiState.Idle
                _reviewerInfo.value = emptyMap()
            } else {
                _profileState.value = ProfileUiState.Loading
                fetchProfileFromApi()
            }
        }
    }

    fun signOut() {
        usernameCheckJob?.cancel()
        viewModelScope.launch {
            prefs.deactivateAccount()
            prefs.clearApiKey()
            apiClient.setApiKey("")
            repository.invalidateAll()
            _profileState.value = ProfileUiState.NotLoggedIn
            _editState.value = EditUiState.Idle
            _lastDraft.value = null
            _joinRequestsState.value = JoinRequestsUiState.Idle
            _reviewerInfo.value = emptyMap()
        }
    }

    private fun currentDraft(): EditUiState.Editing? = when (val s = _editState.value) {
        is EditUiState.Editing -> s
        is EditUiState.SaveError -> s.draft
        else -> null
    }

    private fun updateDraft(update: (EditUiState.Editing) -> EditUiState.Editing) {
        val draft = currentDraft() ?: return
        val updated = update(draft)
        _lastDraft.value = updated
        _editState.value = updated
    }
}
