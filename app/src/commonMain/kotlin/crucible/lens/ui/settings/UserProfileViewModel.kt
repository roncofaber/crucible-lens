package crucible.lens.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import crucible.lens.data.repository.CrucibleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    data class Added(val project: Project) : AddToProjectState()
    data class Error(val project: Project, val message: String) : AddToProjectState()
}

data class ProjectMembershipSnapshot(
    val memberProjectIds: Set<String> = emptySet(),
    val resolvedProjectIds: Set<String> = emptySet(),
    val failedProjectIds: Set<String> = emptySet()
)

sealed class ProjectMembershipState {
    data object Idle : ProjectMembershipState()
    data class Checking(val previous: ProjectMembershipSnapshot? = null) : ProjectMembershipState()
    data class Ready(val snapshot: ProjectMembershipSnapshot) : ProjectMembershipState()
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

    private val _membershipState = MutableStateFlow<ProjectMembershipState>(ProjectMembershipState.Idle)
    val membershipState: StateFlow<ProjectMembershipState> = _membershipState.asStateFlow()

    private var membershipJob: Job? = null

    fun load(identifier: String) {
        _state.value = UserProfileState.Loading
        membershipJob?.cancel()
        _membershipState.value = ProjectMembershipState.Idle
        _addToProjectState.value = AddToProjectState.Idle
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
        checkProjectMembership(projectIds = _myProjects.value.map { it.projectId }.toSet(), previous = null)
    }

    fun retryProjectMembership() {
        val previous = (_membershipState.value as? ProjectMembershipState.Ready)?.snapshot ?: return
        if (previous.failedProjectIds.isEmpty()) return
        checkProjectMembership(projectIds = previous.failedProjectIds, previous = previous)
    }

    private fun checkProjectMembership(projectIds: Set<String>, previous: ProjectMembershipSnapshot?) {
        val user = (_state.value as? UserProfileState.Loaded)?.user ?: return
        membershipJob?.cancel()
        if (projectIds.isEmpty()) {
            _membershipState.value = ProjectMembershipState.Ready(previous ?: ProjectMembershipSnapshot())
            return
        }
        _membershipState.value = ProjectMembershipState.Checking(previous)
        membershipJob = viewModelScope.launch {
            val checks = coroutineScope {
                _myProjects.value.filter { it.projectId in projectIds }.map { project ->
                    async {
                        try {
                            when (val result = repository.fetchProjectMembers(project.uniqueId)) {
                                is ApiResult.Success -> ProjectMembershipCheck(
                                    projectId = project.projectId,
                                    isMember = result.data.any { member ->
                                        (user.uniqueId != null && member.uniqueId == user.uniqueId) ||
                                            (user.username != null && member.username == user.username)
                                    }
                                )
                                is ApiResult.Error -> ProjectMembershipCheck(project.projectId, isMember = null)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            ProjectMembershipCheck(project.projectId, isMember = null)
                        }
                    }
                }.map { it.await() }
            }
            _membershipState.value = ProjectMembershipState.Ready(mergeProjectMembership(previous, checks))
        }
    }

    fun addToProject(project: Project) {
        val userId = (_state.value as? UserProfileState.Loaded)?.user?.uniqueId ?: return
        if (_addToProjectState.value is AddToProjectState.Adding) return
        _addToProjectState.value = AddToProjectState.Adding(project)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.addProjectMember(project.uniqueId, userId)) {
                    is ApiResult.Success -> {
                        repository.invalidateProjectMembers(project.uniqueId)
                        val current = (_membershipState.value as? ProjectMembershipState.Ready)?.snapshot
                            ?: ProjectMembershipSnapshot()
                        _membershipState.value = ProjectMembershipState.Ready(
                            current.copy(
                                memberProjectIds = current.memberProjectIds + project.projectId,
                                resolvedProjectIds = current.resolvedProjectIds + project.projectId,
                                failedProjectIds = current.failedProjectIds - project.projectId
                            )
                        )
                        _addToProjectState.value = AddToProjectState.Added(project)
                    }
                    is ApiResult.Error -> _addToProjectState.value = AddToProjectState.Error(
                        project,
                        addToProjectError(result.code)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _addToProjectState.value = AddToProjectState.Error(
                    project,
                    "Connection error. Check your network and try again"
                )
            }
        }
    }

    fun consumeAddToProjectResult() {
        if (_addToProjectState.value is AddToProjectState.Adding) return
        _addToProjectState.value = AddToProjectState.Idle
    }
}

internal data class ProjectMembershipCheck(
    val projectId: String,
    val isMember: Boolean?
)

internal fun mergeProjectMembership(
    previous: ProjectMembershipSnapshot?,
    checks: List<ProjectMembershipCheck>
): ProjectMembershipSnapshot {
    val checkedIds = checks.mapTo(mutableSetOf()) { it.projectId }
    val resolved = checks.filter { it.isMember != null }.mapTo(mutableSetOf()) { it.projectId }
    val members = checks.filter { it.isMember == true }.mapTo(mutableSetOf()) { it.projectId }
    val failed = checks.filter { it.isMember == null }.mapTo(mutableSetOf()) { it.projectId }
    return ProjectMembershipSnapshot(
        memberProjectIds = previous?.memberProjectIds.orEmpty() - checkedIds + members,
        resolvedProjectIds = previous?.resolvedProjectIds.orEmpty() - checkedIds + resolved,
        failedProjectIds = previous?.failedProjectIds.orEmpty() - checkedIds + failed
    )
}

internal fun addToProjectError(code: Int): String = when (code) {
    401 -> "Sign in again before adding a member"
    403 -> "You do not have permission to add members to this project"
    409 -> "This user may already be a project member"
    in 500..599 -> "Crucible service error ($code)"
    else -> "Could not add member ($code)"
}
