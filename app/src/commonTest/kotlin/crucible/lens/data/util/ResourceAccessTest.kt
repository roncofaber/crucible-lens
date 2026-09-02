package crucible.lens.data.util

import crucible.lens.data.model.AccessGrant
import crucible.lens.data.model.AccessPermission
import crucible.lens.data.model.AccessPrincipalKind
import crucible.lens.data.model.AccessPrincipalType
import crucible.lens.data.model.ResourceGrantRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourceAccessTest {
    @Test
    fun writablePrincipalsMapToCanonicalMutationTargets() {
        assertEquals(
            AccessGrantMutationTarget(AccessPrincipalKind.Users, "user-a"),
            accessGrantMutationTarget(grant(AccessPrincipalType.User, principalId = "user-a"))
        )
        assertEquals(
            AccessGrantMutationTarget(AccessPrincipalKind.Users, "service-a"),
            accessGrantMutationTarget(grant(AccessPrincipalType.ServiceAccount, principalId = "service-a"))
        )
        assertEquals(
            AccessGrantMutationTarget(AccessPrincipalKind.Projects, "project-slug"),
            accessGrantMutationTarget(grant(AccessPrincipalType.Project, slug = "project-slug"))
        )
    }

    @Test
    fun unsupportedAndOwnerGrantsAreReadOnly() {
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.Instrument)))
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.Public)))
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.System)))
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.Unknown)))
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.User, permission = AccessPermission.Owner)))
        assertNull(accessGrantMutationTarget(grant(AccessPrincipalType.Project, slug = null)))
    }

    @Test
    fun maximumGrantRoleLimitsSelectableRoles() {
        assertEquals(emptyList(), allowedGrantRoles(null))
        assertEquals(listOf(ResourceGrantRole.Viewer), allowedGrantRoles(ResourceGrantRole.Viewer))
        assertEquals(
            listOf(ResourceGrantRole.Viewer, ResourceGrantRole.Contributor, ResourceGrantRole.Editor),
            allowedGrantRoles(ResourceGrantRole.Editor)
        )
        assertEquals(ResourceGrantRole.Admin, AccessPermission.Admin.asGrantRole())
        assertNull(AccessPermission.Owner.asGrantRole())
    }

    @Test
    fun grantsSortByDescendingAuthorityThenName() {
        val grants = listOf(
            grant(AccessPrincipalType.User, principalId = "viewer", permission = AccessPermission.Viewer),
            grant(AccessPrincipalType.User, principalId = "editor-z", permission = AccessPermission.Editor),
            grant(AccessPrincipalType.User, principalId = "owner", permission = AccessPermission.Owner),
            grant(AccessPrincipalType.User, principalId = "admin", permission = AccessPermission.Admin),
            grant(AccessPrincipalType.User, principalId = "editor-a", permission = AccessPermission.Editor),
            grant(AccessPrincipalType.User, principalId = "contributor", permission = AccessPermission.Contributor)
        )

        assertEquals(
            listOf("owner", "admin", "editor-a", "editor-z", "contributor", "viewer"),
            grants.sortedWith(accessGrantComparator).map { it.principalId }
        )
    }

    private fun grant(
        type: AccessPrincipalType,
        principalId: String = "principal-a",
        slug: String? = null,
        permission: AccessPermission = AccessPermission.Viewer
    ) = AccessGrant(
        principalId = principalId,
        principalType = type,
        permission = permission,
        slug = slug
    )
}
