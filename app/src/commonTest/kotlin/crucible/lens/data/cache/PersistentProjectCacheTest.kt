package crucible.lens.data.cache

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentProjectCacheTest {
    private val owner = ProjectCacheOwner("account-a", "https://crucible.lbl.gov/api/v3/")

    @Test
    fun cacheBelongsOnlyToItsOwner() {
        val cache = CachedProjects(accountId = "account-a", serverUrl = owner.serverUrl, projects = emptyList(), cachedAt = 0L)

        assertEquals(cache, cache.migrate(owner))
        assertFalse(cache.belongsTo(owner.copy(accountId = "account-b")))
    }

    @Test
    fun legacyCacheIsRejectedWithoutOwnershipMetadata() {
        val cache = CachedProjects(projects = emptyList(), cachedAt = 0L)

        assertNull(cache.migrate(owner))
    }

    @Test
    fun projectContentIsScopedByAccountAndProject() {
        val content = projectContent("project-a")
        val cache = CachedProjectContents(accountId = owner.accountId, serverUrl = owner.serverUrl, projects = mapOf(content.projectMfid to content))

        assertEquals(content, cache.contentFor(owner, "mfid-project-a"))
        assertNull(cache.contentFor(owner.copy(accountId = "account-b"), "mfid-project-a"))
        assertNull(cache.contentFor(owner, "mfid-project-b"))
        assertFalse(cache.copy(version = PROJECT_CONTENT_CACHE_VERSION + 1).belongsTo(owner))
    }

    @Test
    fun retainingProjectsRemovesUnsyncedContent() {
        val first = projectContent("project-a")
        val second = projectContent("project-b")
        val cache = CachedProjectContents(
            accountId = "account-a",
            projects = mapOf(first.projectMfid to first, second.projectMfid to second)
        )

        assertEquals(setOf("mfid-project-b"), cache.retaining(setOf("mfid-project-b")).projects.keys)
    }

    @Test
    fun contentSerializationRecoversFromCorruption() {
        val content = projectContent("project-a")
        val cache = CachedProjectContents(accountId = owner.accountId, serverUrl = owner.serverUrl, projects = mapOf(content.projectMfid to content))

        assertEquals(cache, decodeProjectContents(encodeProjectContents(cache)))
        assertNull(decodeProjectContents("not-json"))
    }

    @Test
    fun legacyContentIsRejectedForRefresh() {
        val legacy = CachedProjectContents(
            version = PROJECT_CONTENT_CACHE_VERSION - 1,
            accountId = owner.accountId,
            projects = mapOf("project-a" to projectContent("project-a"))
        )

        assertNull(legacy.migrate(owner))
    }

    @Test
    fun legacyContentIsNotClaimedByCustomServer() {
        val legacy = CachedProjectContents(
            version = PROJECT_CONTENT_CACHE_VERSION - 1,
            accountId = owner.accountId
        )

        assertNull(legacy.migrate(owner.copy(serverUrl = "https://custom.test/api/")))
    }

    @Test
    fun cacheOwnershipIncludesServer() {
        val cache = CachedProjectContents(accountId = owner.accountId, serverUrl = owner.serverUrl)

        assertTrue(cache.belongsTo(owner))
        assertFalse(cache.belongsTo(owner.copy(serverUrl = "https://other.test/api/")))
        assertFalse(owner.storageKey() == owner.copy(serverUrl = "https://other.test/api/").storageKey())
    }

    @Test
    fun deltaUpsertsAndDeletesResourcesAtomically() {
        val content = CachedProjectContent(
            projectMfid = "mfid-project-a",
            projectSlug = "project-a",
            samples = listOf(Sample(uniqueId = "sample-old"), Sample(uniqueId = "sample-delete")),
            datasets = listOf(Dataset(uniqueId = "dataset-delete")),
            cachedAt = 100L
        )

        val updated = content.applyDelta(
            ProjectContentDelta(
                projectMfid = "mfid-project-a",
                projectSlug = "project-a",
                synchronizedAt = 200L,
                cursor = "cursor-2",
                samples = listOf(Sample(uniqueId = "sample-old", sampleName = "Updated"), Sample(uniqueId = "sample-new")),
                deletedSampleIds = setOf("sample-delete"),
                deletedDatasetIds = setOf("dataset-delete")
            )
        )

        assertEquals(setOf("sample-old", "sample-new"), updated.samples.map(Sample::uniqueId).toSet())
        assertEquals("Updated", updated.samples.first { it.uniqueId == "sample-old" }.sampleName)
        assertTrue(updated.datasets.isEmpty())
        assertEquals("cursor-2", updated.replica.deltaCursor)
        assertEquals(setOf("sample-delete"), updated.replica.deletedSampleIds)
    }

    @Test
    fun staleDeltaCannotReinsertDeletedResource() {
        val deleted = projectContent("project-a").applyDelta(
            ProjectContentDelta(
                projectMfid = "mfid-project-a",
                projectSlug = "project-a",
                synchronizedAt = 2000L,
                deletedSampleIds = setOf("sample-project-a")
            )
        )

        val stale = deleted.applyDelta(
            ProjectContentDelta(
                projectMfid = "mfid-project-a",
                projectSlug = "project-a",
                synchronizedAt = 1500L,
                samples = listOf(Sample(uniqueId = "sample-project-a"))
            )
        )

        assertEquals(deleted, stale)
        assertTrue(stale.samples.isEmpty())
    }

    @Test
    fun incompatibleDeltaCanRequireFullRefresh() {
        val content = projectContent("project-a").requireFullRefresh()

        assertTrue(content.replica.requiresFullRefresh)
    }

    @Test
    fun deltaRejectsResourceFromAnotherProject() {
        val content = projectContent("project-a")

        assertFailsWith<IllegalArgumentException> {
            content.applyDelta(
                ProjectContentDelta(
                    projectMfid = "mfid-project-a",
                    projectSlug = "project-a",
                    synchronizedAt = 2000L,
                    samples = listOf(Sample(uniqueId = "wrong", projectId = "project-b"))
                )
            )
        }
    }

    private fun projectContent(projectId: String) = CachedProjectContent(
        projectMfid = "mfid-$projectId",
        projectSlug = projectId,
        samples = listOf(Sample(uniqueId = "sample-$projectId", projectId = projectId)),
        datasets = listOf(Dataset(uniqueId = "dataset-$projectId", projectId = projectId)),
        cachedAt = 1234L
    )
}
