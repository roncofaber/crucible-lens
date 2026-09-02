package crucible.lens.data.preferences

import crucible.lens.data.model.Project
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectReferenceMigrationTest {
    private val projects = listOf(
        Project(uniqueId = "mfid-a", projectId = "project-a"),
        Project(uniqueId = "mfid-b", projectId = "project-b")
    )

    @Test
    fun migratesKnownSlugsAndPreservesMfids() {
        assertEquals(
            setOf("mfid-a", "mfid-b"),
            migratePinnedProjectReferences(setOf("project-a", "mfid-b"), projects)
        )
    }

    @Test
    fun preservesUnresolvedReferencesForLaterRefresh() {
        assertEquals(
            setOf("missing-project"),
            migratePinnedProjectReferences(setOf("missing-project"), projects)
        )
    }

    @Test
    fun migratesSyncSlugsAndDropsUnresolvedSelections() {
        assertEquals(
            setOf("mfid-a", "mfid-b"),
            migrateSyncedProjectReferences(setOf("project-a", "mfid-b", "missing-project"), projects)
        )
    }

    @Test
    fun detectsLegacySyncReferences() {
        assertEquals(true, syncedProjectReferencesNeedMigration(setOf("project-a"), projects))
        assertEquals(false, syncedProjectReferencesNeedMigration(setOf("mfid-a"), projects))
        assertEquals(true, isMfidReference("01k4abcdefghjkmnpqrstvwxyz"))
    }
}
