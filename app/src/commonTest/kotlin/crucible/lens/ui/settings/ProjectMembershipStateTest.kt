package crucible.lens.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectMembershipStateTest {

    @Test
    fun completeLookupRecordsMembersAndVerifiedNonMembers() {
        val snapshot = mergeProjectMembership(
            previous = null,
            checks = listOf(
                ProjectMembershipCheck("member", true),
                ProjectMembershipCheck("available", false)
            )
        )

        assertEquals(setOf("member"), snapshot.memberProjectIds)
        assertEquals(setOf("member", "available"), snapshot.resolvedProjectIds)
        assertTrue(snapshot.failedProjectIds.isEmpty())
    }

    @Test
    fun partialLookupKeepsSuccessfulProjectsAndMarksFailuresUnknown() {
        val snapshot = mergeProjectMembership(
            previous = null,
            checks = listOf(
                ProjectMembershipCheck("member", true),
                ProjectMembershipCheck("available", false),
                ProjectMembershipCheck("unknown", null)
            )
        )

        assertEquals(setOf("member"), snapshot.memberProjectIds)
        assertEquals(setOf("member", "available"), snapshot.resolvedProjectIds)
        assertEquals(setOf("unknown"), snapshot.failedProjectIds)
    }

    @Test
    fun failedLookupLeavesEveryProjectUnresolved() {
        val snapshot = mergeProjectMembership(
            previous = null,
            checks = listOf(
                ProjectMembershipCheck("first", null),
                ProjectMembershipCheck("second", null)
            )
        )

        assertTrue(snapshot.memberProjectIds.isEmpty())
        assertTrue(snapshot.resolvedProjectIds.isEmpty())
        assertEquals(setOf("first", "second"), snapshot.failedProjectIds)
    }

    @Test
    fun failedRetryPreservesPreviouslyResolvedProjects() {
        val previous = ProjectMembershipSnapshot(
            memberProjectIds = setOf("member"),
            resolvedProjectIds = setOf("member", "available"),
            failedProjectIds = setOf("unknown")
        )

        val snapshot = mergeProjectMembership(
            previous = previous,
            checks = listOf(ProjectMembershipCheck("unknown", null))
        )

        assertEquals(setOf("member"), snapshot.memberProjectIds)
        assertEquals(setOf("member", "available"), snapshot.resolvedProjectIds)
        assertEquals(setOf("unknown"), snapshot.failedProjectIds)
    }

    @Test
    fun successfulRetryResolvesOnlyTheFailedProject() {
        val previous = ProjectMembershipSnapshot(
            memberProjectIds = setOf("member"),
            resolvedProjectIds = setOf("member", "available"),
            failedProjectIds = setOf("unknown")
        )

        val snapshot = mergeProjectMembership(
            previous = previous,
            checks = listOf(ProjectMembershipCheck("unknown", false))
        )

        assertEquals(setOf("member"), snapshot.memberProjectIds)
        assertEquals(setOf("member", "available", "unknown"), snapshot.resolvedProjectIds)
        assertTrue(snapshot.failedProjectIds.isEmpty())
    }

    @Test
    fun addMemberErrorsDistinguishConflictAndPermissionFailures() {
        assertEquals("You do not have permission to add members to this project", addToProjectError(403))
        assertEquals("This user may already be a project member", addToProjectError(409))
        assertEquals("Crucible service error (503)", addToProjectError(503))
    }
}
