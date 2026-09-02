package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.JoinRequest
import crucible.lens.data.model.Project
import crucible.lens.data.model.TransferOwnershipResponse
import crucible.lens.data.model.User
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.util.ProjectMemberRole
import crucible.lens.data.util.SEARCH_DEBOUNCE_MS
import crucible.lens.data.util.SEARCH_MIN_QUERY_LENGTH
import crucible.lens.data.util.allowsAccessManagement
import crucible.lens.data.util.allowsEdit
import crucible.lens.data.util.allowsTransfer
import crucible.lens.data.util.assignableProjectRoles
import crucible.lens.data.util.canAddProjectMembers
import crucible.lens.data.util.canChangeProjectRole
import crucible.lens.data.util.canRenameProject
import crucible.lens.data.util.projectSlugValidationError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ManageProjectState {
    object Loading : ManageProjectState()
    data class Loaded(
        val project: Project,
        val members: List<User>,
        val isLead: Boolean,
        val currentUserRole: ProjectMemberRole?,
        val joinRequests: List<JoinRequest> = emptyList(),
        val requesterInfo: Map<String, User> = emptyMap(),
        val membersError: String? = null,
        val joinRequestsError: String? = null
    ) : ManageProjectState() {
        val canEdit: Boolean get() = project.capabilities.allowsEdit(isLead)
        val canManageAccess: Boolean get() = project.capabilities.allowsAccessManagement(currentUserRole.canAddProjectMembers())
        val canRename: Boolean get() = project.capabilities.allowsEdit(currentUserRole.canRenameProject())
        val canTransfer: Boolean get() = project.capabilities.allowsTransfer(isLead)
        val canReviewJoinRequests: Boolean get() = isLead || canTransfer
        val canLeave: Boolean get() = currentUserRole != null && !isLead
        val assignableRoles: List<ProjectMemberRole> get() = project.capabilities.assignableProjectRoles(currentUserRole)

        fun canChangeMemberRole(role: ProjectMemberRole?): Boolean = project.capabilities.canChangeProjectRole(role, currentUserRole)
    }
    data class Error(val message: String) : ManageProjectState()
}

sealed class ProjectEditState {
    object Idle : ProjectEditState()
    data class Editing(
        val title: String,
        val organization: String
    ) : ProjectEditState()
    object Saving : ProjectEditState()
    data class SaveError(val draft: Editing, val message: String) : ProjectEditState()
}

data class LeadershipTransferDraft(
    val query: String = "",
    val results: List<User> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val actionError: String? = null
)

sealed class LeadershipTransferState {
    data object Idle : LeadershipTransferState()
    data class Selecting(val draft: LeadershipTransferDraft = LeadershipTransferDraft()) : LeadershipTransferState()
    data class Previewing(val draft: LeadershipTransferDraft) : LeadershipTransferState()
    data class PreviewReady(val preview: TransferOwnershipResponse, val error: String? = null) : LeadershipTransferState()
    data class Transferring(val preview: TransferOwnershipResponse) : LeadershipTransferState()
    data class Success(val newOwner: User) : LeadershipTransferState()
}

sealed class ProjectRenameState {
    data object Idle : ProjectRenameState()
    data class Editing(val value: String, val error: String? = null) : ProjectRenameState()
    data class Saving(val value: String) : ProjectRenameState()
    data class Success(val projectSlug: String) : ProjectRenameState()
}

sealed class MemberRoleState {
    data object Idle : MemberRoleState()
    data class Saving(val userId: String, val role: ProjectMemberRole) : MemberRoleState()
    data class Error(val userId: String, val message: String) : MemberRoleState()
}

class ManageProjectViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ManageProjectState>(ManageProjectState.Loading)
    val state: StateFlow<ManageProjectState> = _state.asStateFlow()

    private val _editState = MutableStateFlow<ProjectEditState>(ProjectEditState.Idle)
    val editState: StateFlow<ProjectEditState> = _editState.asStateFlow()

    private val _leadershipTransferState = MutableStateFlow<LeadershipTransferState>(LeadershipTransferState.Idle)
    val leadershipTransferState: StateFlow<LeadershipTransferState> = _leadershipTransferState.asStateFlow()

    private val _projectRenameState = MutableStateFlow<ProjectRenameState>(ProjectRenameState.Idle)
    val projectRenameState: StateFlow<ProjectRenameState> = _projectRenameState.asStateFlow()

    private val _memberRoleState = MutableStateFlow<MemberRoleState>(MemberRoleState.Idle)
    val memberRoleState: StateFlow<MemberRoleState> = _memberRoleState.asStateFlow()

    private val _pendingRemove = MutableStateFlow<User?>(null)
    val pendingRemove: StateFlow<User?> = _pendingRemove.asStateFlow()

    private val _removingMemberOrcid = MutableStateFlow<String?>(null)
    val removingMemberOrcid: StateFlow<String?> = _removingMemberOrcid.asStateFlow()

    private val _removeMemberError = MutableStateFlow<String?>(null)
    val removeMemberError: StateFlow<String?> = _removeMemberError.asStateFlow()

    private val _leaveError = MutableStateFlow<String?>(null)
    val leaveError: StateFlow<String?> = _leaveError.asStateFlow()

    private val _isAddMemberSheetVisible = MutableStateFlow(false)
    val isAddMemberSheetVisible: StateFlow<Boolean> = _isAddMemberSheetVisible.asStateFlow()

    private val _memberSearchResults = MutableStateFlow<List<User>>(emptyList())
    val memberSearchResults: StateFlow<List<User>> = _memberSearchResults.asStateFlow()

    private val _isMemberSearching = MutableStateFlow(false)
    val isMemberSearching: StateFlow<Boolean> = _isMemberSearching.asStateFlow()

    private val _memberSearchError = MutableStateFlow<String?>(null)
    val memberSearchError: StateFlow<String?> = _memberSearchError.asStateFlow()

    private val _addMemberError = MutableStateFlow<String?>(null)
    val addMemberError: StateFlow<String?> = _addMemberError.asStateFlow()

    private val _reviewingJoinRequestId = MutableStateFlow<Int?>(null)
    val reviewingJoinRequestId: StateFlow<Int?> = _reviewingJoinRequestId.asStateFlow()

    private val _projectActionError = MutableStateFlow<String?>(null)
    val projectActionError: StateFlow<String?> = _projectActionError.asStateFlow()

    private val _addingMemberId = MutableStateFlow<String?>(null)
    val addingMemberId: StateFlow<String?> = _addingMemberId.asStateFlow()

    private var projectId: String = ""
    private var projectSlug: String = ""
    private var currentUserOrcid: String? = null
    private var memberSearchJob: Job? = null
    private var leadershipSearchJob: Job? = null

    fun init(projectId: String, currentUserOrcid: String?) {
        this.projectId = projectId
        this.currentUserOrcid = currentUserOrcid
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ManageProjectState.Loading
            try {
                val project = when (val result = repository.fetchProject(projectId, forceRefresh = true)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Error -> {
                        _state.value = ManageProjectState.Error(
                            if (result.code == 404) "Project not found" else "Failed to load project (${result.code})"
                        )
                        return@launch
                    }
                }
                projectSlug = project.projectId
                val membersResult = repository.fetchProjectMembers(projectId, forceRefresh = true)
                val members = (membersResult as? ApiResult.Success)?.data ?: emptyList()
                val membersError = (membersResult as? ApiResult.Error)?.let { "Failed to load members (${it.code})" }
                val isLead = isCurrentUserLead(project)
                val currentUserRole = currentUserRole(project, members, isLead)
                val canReviewJoinRequests = project.capabilities.allowsTransfer(isLead)
                val joinRequestsResult = if (canReviewJoinRequests) {
                    apiClient.service.getJoinRequests(groupName = projectSlug, status = "pending")
                } else {
                    ApiResult.Success<List<JoinRequest>>(emptyList())
                }
                val joinRequests = (joinRequestsResult as? ApiResult.Success)?.data ?: emptyList()
                val joinRequestsError = (joinRequestsResult as? ApiResult.Error)?.let { "Failed to load pending requests (${it.code})" }
                val requesterInfo = if (joinRequests.isNotEmpty()) {
                    (apiClient.service.resolveUsers(orcids = joinRequests.map { it.requesterId }) as? ApiResult.Success)?.data
                        ?.mapNotNull { (orcid, user) -> user?.let { orcid to it } }?.toMap() ?: emptyMap()
                } else emptyMap()
                _state.value = ManageProjectState.Loaded(
                    project = project,
                    members = members,
                    isLead = isLead,
                    currentUserRole = currentUserRole,
                    joinRequests = joinRequests,
                    requesterInfo = requesterInfo,
                    membersError = membersError,
                    joinRequestsError = joinRequestsError
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = ManageProjectState.Error("Connection error. Check your network and try again")
            }
        }
    }

    fun retryMembers() {
        if (_state.value !is ManageProjectState.Loaded) return
        viewModelScope.launch {
            try {
                when (val result = repository.fetchProjectMembers(projectId, forceRefresh = true)) {
                    is ApiResult.Success -> (_state.value as? ManageProjectState.Loaded)?.let {
                        _state.value = it.copy(
                            members = result.data,
                            currentUserRole = currentUserRole(it.project, result.data, it.isLead),
                            membersError = null
                        )
                    }
                    is ApiResult.Error -> (_state.value as? ManageProjectState.Loaded)?.let {
                        _state.value = it.copy(membersError = "Failed to load members (${result.code})")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                (_state.value as? ManageProjectState.Loaded)?.let {
                    _state.value = it.copy(membersError = "Connection error. Check your network and try again")
                }
            }
        }
    }

    fun retryJoinRequests() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canReviewJoinRequests) return
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.getJoinRequests(groupName = projectSlug, status = "pending")) {
                    is ApiResult.Success -> {
                        val requesterInfo = if (result.data.isNotEmpty()) {
                            (apiClient.service.resolveUsers(orcids = result.data.map { it.requesterId }) as? ApiResult.Success)?.data
                                ?.mapNotNull { (orcid, user) -> user?.let { orcid to it } }?.toMap() ?: emptyMap()
                        } else emptyMap()
                        val current = _state.value as? ManageProjectState.Loaded ?: return@launch
                        _state.value = current.copy(
                            joinRequests = result.data,
                            requesterInfo = requesterInfo,
                            joinRequestsError = null
                        )
                    }
                    is ApiResult.Error -> (_state.value as? ManageProjectState.Loaded)?.let {
                        _state.value = it.copy(joinRequestsError = "Failed to load pending requests (${result.code})")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                (_state.value as? ManageProjectState.Loaded)?.let {
                    _state.value = it.copy(joinRequestsError = "Connection error. Check your network and try again")
                }
            }
        }
    }

    // ORCID (Project.projectLeadOrcid) is the source of truth for identity, not username —
    // usernames can be absent or stale, while ORCID is the account's unique, unchanging id.
    private fun isCurrentUserLead(project: Project): Boolean {
        val orcid = currentUserOrcid ?: return false
        return project.projectLeadOrcid == orcid || project.lead?.uniqueId == orcid
    }

    private fun currentUserRole(project: Project, members: List<User>, isLead: Boolean): ProjectMemberRole? {
        if (isLead) return ProjectMemberRole.Owner
        val memberRole = members.firstOrNull { it.uniqueId == currentUserOrcid }?.role
        return ProjectMemberRole.fromApi(memberRole)
    }

    // ── Edit project info ─────────────────────────────────────────────────────

    fun startEdit() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canEdit) return
        _editState.value = ProjectEditState.Editing(
            title = loaded.project.title ?: "",
            organization = loaded.project.organization ?: ""
        )
    }

    fun cancelEdit() {
        _editState.value = ProjectEditState.Idle
    }

    fun onTitleChanged(value: String) = updateEditDraft { it.copy(title = value) }
    fun onOrganizationChanged(value: String) = updateEditDraft { it.copy(organization = value) }

    fun saveProject() {
        val loadedState = _state.value as? ManageProjectState.Loaded ?: return
        if (!loadedState.canEdit) return
        val draft = currentEditDraft() ?: return
        if (_editState.value is ProjectEditState.Saving) return
        _editState.value = ProjectEditState.Saving
        viewModelScope.launch {
            val loaded = _state.value as? ManageProjectState.Loaded
            val result = apiClient.service.updateProject(
                projectReference = projectId,
                title = draft.title.trim().ifBlank { null },
                organization = draft.organization.trim().ifBlank { null }
            )
            when (result) {
                is ApiResult.Success -> {
                    // Both caches: invalidateProjects() only clears the projects *list* (keyed on
                    // Unit). ProjectDetailScreen's header reads observeProject(projectId) from the
                    // separate per-project cache, so an edited title or organization would stay
                    // stale there for the rest of the TTL without this.
                    repository.invalidateProjects()
                    repository.invalidateProject(projectId)
                    val members = loaded?.members ?: emptyList()
                    val project = result.data.copy(capabilities = result.data.capabilities ?: loaded?.project?.capabilities)
                    val isLead = isCurrentUserLead(project)
                    _state.value = ManageProjectState.Loaded(
                        project, members, isLead,
                        currentUserRole = currentUserRole(project, members, isLead),
                        joinRequests = loaded?.joinRequests ?: emptyList(),
                        requesterInfo = loaded?.requesterInfo ?: emptyMap(),
                        membersError = loaded?.membersError,
                        joinRequestsError = loaded?.joinRequestsError
                    )
                    _editState.value = ProjectEditState.Idle
                }
                is ApiResult.Error -> _editState.value = ProjectEditState.SaveError(draft, "Save failed (${result.code})")
            }
        }
    }

    // ── Member management ─────────────────────────────────────────────────────

    fun showProjectRename() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canRename) return
        _projectRenameState.value = ProjectRenameState.Editing(loaded.project.projectId)
    }

    fun updateProjectRename(value: String) {
        val state = _projectRenameState.value as? ProjectRenameState.Editing ?: return
        _projectRenameState.value = state.copy(value = value, error = null)
    }

    fun dismissProjectRename() {
        if (_projectRenameState.value !is ProjectRenameState.Saving) {
            _projectRenameState.value = ProjectRenameState.Idle
        }
    }

    fun renameProject() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canRename) return
        val state = _projectRenameState.value as? ProjectRenameState.Editing ?: return
        val newSlug = state.value.trim()
        val validationError = projectSlugValidationError(newSlug)
        if (validationError != null) {
            _projectRenameState.value = state.copy(error = validationError)
            return
        }
        if (newSlug == projectSlug) {
            _projectRenameState.value = ProjectRenameState.Idle
            return
        }
        _projectRenameState.value = ProjectRenameState.Saving(newSlug)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.updateProject(projectReference = projectId, projectSlug = newSlug)) {
                    is ApiResult.Success -> {
                        projectSlug = result.data.projectId
                        repository.invalidateProjects()
                        repository.invalidateProject(projectId)
                        val loaded = _state.value as? ManageProjectState.Loaded
                        if (loaded != null) {
                            _state.value = loaded.copy(
                                project = result.data.copy(capabilities = result.data.capabilities ?: loaded.project.capabilities)
                            )
                        }
                        _projectRenameState.value = ProjectRenameState.Success(result.data.projectId)
                    }
                    is ApiResult.Error -> _projectRenameState.value = ProjectRenameState.Editing(
                        value = newSlug,
                        error = projectRenameError(result.code)
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _projectRenameState.value = ProjectRenameState.Editing(newSlug, "Connection error. Try again")
            }
        }
    }

    fun consumeProjectRenameSuccess() {
        if (_projectRenameState.value is ProjectRenameState.Success) {
            _projectRenameState.value = ProjectRenameState.Idle
        }
    }

    fun showLeadershipTransfer() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canTransfer) return
        _leadershipTransferState.value = LeadershipTransferState.Selecting()
    }

    fun dismissLeadershipTransfer() {
        when (_leadershipTransferState.value) {
            is LeadershipTransferState.Previewing, is LeadershipTransferState.Transferring -> return
            else -> {
                leadershipSearchJob?.cancel()
                _leadershipTransferState.value = LeadershipTransferState.Idle
            }
        }
    }

    fun searchLeadershipCandidates(query: String) {
        val state = _leadershipTransferState.value as? LeadershipTransferState.Selecting ?: return
        leadershipSearchJob?.cancel()
        val draft = state.draft.copy(
            query = query,
            results = emptyList(),
            isSearching = query.length >= SEARCH_MIN_QUERY_LENGTH,
            searchError = null,
            actionError = null
        )
        _leadershipTransferState.value = LeadershipTransferState.Selecting(draft)
        if (query.length < SEARCH_MIN_QUERY_LENGTH) return
        leadershipSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            try {
                when (val result = apiClient.service.searchUsers(query)) {
                    is ApiResult.Success -> {
                        val currentLead = (_state.value as? ManageProjectState.Loaded)?.project?.projectLeadOrcid
                        updateLeadershipDraft {
                            it.copy(results = result.data.filter { user -> user.uniqueId != null && user.uniqueId != currentLead }, isSearching = false)
                        }
                    }
                    is ApiResult.Error -> updateLeadershipDraft {
                        it.copy(isSearching = false, searchError = "Search failed (${result.code})")
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                updateLeadershipDraft { it.copy(isSearching = false, searchError = "Connection error. Try again") }
            }
        }
    }

    fun previewLeadershipTransfer(user: User) {
        val newOwner = user.uniqueId ?: return
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canTransfer) return
        val draft = (_leadershipTransferState.value as? LeadershipTransferState.Selecting)?.draft ?: return
        leadershipSearchJob?.cancel()
        _leadershipTransferState.value = LeadershipTransferState.Previewing(draft)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.transferResourceOwnership(projectId, newOwner)) {
                    is ApiResult.Success -> _leadershipTransferState.value = LeadershipTransferState.PreviewReady(result.data)
                    is ApiResult.Error -> _leadershipTransferState.value = LeadershipTransferState.Selecting(
                        draft.copy(actionError = leadershipTransferError(result.code))
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _leadershipTransferState.value = LeadershipTransferState.Selecting(
                    draft.copy(actionError = "Connection error. Try again")
                )
            }
        }
    }

    fun confirmLeadershipTransfer() {
        val preview = (_leadershipTransferState.value as? LeadershipTransferState.PreviewReady)?.preview ?: return
        val newOwnerId = preview.newOwner.uniqueId ?: return
        _leadershipTransferState.value = LeadershipTransferState.Transferring(preview)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.transferResourceOwnership(projectId, newOwnerId, confirm = true)) {
                    is ApiResult.Success -> {
                        repository.invalidateProjects()
                        repository.invalidateProject(projectId)
                        repository.invalidateProjectMembers(projectId)
                        val loaded = _state.value as? ManageProjectState.Loaded
                        if (loaded != null) {
                            val project = loaded.project.copy(
                                projectLeadOrcid = result.data.newOwner.uniqueId,
                                lead = result.data.newOwner,
                                capabilities = loaded.project.capabilities.takeUnless { loaded.isLead }
                            )
                            val members = if (result.data.newOwner.uniqueId != null && loaded.members.none { it.uniqueId == result.data.newOwner.uniqueId }) {
                                loaded.members + result.data.newOwner.copy(role = ProjectMemberRole.Owner.apiValue)
                            } else {
                                loaded.members.map { member ->
                                    when (member.uniqueId) {
                                        result.data.previousOwner?.uniqueId -> member.copy(role = ProjectMemberRole.Admin.apiValue)
                                        result.data.newOwner.uniqueId -> member.copy(role = ProjectMemberRole.Owner.apiValue)
                                        else -> member
                                    }
                                }
                            }
                            val isLead = isCurrentUserLead(project)
                            val canReviewJoinRequests = project.capabilities.allowsTransfer(isLead)
                            _state.value = loaded.copy(
                                project = project,
                                members = members,
                                isLead = isLead,
                                currentUserRole = currentUserRole(project, members, isLead),
                                joinRequests = if (canReviewJoinRequests) loaded.joinRequests else emptyList(),
                                requesterInfo = if (canReviewJoinRequests) loaded.requesterInfo else emptyMap()
                            )
                        }
                        _leadershipTransferState.value = LeadershipTransferState.Success(result.data.newOwner)
                    }
                    is ApiResult.Error -> _leadershipTransferState.value = LeadershipTransferState.PreviewReady(
                        preview = preview,
                        error = leadershipTransferError(result.code)
                    )
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _leadershipTransferState.value = LeadershipTransferState.PreviewReady(
                    preview = preview,
                    error = "Connection error. Try again"
                )
            }
        }
    }

    fun consumeLeadershipTransferSuccess() {
        if (_leadershipTransferState.value is LeadershipTransferState.Success) {
            _leadershipTransferState.value = LeadershipTransferState.Idle
        }
    }

    fun showAddMemberSheet() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canManageAccess || loaded.assignableRoles.isEmpty()) return
        _isAddMemberSheetVisible.value = true
        _memberSearchResults.value = emptyList()
        _memberSearchError.value = null
        _addMemberError.value = null
    }

    fun hideAddMemberSheet() {
        _isAddMemberSheetVisible.value = false
        memberSearchJob?.cancel()
        _isMemberSearching.value = false
    }

    fun searchMembers(query: String) {
        memberSearchJob?.cancel()
        _memberSearchError.value = null
        if (query.length < SEARCH_MIN_QUERY_LENGTH) { _memberSearchResults.value = emptyList(); return }
        memberSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _isMemberSearching.value = true
            try {
                when (val result = apiClient.service.searchUsers(query)) {
                    is ApiResult.Success -> _memberSearchResults.value = result.data
                    is ApiResult.Error -> {
                        _memberSearchResults.value = emptyList()
                        _memberSearchError.value = "Search failed (${result.code})"
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _memberSearchResults.value = emptyList()
                _memberSearchError.value = "Connection error. Try again"
            } finally {
                _isMemberSearching.value = false
            }
        }
    }

    // Sheet stays open on success so multiple members can be added in one visit — see
    // MembersCard/AddMemberSheet's WhatsApp-style "add participants" flow in ManageProjectScreen.kt.
    fun addMember(user: User, role: ProjectMemberRole) {
        val userId = user.uniqueId ?: return
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (role !in loaded.assignableRoles) return
        if (_addingMemberId.value != null) return
        viewModelScope.launch {
            _addingMemberId.value = userId
            _addMemberError.value = null
            try {
                when (val result = apiClient.service.addProjectMember(projectId, userId, role.apiValue)) {
                    is ApiResult.Success -> {
                        repository.invalidateProjectMembers(projectId)
                        val current = _state.value as? ManageProjectState.Loaded
                        if (current != null) {
                            _state.value = current.copy(
                                members = result.data,
                                currentUserRole = currentUserRole(current.project, result.data, current.isLead)
                            )
                        }
                    }
                    is ApiResult.Error -> _addMemberError.value = "Could not add member (${result.code})"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _addMemberError.value = "Connection error. Try again"
            } finally {
                _addingMemberId.value = null
            }
        }
    }

    fun changeMemberRole(user: User, role: ProjectMemberRole) {
        if (_memberRoleState.value is MemberRoleState.Saving) return
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        val userId = user.uniqueId ?: return
        val currentRole = ProjectMemberRole.fromApi(user.role)
        if (!loaded.canChangeMemberRole(currentRole)) return
        if (role !in loaded.assignableRoles) return
        if (role == currentRole) {
            _memberRoleState.value = MemberRoleState.Idle
            return
        }
        _memberRoleState.value = MemberRoleState.Saving(userId, role)
        viewModelScope.launch {
            try {
                when (val result = apiClient.service.updateProjectMemberRole(projectId, userId, role.apiValue)) {
                    is ApiResult.Success -> {
                        repository.invalidateProjectMembers(projectId)
                        val current = _state.value as? ManageProjectState.Loaded
                        if (current != null) {
                            _state.value = current.copy(
                                members = result.data,
                                currentUserRole = currentUserRole(current.project, result.data, current.isLead)
                            )
                        }
                        _memberRoleState.value = MemberRoleState.Idle
                    }
                    is ApiResult.Error -> _memberRoleState.value = MemberRoleState.Error(userId, "Could not change role (${result.code})")
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _memberRoleState.value = MemberRoleState.Error(userId, "Connection error. Try again")
            }
        }
    }

    fun clearMemberRoleError() {
        if (_memberRoleState.value is MemberRoleState.Error) _memberRoleState.value = MemberRoleState.Idle
    }

    fun confirmRemove(user: User) {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canTransfer || user.uniqueId == loaded.project.projectLeadOrcid) return
        _removeMemberError.value = null
        _pendingRemove.value = user
    }

    fun cancelRemove() {
        if (_removingMemberOrcid.value == null) {
            _removeMemberError.value = null
            _pendingRemove.value = null
        }
    }

    // ── Join request review ───────────────────────────────────────────────────

    fun approveJoinRequest(request: JoinRequest) = reviewJoinRequest(request, "approved")
    fun rejectJoinRequest(request: JoinRequest) = reviewJoinRequest(request, "rejected")

    private fun reviewJoinRequest(request: JoinRequest, status: String) {
        if (_reviewingJoinRequestId.value != null) return
        viewModelScope.launch {
            _reviewingJoinRequestId.value = request.id
            _projectActionError.value = null
            try {
                when (val result = apiClient.service.reviewJoinRequest(request.id, status)) {
                    is ApiResult.Success -> {
                        val loaded = _state.value as? ManageProjectState.Loaded ?: return@launch
                        val updatedRequests = loaded.joinRequests.filter { it.id != request.id }
                        if (status == "approved") {
                            _state.value = loaded.copy(joinRequests = updatedRequests)
                            repository.invalidateProjectMembers(projectId)
                            when (val membersResult = repository.fetchProjectMembers(projectId, forceRefresh = true)) {
                                is ApiResult.Success -> (_state.value as? ManageProjectState.Loaded)?.let {
                                    _state.value = it.copy(members = membersResult.data, membersError = null)
                                }
                                is ApiResult.Error -> (_state.value as? ManageProjectState.Loaded)?.let {
                                    _state.value = it.copy(membersError = "Request approved, but members could not refresh (${membersResult.code})")
                                }
                            }
                        } else {
                            _state.value = loaded.copy(joinRequests = updatedRequests)
                        }
                    }
                    is ApiResult.Error -> _projectActionError.value = "Could not review request (${result.code})"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _projectActionError.value = "Connection error. Try again"
            } finally {
                _reviewingJoinRequestId.value = null
            }
        }
    }

    fun removeMember() {
        val user = _pendingRemove.value ?: return
        val orcid = user.uniqueId ?: return
        if (_removingMemberOrcid.value != null) return
        viewModelScope.launch {
            _removingMemberOrcid.value = orcid
            _removeMemberError.value = null
            try {
                when (val result = apiClient.service.removeProjectMember(projectId, orcid)) {
                    is ApiResult.Success -> {
                        repository.invalidateProjectMembers(projectId)
                        val loaded = _state.value as? ManageProjectState.Loaded
                        if (loaded != null) {
                            _state.value = loaded.copy(
                                members = result.data,
                                currentUserRole = currentUserRole(loaded.project, result.data, loaded.isLead)
                            )
                        }
                        _pendingRemove.value = null
                    }
                    is ApiResult.Error -> _removeMemberError.value = "Could not remove member (${result.code})"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _removeMemberError.value = "Connection error. Try again"
            } finally {
                _removingMemberOrcid.value = null
            }
        }
    }

    fun dismissProjectActionError() { _projectActionError.value = null }

    // ── Leave project (any member, except the lead — must transfer leadership first) ────────

    /**
     * The project lead can't leave server-side (409) — the overflow menu item is disabled for
     * the lead so this shouldn't normally be reachable, but the [isLead] check here is defense
     * in depth against a stale UI state.
     */
    fun leaveProject(onLeft: () -> Unit) {
        val orcid = currentUserOrcid ?: return
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        if (!loaded.canLeave) return
        viewModelScope.launch {
            when (val result = apiClient.service.removeProjectMember(projectId, orcid)) {
                is ApiResult.Success -> {
                    repository.invalidateProjects()
                    repository.invalidateProjectMembers(projectId)
                    onLeft()
                }
                is ApiResult.Error -> _leaveError.value = "Failed to leave project (${result.code})"
            }
        }
    }

    fun dismissLeaveError() { _leaveError.value = null }

    private fun updateLeadershipDraft(update: (LeadershipTransferDraft) -> LeadershipTransferDraft) {
        val state = _leadershipTransferState.value as? LeadershipTransferState.Selecting ?: return
        _leadershipTransferState.value = LeadershipTransferState.Selecting(update(state.draft))
    }

    private fun leadershipTransferError(code: Int): String = when (code) {
        403 -> "You do not have permission to transfer leadership"
        404 -> "The selected user is no longer available"
        409 -> "Leadership cannot be transferred to that user"
        422 -> "The selected user is invalid"
        else -> "Could not transfer leadership ($code)"
    }

    private fun projectRenameError(code: Int): String = when (code) {
        403 -> "You do not have permission to rename this project"
        409 -> "That project ID is already in use"
        422 -> "That project ID is invalid"
        else -> "Could not rename the project ($code)"
    }

    private fun currentEditDraft(): ProjectEditState.Editing? = when (val s = _editState.value) {
        is ProjectEditState.Editing -> s
        is ProjectEditState.SaveError -> s.draft
        else -> null
    }

    private fun updateEditDraft(update: (ProjectEditState.Editing) -> ProjectEditState.Editing) {
        val draft = currentEditDraft() ?: return
        _editState.value = update(draft)
    }
}
