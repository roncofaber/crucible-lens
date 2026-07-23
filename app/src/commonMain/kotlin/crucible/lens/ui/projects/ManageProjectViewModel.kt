package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.cache.CacheManager
import crucible.lens.data.model.Project
import crucible.lens.data.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ManageProjectState {
    object Loading : ManageProjectState()
    data class Loaded(val project: Project, val members: List<User>, val isLead: Boolean) : ManageProjectState()
    data class Error(val message: String) : ManageProjectState()
}

sealed class ProjectEditState {
    object Idle : ProjectEditState()
    data class Editing(
        val title: String,
        val organization: String,
        val leadUsername: String,
        val leadSearch: List<User> = emptyList(),
        val isLeadSearching: Boolean = false
    ) : ProjectEditState()
    object Saving : ProjectEditState()
    data class SaveError(val draft: Editing, val message: String) : ProjectEditState()
}

class ManageProjectViewModel(
    private val apiClient: ApiClient,
    private val cacheManager: CacheManager
) : ViewModel() {

    private val _state = MutableStateFlow<ManageProjectState>(ManageProjectState.Loading)
    val state: StateFlow<ManageProjectState> = _state.asStateFlow()

    private val _editState = MutableStateFlow<ProjectEditState>(ProjectEditState.Idle)
    val editState: StateFlow<ProjectEditState> = _editState.asStateFlow()

    private val _pendingRemove = MutableStateFlow<User?>(null)
    val pendingRemove: StateFlow<User?> = _pendingRemove.asStateFlow()

    private val _isAddMemberSheetVisible = MutableStateFlow(false)
    val isAddMemberSheetVisible: StateFlow<Boolean> = _isAddMemberSheetVisible.asStateFlow()

    private val _memberSearchResults = MutableStateFlow<List<User>>(emptyList())
    val memberSearchResults: StateFlow<List<User>> = _memberSearchResults.asStateFlow()

    private val _isMemberSearching = MutableStateFlow(false)
    val isMemberSearching: StateFlow<Boolean> = _isMemberSearching.asStateFlow()

    private val _isAddingMember = MutableStateFlow(false)
    val isAddingMember: StateFlow<Boolean> = _isAddingMember.asStateFlow()

    private var projectId: String = ""
    private var currentUserOrcid: String? = null
    private var memberSearchJob: Job? = null
    private var leadSearchJob: Job? = null

    fun init(projectId: String, currentUserOrcid: String?) {
        this.projectId = projectId
        this.currentUserOrcid = currentUserOrcid
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ManageProjectState.Loading
            val projectResult = apiClient.service.getProject(projectId)
            val project = (projectResult as? ApiResult.Success)?.data
            if (project == null) {
                _state.value = ManageProjectState.Error("Project not found")
                return@launch
            }
            val members = (apiClient.service.getProjectUsers(projectId) as? ApiResult.Success)?.data ?: emptyList()
            val isLead = isCurrentUserLead(project)
            _state.value = ManageProjectState.Loaded(project, members, isLead)
        }
    }

    // ORCID (Project.projectLeadOrcid) is the source of truth for identity, not username —
    // usernames can be absent or stale, while ORCID is the account's unique, unchanging id.
    private fun isCurrentUserLead(project: Project): Boolean {
        val orcid = currentUserOrcid ?: return false
        return project.projectLeadOrcid == orcid || project.lead?.uniqueId == orcid
    }

    // ── Edit project info ─────────────────────────────────────────────────────

    fun startEdit() {
        val loaded = _state.value as? ManageProjectState.Loaded ?: return
        _editState.value = ProjectEditState.Editing(
            title = loaded.project.title ?: "",
            organization = loaded.project.organization ?: "",
            leadUsername = loaded.project.lead?.username ?: ""
        )
    }

    fun cancelEdit() {
        leadSearchJob?.cancel()
        _editState.value = ProjectEditState.Idle
    }

    fun onTitleChanged(value: String) = updateEditDraft { it.copy(title = value) }
    fun onOrganizationChanged(value: String) = updateEditDraft { it.copy(organization = value) }

    fun onLeadUsernameChanged(value: String) {
        leadSearchJob?.cancel()
        updateEditDraft { it.copy(leadUsername = value, leadSearch = emptyList(), isLeadSearching = false) }
        if (value.length < 3) return
        updateEditDraft { it.copy(isLeadSearching = true) }
        leadSearchJob = viewModelScope.launch {
            delay(400)
            val results = (apiClient.service.searchUsers(value) as? ApiResult.Success)?.data ?: emptyList()
            updateEditDraft { it.copy(leadSearch = results, isLeadSearching = false) }
        }
    }

    fun selectLeadUser(user: User) {
        leadSearchJob?.cancel()
        updateEditDraft { it.copy(leadUsername = user.username ?: "", leadSearch = emptyList(), isLeadSearching = false) }
    }

    fun saveProject() {
        val draft = currentEditDraft() ?: return
        if (_editState.value is ProjectEditState.Saving) return
        _editState.value = ProjectEditState.Saving
        viewModelScope.launch {
            val loaded = _state.value as? ManageProjectState.Loaded
            val result = apiClient.service.updateProject(
                projectId = projectId,
                title = draft.title.trim().ifBlank { null },
                organization = draft.organization.trim().ifBlank { null },
                projectLeadUsername = draft.leadUsername.trim().ifBlank { null }
            )
            when (result) {
                is ApiResult.Success -> {
                    cacheManager.clearProjectsCache()
                    val members = loaded?.members ?: emptyList()
                    val isLead = isCurrentUserLead(result.data)
                    _state.value = ManageProjectState.Loaded(result.data, members, isLead)
                    _editState.value = ProjectEditState.Idle
                }
                is ApiResult.Error -> _editState.value = ProjectEditState.SaveError(draft, "Save failed (${result.code})")
            }
        }
    }

    // ── Member management ─────────────────────────────────────────────────────

    fun showAddMemberSheet() { _isAddMemberSheetVisible.value = true; _memberSearchResults.value = emptyList() }
    fun hideAddMemberSheet() { _isAddMemberSheetVisible.value = false; memberSearchJob?.cancel() }

    fun searchMembers(query: String) {
        memberSearchJob?.cancel()
        if (query.length < 3) { _memberSearchResults.value = emptyList(); return }
        memberSearchJob = viewModelScope.launch {
            delay(350)
            _isMemberSearching.value = true
            _memberSearchResults.value = (apiClient.service.searchUsers(query) as? ApiResult.Success)?.data ?: emptyList()
            _isMemberSearching.value = false
        }
    }

    fun addMember(user: User) {
        val username = user.username ?: return
        viewModelScope.launch {
            _isAddingMember.value = true
            val result = apiClient.service.addProjectMember(projectId, username)
            if (result is ApiResult.Success && result.data) {
                val loaded = _state.value as? ManageProjectState.Loaded
                if (loaded != null && loaded.members.none { it.uniqueId == user.uniqueId }) {
                    _state.value = loaded.copy(members = loaded.members + user)
                }
                hideAddMemberSheet()
            }
            _isAddingMember.value = false
        }
    }

    fun confirmRemove(user: User) { _pendingRemove.value = user }
    fun cancelRemove() { _pendingRemove.value = null }

    fun removeMember() {
        val user = _pendingRemove.value ?: return
        val orcid = user.uniqueId ?: return
        _pendingRemove.value = null
        viewModelScope.launch {
            val result = apiClient.service.removeProjectMember(projectId, orcid)
            if (result is ApiResult.Success && result.data) {
                val loaded = _state.value as? ManageProjectState.Loaded
                if (loaded != null) {
                    _state.value = loaded.copy(members = loaded.members.filter { it.uniqueId != orcid })
                }
            }
        }
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
