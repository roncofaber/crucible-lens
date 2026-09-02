package crucible.lens.ui.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessPrincipalKind
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Project
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.model.User
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.util.SEARCH_DEBOUNCE_MS
import crucible.lens.data.util.SEARCH_MIN_QUERY_LENGTH
import crucible.lens.data.util.accessGrantComparator
import crucible.lens.data.util.accessGrantMutationTarget
import crucible.lens.data.util.allowedGrantRoles
import crucible.lens.data.util.asGrantRole
import crucible.lens.data.util.userDisplayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ManageResourceAccessState {
    data object Loading : ManageResourceAccessState()
    data class Loaded(
        val resource: CrucibleResource,
        val grants: List<AccessGrant>,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
        val isChangingPublicAccess: Boolean = false,
        val publicAccessError: String? = null
    ) : ManageResourceAccessState() {
        val isPublic: Boolean get() = grants.any { it.principalType == AccessPrincipalType.Public }
        val allowedRoles: List<ResourceGrantRole> get() = allowedGrantRoles(resource.capabilities?.maxGrantRole)
    }
    data class Error(val message: String) : ManageResourceAccessState()
}

enum class AccessCandidateType(val label: String) {
    User("User or service account"),
    Project("Project")
}

data class AccessCandidate(
    val principalId: String,
    val requestReference: String,
    val displayName: String,
    val supportingText: String?,
    val principalType: AccessPrincipalType
)

data class AddAccessState(
    val candidateType: AccessCandidateType = AccessCandidateType.User,
    val query: String = "",
    val candidates: List<AccessCandidate> = emptyList(),
    val selected: AccessCandidate? = null,
    val role: ResourceGrantRole = ResourceGrantRole.Viewer,
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val searchError: String? = null,
    val saveError: String? = null
)

data class EditAccessState(
    val grant: AccessGrant,
    val role: ResourceGrantRole,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class RemoveAccessState(
    val grant: AccessGrant,
    val isRemoving: Boolean = false,
    val error: String? = null
)

class ManageResourceAccessViewModel(
    private val repository: CrucibleRepository,
    private val apiClient: ApiClient
) : ViewModel() {
    private val _state = MutableStateFlow<ManageResourceAccessState>(ManageResourceAccessState.Loading)
    val state: StateFlow<ManageResourceAccessState> = _state.asStateFlow()

    private val _addState = MutableStateFlow<AddAccessState?>(null)
    val addState: StateFlow<AddAccessState?> = _addState.asStateFlow()

    private val _editState = MutableStateFlow<EditAccessState?>(null)
    val editState: StateFlow<EditAccessState?> = _editState.asStateFlow()

    private val _removeState = MutableStateFlow<RemoveAccessState?>(null)
    val removeState: StateFlow<RemoveAccessState?> = _removeState.asStateFlow()

    private var resourceMfid: String = ""
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun init(resourceMfid: String) {
        if (this.resourceMfid == resourceMfid && _state.value !is ManageResourceAccessState.Error) return
        this.resourceMfid = resourceMfid
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val previous = _state.value as? ManageResourceAccessState.Loaded
        loadJob = viewModelScope.launch {
            _state.value = if (forceRefresh && previous != null) previous.copy(isRefreshing = true, refreshError = null)
            else ManageResourceAccessState.Loading
            val resource = repository.getCachedResource(resourceMfid) ?: when (val result = repository.fetchResourceByUuid(resourceMfid)) {
                is ResourceResult.Success -> result.resource
                is ResourceResult.Error -> {
                    _state.value = accessLoadFailure(previous, result.message)
                    return@launch
                }
                ResourceResult.Loading -> return@launch
            }
            if (resource.capabilities?.canManageAccess != true) {
                _state.value = ManageResourceAccessState.Error("You do not have permission to manage this resource's access")
                return@launch
            }
            when (val result = repository.fetchResourceAccess(resourceMfid, forceRefresh)) {
                is ApiResult.Success -> _state.value = ManageResourceAccessState.Loaded(
                    resource = resource,
                    grants = result.data.sortedWith(accessGrantComparator)
                )
                is ApiResult.Error -> _state.value = accessLoadFailure(previous, accessError(result.code, "load access"))
            }
        }
    }

    fun setPublicAccess(isPublic: Boolean) {
        val loaded = _state.value as? ManageResourceAccessState.Loaded ?: return
        if (loaded.isChangingPublicAccess || loaded.isPublic == isPublic) return
        _state.value = loaded.copy(isChangingPublicAccess = true, publicAccessError = null)
        viewModelScope.launch {
            when (val result = repository.setResourcePublic(resourceMfid, isPublic)) {
                is ApiResult.Success -> updateLoadedFromCache { it.copy(isChangingPublicAccess = false, publicAccessError = null) }
                is ApiResult.Error -> _state.value = loaded.copy(
                    isChangingPublicAccess = false,
                    publicAccessError = accessError(result.code, if (isPublic) "enable public access" else "disable public access")
                )
            }
        }
    }

    fun showAddAccess() {
        val loaded = _state.value as? ManageResourceAccessState.Loaded ?: return
        if (loaded.allowedRoles.isEmpty()) return
        _addState.value = AddAccessState(role = loaded.allowedRoles.first())
    }

    fun dismissAddAccess() {
        if (_addState.value?.isSaving != true) {
            searchJob?.cancel()
            _addState.value = null
        }
    }

    fun selectCandidateType(type: AccessCandidateType) {
        searchJob?.cancel()
        _addState.value = _addState.value?.copy(
            candidateType = type,
            query = "",
            candidates = emptyList(),
            selected = null,
            isSearching = false,
            searchError = null,
            saveError = null
        )
    }

    fun updateCandidateQuery(query: String) {
        val current = _addState.value ?: return
        searchJob?.cancel()
        _addState.value = current.copy(
            query = query,
            candidates = emptyList(),
            selected = null,
            isSearching = false,
            searchError = null,
            saveError = null
        )
        if (query.length < SEARCH_MIN_QUERY_LENGTH) return
        searchCandidates(query, current.candidateType)
    }

    fun retryCandidateSearch() {
        val current = _addState.value ?: return
        if (current.query.length < SEARCH_MIN_QUERY_LENGTH) return
        searchJob?.cancel()
        searchCandidates(current.query, current.candidateType)
    }

    fun selectCandidate(candidate: AccessCandidate) {
        searchJob?.cancel()
        _addState.value = _addState.value?.copy(
            query = candidate.requestReference,
            candidates = listOf(candidate),
            selected = candidate,
            isSearching = false,
            searchError = null,
            saveError = null
        )
    }

    fun selectAddRole(role: ResourceGrantRole) {
        val allowed = (_state.value as? ManageResourceAccessState.Loaded)?.allowedRoles.orEmpty()
        if (role !in allowed) return
        _addState.value = _addState.value?.copy(role = role, saveError = null)
    }

    fun addAccess() {
        val current = _addState.value ?: return
        val candidate = current.selected ?: return
        if (current.isSaving) return
        _addState.value = current.copy(isSaving = true, saveError = null)
        val kind = when (current.candidateType) {
            AccessCandidateType.User -> AccessPrincipalKind.Users
            AccessCandidateType.Project -> AccessPrincipalKind.Projects
        }
        viewModelScope.launch {
            when (val result = repository.setResourceAccess(resourceMfid, kind, candidate.requestReference, current.role)) {
                is ApiResult.Success -> {
                    updateLoadedFromCache()
                    _addState.value = null
                }
                is ApiResult.Error -> _addState.value = current.copy(
                    isSaving = false,
                    saveError = accessError(result.code, "grant access")
                )
            }
        }
    }

    fun editGrant(grant: AccessGrant) {
        val loaded = _state.value as? ManageResourceAccessState.Loaded ?: return
        val role = grant.permission.asGrantRole() ?: return
        if (accessGrantMutationTarget(grant) == null || role !in loaded.allowedRoles) return
        _editState.value = EditAccessState(grant, role)
    }

    fun selectEditRole(role: ResourceGrantRole) {
        val allowed = (_state.value as? ManageResourceAccessState.Loaded)?.allowedRoles.orEmpty()
        if (role !in allowed) return
        _editState.value = _editState.value?.copy(role = role, error = null)
    }

    fun saveEditedGrant() {
        val current = _editState.value ?: return
        val target = accessGrantMutationTarget(current.grant) ?: return
        if (current.isSaving) return
        _editState.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            when (val result = repository.setResourceAccess(resourceMfid, target.kind, target.principal, current.role)) {
                is ApiResult.Success -> {
                    updateLoadedFromCache()
                    _editState.value = null
                }
                is ApiResult.Error -> _editState.value = current.copy(
                    isSaving = false,
                    error = accessError(result.code, "change access")
                )
            }
        }
    }

    fun dismissEditGrant() {
        if (_editState.value?.isSaving != true) _editState.value = null
    }

    fun requestRemoveGrant() {
        val grant = _editState.value?.grant ?: return
        if (accessGrantMutationTarget(grant) == null) return
        _removeState.value = RemoveAccessState(grant)
    }

    fun confirmRemoveGrant() {
        val current = _removeState.value ?: return
        if (current.isRemoving) return
        _removeState.value = current.copy(isRemoving = true, error = null)
        viewModelScope.launch {
            when (val result = repository.revokeResourceAccess(resourceMfid, current.grant)) {
                is ApiResult.Success -> {
                    updateLoadedFromCache()
                    _removeState.value = null
                    _editState.value = null
                }
                is ApiResult.Error -> _removeState.value = current.copy(
                    isRemoving = false,
                    error = accessError(result.code, "remove access")
                )
            }
        }
    }

    fun dismissRemoveGrant() {
        if (_removeState.value?.isRemoving != true) _removeState.value = null
    }

    private fun searchCandidates(query: String, type: AccessCandidateType) {
        _addState.value = _addState.value?.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            try {
                val candidates = when (type) {
                    AccessCandidateType.User -> when (val result = apiClient.service.searchUsers(query)) {
                        is ApiResult.Success -> result.data.mapNotNull(::userCandidate)
                        is ApiResult.Error -> {
                            setSearchError(result.code)
                            return@launch
                        }
                    }
                    AccessCandidateType.Project -> when (val result = apiClient.service.searchProjects(query)) {
                        is ApiResult.Success -> result.data.map(::projectCandidate)
                        is ApiResult.Error -> {
                            setSearchError(result.code)
                            return@launch
                        }
                    }
                }
                _addState.value = _addState.value?.copy(
                    candidates = candidates,
                    isSearching = false,
                    searchError = null
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _addState.value = _addState.value?.copy(
                    candidates = emptyList(),
                    isSearching = false,
                    searchError = "Connection error. Check your network and try again"
                )
            }
        }
    }

    private fun setSearchError(code: Int) {
        _addState.value = _addState.value?.copy(
            candidates = emptyList(),
            isSearching = false,
            searchError = accessError(code, "search")
        )
    }

    private fun updateLoadedFromCache(transform: (ManageResourceAccessState.Loaded) -> ManageResourceAccessState.Loaded = { it }) {
        val loaded = _state.value as? ManageResourceAccessState.Loaded ?: return
        val grants = repository.getCachedResourceAccess(resourceMfid) ?: loaded.grants
        val resource = repository.getCachedResource(resourceMfid) ?: loaded.resource
        _state.value = transform(loaded.copy(resource = resource, grants = grants.sortedWith(accessGrantComparator)))
    }
}

internal fun userCandidate(user: User): AccessCandidate? {
    val principalId = user.uniqueId ?: return null
    return AccessCandidate(
        principalId = principalId,
        requestReference = principalId,
        displayName = userDisplayName(user),
        supportingText = user.username?.let { "@$it" },
        principalType = if (user.isServiceAccount) AccessPrincipalType.ServiceAccount else AccessPrincipalType.User
    )
}

internal fun projectCandidate(project: Project): AccessCandidate = AccessCandidate(
    principalId = project.uniqueId,
    requestReference = project.projectId,
    displayName = project.title ?: project.projectId,
    supportingText = project.projectId.takeUnless { it == project.title },
    principalType = AccessPrincipalType.Project
)

internal fun accessLoadFailure(
    previous: ManageResourceAccessState.Loaded?,
    message: String
): ManageResourceAccessState = previous?.copy(isRefreshing = false, refreshError = message)
    ?: ManageResourceAccessState.Error(message)

internal fun accessError(code: Int, action: String): String = when (code) {
    401 -> "Sign in again to $action"
    403 -> "You no longer have permission to $action"
    404 -> "The resource or selected principal was not found"
    409 -> "That access change conflicts with the current owner or resource state"
    422 -> "The requested access level is not valid"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not $action ($code)"
}
