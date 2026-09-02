package crucible.lens.data.util

import crucible.lens.data.model.ResourceCapabilities
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.model.User

enum class ProjectMemberRole(val apiValue: String, val label: String) {
    Viewer("viewer", "Viewer"),
    Contributor("contributor", "Contributor"),
    Editor("editor", "Editor"),
    Admin("admin", "Admin"),
    Owner("owner", "Owner");

    companion object {
        fun fromApi(value: String?): ProjectMemberRole? = entries.firstOrNull {
            it.apiValue.equals(value, ignoreCase = true)
        }
    }
}

val projectMemberComparator = compareBy<User>(
    { ProjectMemberRole.fromApi(it.role)?.sortPriority ?: Int.MAX_VALUE },
    { userSortKey(it) }
)

private val ProjectMemberRole.sortPriority: Int
    get() = when (this) {
        ProjectMemberRole.Owner -> 0
        ProjectMemberRole.Admin -> 1
        ProjectMemberRole.Editor -> 2
        ProjectMemberRole.Contributor -> 3
        ProjectMemberRole.Viewer -> 4
    }

fun ProjectMemberRole?.canAddProjectMembers(): Boolean = this != null && this >= ProjectMemberRole.Editor

fun ProjectMemberRole?.canRenameProject(): Boolean = this != null && this >= ProjectMemberRole.Admin

fun ProjectMemberRole?.assignableProjectRoles(): List<ProjectMemberRole> {
    if (!canAddProjectMembers()) return emptyList()
    return ProjectMemberRole.entries.filter { it < this!! }
}

fun ProjectMemberRole?.canChangeProjectRole(current: ProjectMemberRole?): Boolean {
    return canAddProjectMembers() && current != null && current < this!!
}

fun ResourceCapabilities?.allowsEdit(fallback: Boolean): Boolean = this?.canEdit ?: fallback

fun ResourceCapabilities?.allowsAccessManagement(fallback: Boolean): Boolean = this?.canManageAccess ?: fallback

fun ResourceCapabilities?.allowsTransfer(fallback: Boolean): Boolean = this?.canTransfer ?: fallback

fun ResourceCapabilities?.assignableProjectRoles(fallbackRole: ProjectMemberRole?): List<ProjectMemberRole> {
    if (this == null) return fallbackRole.assignableProjectRoles()
    if (!canManageAccess) return emptyList()
    val maximum = maxGrantRole?.toProjectMemberRole() ?: return emptyList()
    return ProjectMemberRole.entries.filter { it != ProjectMemberRole.Owner && it <= maximum }
}

fun ResourceCapabilities?.canChangeProjectRole(current: ProjectMemberRole?, fallbackRole: ProjectMemberRole?): Boolean {
    if (this == null) return fallbackRole.canChangeProjectRole(current)
    val maximum = maxGrantRole?.toProjectMemberRole()
    return canManageAccess && current != null && current != ProjectMemberRole.Owner && maximum != null && current <= maximum
}

private fun ResourceGrantRole.toProjectMemberRole(): ProjectMemberRole = when (this) {
    ResourceGrantRole.Viewer -> ProjectMemberRole.Viewer
    ResourceGrantRole.Contributor -> ProjectMemberRole.Contributor
    ResourceGrantRole.Editor -> ProjectMemberRole.Editor
    ResourceGrantRole.Admin -> ProjectMemberRole.Admin
}
