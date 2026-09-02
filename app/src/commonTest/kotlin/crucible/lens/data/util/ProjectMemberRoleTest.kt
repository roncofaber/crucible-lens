package crucible.lens.data.util

import crucible.lens.data.model.ResourceCapabilities
import crucible.lens.data.model.ResourceGrantRole
import crucible.lens.data.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectMemberRoleTest {
    @Test
    fun membersSortByDescendingAuthorityThenName() {
        val members = listOf(
            User(lastName = "Zulu", role = "viewer"),
            User(lastName = "Bravo", role = "editor"),
            User(lastName = "Alpha", role = "editor"),
            User(lastName = "Lead", role = "owner"),
            User(lastName = "Admin", role = "admin"),
            User(lastName = "Contributor", role = "contributor"),
            User(lastName = "Unknown", role = null)
        )

        assertEquals(
            listOf("Lead", "Admin", "Alpha", "Bravo", "Contributor", "Zulu", "Unknown"),
            members.sortedWith(projectMemberComparator).map { it.lastName }
        )
    }

    @Test
    fun projectRolesMatchApiGrantRules() {
        assertFalse(ProjectMemberRole.Contributor.canAddProjectMembers())
        assertTrue(ProjectMemberRole.Editor.canAddProjectMembers())
        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor),
            ProjectMemberRole.Editor.assignableProjectRoles()
        )
        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor, ProjectMemberRole.Editor),
            ProjectMemberRole.Admin.assignableProjectRoles()
        )
        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor, ProjectMemberRole.Editor, ProjectMemberRole.Admin),
            ProjectMemberRole.Owner.assignableProjectRoles()
        )
    }

    @Test
    fun onlyAdminsAndOwnersCanRenameProjects() {
        assertFalse(ProjectMemberRole.Editor.canRenameProject())
        assertTrue(ProjectMemberRole.Admin.canRenameProject())
        assertTrue(ProjectMemberRole.Owner.canRenameProject())
    }

    @Test
    fun memberRoleChangesCannotTouchHigherOrOwnerRoles() {
        assertTrue(ProjectMemberRole.Editor.canChangeProjectRole(ProjectMemberRole.Contributor))
        assertFalse(ProjectMemberRole.Editor.canChangeProjectRole(ProjectMemberRole.Editor))
        assertFalse(ProjectMemberRole.Editor.canChangeProjectRole(ProjectMemberRole.Admin))
        assertFalse(ProjectMemberRole.Admin.canChangeProjectRole(ProjectMemberRole.Admin))
        assertFalse(ProjectMemberRole.Owner.canChangeProjectRole(ProjectMemberRole.Owner))
    }

    @Test
    fun projectCapabilitiesExpressStrictRoleCeilings() {
        val editorCapabilities = capabilities(ResourceGrantRole.Contributor)
        val adminCapabilities = capabilities(ResourceGrantRole.Editor)
        val ownerCapabilities = capabilities(ResourceGrantRole.Admin)

        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor),
            editorCapabilities.assignableProjectRoles(fallbackRole = ProjectMemberRole.Owner)
        )
        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor, ProjectMemberRole.Editor),
            adminCapabilities.assignableProjectRoles(fallbackRole = ProjectMemberRole.Owner)
        )
        assertEquals(
            listOf(ProjectMemberRole.Viewer, ProjectMemberRole.Contributor, ProjectMemberRole.Editor, ProjectMemberRole.Admin),
            ownerCapabilities.assignableProjectRoles(fallbackRole = null)
        )
        assertTrue(editorCapabilities.canChangeProjectRole(ProjectMemberRole.Contributor, fallbackRole = null))
        assertFalse(editorCapabilities.canChangeProjectRole(ProjectMemberRole.Editor, fallbackRole = ProjectMemberRole.Owner))
        assertTrue(adminCapabilities.canChangeProjectRole(ProjectMemberRole.Editor, fallbackRole = null))
        assertFalse(adminCapabilities.canChangeProjectRole(ProjectMemberRole.Admin, fallbackRole = ProjectMemberRole.Owner))
    }

    @Test
    fun missingResourceCapabilitiesUseExistingFallbackRules() {
        val capabilities: ResourceCapabilities? = null

        assertTrue(capabilities.allowsEdit(fallback = true))
        assertTrue(capabilities.allowsTransfer(fallback = true))
        assertEquals(ProjectMemberRole.Owner.assignableProjectRoles(), capabilities.assignableProjectRoles(ProjectMemberRole.Owner))
    }

    private fun capabilities(maximum: ResourceGrantRole) = ResourceCapabilities(
        canEdit = true,
        canManageAccess = true,
        maxGrantRole = maximum
    )
}
