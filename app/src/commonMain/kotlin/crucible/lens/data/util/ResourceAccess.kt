package crucible.lens.data.util

import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessPermission
import crucible.lens.data.model.AccessPrincipalKind
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.ResourceGrantRole

data class AccessGrantMutationTarget(
    val kind: AccessPrincipalKind,
    val principal: String
)

val accessGrantComparator = compareBy<AccessGrant>(
    { accessPermissionSortOrder(it.permission) },
    { it.displayName?.lowercase() ?: it.slug?.lowercase() ?: it.principalId.lowercase() },
    { accessPrincipalSortOrder(it.principalType) }
)

fun accessGrantMutationTarget(grant: AccessGrant): AccessGrantMutationTarget? {
    if (grant.permission == AccessPermission.Owner) return null
    return when (grant.principalType) {
        AccessPrincipalType.User,
        AccessPrincipalType.ServiceAccount -> AccessGrantMutationTarget(AccessPrincipalKind.Users, grant.principalId)
        AccessPrincipalType.Project -> grant.slug?.let { AccessGrantMutationTarget(AccessPrincipalKind.Projects, it) }
        else -> null
    }
}

fun allowedGrantRoles(maxRole: ResourceGrantRole?): List<ResourceGrantRole> =
    maxRole?.let { maximum -> ResourceGrantRole.entries.take(maximum.ordinal + 1) }.orEmpty()

fun AccessPermission.asGrantRole(): ResourceGrantRole? = when (this) {
    AccessPermission.Viewer -> ResourceGrantRole.Viewer
    AccessPermission.Contributor -> ResourceGrantRole.Contributor
    AccessPermission.Editor -> ResourceGrantRole.Editor
    AccessPermission.Admin -> ResourceGrantRole.Admin
    AccessPermission.Owner -> null
}

fun ResourceGrantRole.displayLabel(): String = name.replaceFirstChar { it.uppercase() }

fun AccessPermission.displayLabel(): String = name.replaceFirstChar { it.uppercase() }

fun AccessPrincipalType.displayLabel(): String = when (this) {
    AccessPrincipalType.User -> "User"
    AccessPrincipalType.ServiceAccount -> "Service account"
    AccessPrincipalType.Project -> "Project"
    AccessPrincipalType.Instrument -> "Instrument"
    AccessPrincipalType.Public -> "Public"
    AccessPrincipalType.System -> "System"
    AccessPrincipalType.Unknown -> "Unknown"
}

private fun accessPrincipalSortOrder(type: AccessPrincipalType): Int = when (type) {
    AccessPrincipalType.Public -> 0
    AccessPrincipalType.User -> 1
    AccessPrincipalType.ServiceAccount -> 2
    AccessPrincipalType.Project -> 3
    AccessPrincipalType.Instrument -> 4
    AccessPrincipalType.System -> 5
    AccessPrincipalType.Unknown -> 6
}

private fun accessPermissionSortOrder(permission: AccessPermission): Int = when (permission) {
    AccessPermission.Owner -> 0
    AccessPermission.Admin -> 1
    AccessPermission.Editor -> 2
    AccessPermission.Contributor -> 3
    AccessPermission.Viewer -> 4
}
