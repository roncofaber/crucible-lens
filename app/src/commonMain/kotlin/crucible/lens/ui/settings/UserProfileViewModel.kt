package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import crucible.lens.data.repository.CrucibleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UserProfileState {
    data object Loading : UserProfileState()
    data class Loaded(val user: User) : UserProfileState()
    data class Error(val message: String) : UserProfileState()
}

sealed class AddToProjectState {
    data object Idle : AddToProjectState()
    data class Adding(val project: Project) : AddToProjectState()
    data class Result(val project: Project, val success: Boolean) : AddToProjectState()
}

class UserProfileViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UserProfileState>(UserProfileState.Loading)
    val state: StateFlow<UserProfileState> = _state.asStateFlow()

    // The projects the current (logged-in) user is a member of - GET /projects is already scoped
    // server-side to member projects, so this is exactly the list "add to project" can offer,
    // with no extra fetch beyond what ProjectsListScreen already triggers/caches.
    private val _myProjects = MutableStateFlow<List<Project>>(emptyList())
    val myProjects: StateFlow<List<Project>> = _myProjects.asStateFlow()

    private val _addToProjectState = MutableStateFlow<AddToProjectState>(AddToProjectState.Idle)
    val addToProjectState: StateFlow<AddToProjectState> = _addToProjectState.asStateFlow()

    // Which of myProjects this profile's user already belongs to - checked lazily (only once the
    // "Add to Project" sheet is opened) via the same per-project member cache ManageProjectScreen
    // reads from, so this is free when that project's members were already fetched elsewhere.
    private val _memberProjectIds = MutableStateFlow<Set<String>>(emptySet())
    val memberProjectIds: StateFlow<Set<String>> = _memberProjectIds.asStateFlow()

    private val _isCheckingMembership = MutableStateFlow(false)
    val isCheckingMembership: StateFlow<Boolean> = _isCheckingMembership.asStateFlow()

    fun load(identifier: String) {
        _state.value = UserProfileState.Loading
        viewModelScope.launch {
            val isOrcid = identifier.contains("-") && identifier.length > 10
            val result: ApiResult<User> = if (isOrcid) {
                when (val r = apiClient.service.resolveUsers(orcids = listOf(identifier))) {
                    is ApiResult.Success -> r.data[identifier]?.let { ApiResult.Success(it) }
                        ?: ApiResult.Error(404, "User not found")
                    is ApiResult.Error -> r
                }
            } else {
                apiClient.service.getUserByUsername(identifier)
            }
            _state.value = when (result) {
                is ApiResult.Success -> UserProfileState.Loaded(result.data)
                is ApiResult.Error -> UserProfileState.Error(result.message)
            }
        }
        viewModelScope.launch {
            repository.observeProjects().collect { projects ->
                _myProjects.value = projects ?: emptyList()
            }
        }
        viewModelScope.launch { repository.fetchProjects() }
    }

    fun checkProjectMembership() {
        val user = (_state.value as? UserProfileState.Loaded)?.user ?: return
        _isCheckingMembership.value = true
        viewModelScope.launch {
            val memberIds = coroutineScope {
                _myProjects.value.map { project ->
                    async {
                        val members = (repository.fetchProjectMembers(project.projectId) as? ApiResult.Success)?.data
                            ?: emptyList()
                        val isMember = members.any { m ->
                            (user.uniqueId != null && m.uniqueId == user.uniqueId) ||
                                (user.username != null && m.username == user.username)
                        }
                        project.projectId.takeIf { isMember }
                    }
                }.mapNotNull { it.await() }
            }.toSet()
            _memberProjectIds.value = memberIds
            _isCheckingMembership.value = false
        }
    }

    fun addToProject(project: Project) {
        val username = (_state.value as? UserProfileState.Loaded)?.user?.username ?: return
        _addToProjectState.value = AddToProjectState.Adding(project)
        viewModelScope.launch {
            val result = apiClient.service.addProjectMember(project.projectId, username)
            val success = result is ApiResult.Success && result.data
            if (success) {
                repository.invalidateProjectMembers(project.projectId)
                _memberProjectIds.value = _memberProjectIds.value + project.projectId
            }
            _addToProjectState.value = AddToProjectState.Result(project, success)
        }
    }

    fun consumeAddToProjectResult() {
        _addToProjectState.value = AddToProjectState.Idle
    }
}
