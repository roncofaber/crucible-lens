package crucible.lens.ui.projects

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.ui.common.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectContentMergeTest {
    @Test
    fun assignedResourcesTakePrecedenceAndOnlySharedAdditionsAreMarked() {
        val assigned = ProjectContent(
            samples = listOf(Sample("sample-a", sampleName = "Assigned")),
            datasets = listOf(Dataset("dataset-a", datasetName = "Assigned"))
        )
        val shared = ProjectContent(
            samples = listOf(
                Sample("sample-a", sampleName = "Duplicate"),
                Sample("sample-b", sampleName = "Shared")
            ),
            datasets = listOf(
                Dataset("dataset-a", datasetName = "Duplicate"),
                Dataset("dataset-b", datasetName = "Shared")
            )
        )

        val merged = mergeProjectContent(assigned, shared)

        assertEquals(listOf("sample-a", "sample-b"), merged.samples.map { it.uniqueId })
        assertEquals("Assigned", merged.samples.first().name)
        assertEquals(setOf("sample-b"), merged.sharedSampleIds)
        assertEquals(listOf("dataset-a", "dataset-b"), merged.datasets.map { it.uniqueId })
        assertEquals("Assigned", merged.datasets.first().name)
        assertEquals(setOf("dataset-b"), merged.sharedDatasetIds)
    }

    @Test
    fun partialFailureRetainsAvailableProjectContent() {
        val assigned = LoadState.Success(
            ProjectContent(samples = listOf(Sample("sample-a")), datasets = emptyList()),
            fromCache = true
        )

        val merged = mergeProjectLoadStates(assigned, LoadState.Error("Shared resources unavailable"))

        val success = assertIs<LoadState.Success<ProjectContent>>(merged)
        assertEquals(listOf("sample-a"), success.data.samples.map { it.uniqueId })
        assertTrue(success.fromCache)
        assertFalse(success.isRefreshing)
        assertEquals("Shared resources unavailable", success.refreshError)
    }
}
