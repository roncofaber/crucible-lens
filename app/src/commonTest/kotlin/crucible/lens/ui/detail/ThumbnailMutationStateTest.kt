package crucible.lens.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals

class ThumbnailMutationStateTest {
    @Test
    fun mutationViewsAreScopedByDatasetAndOperation() {
        val states = mapOf(
            ThumbnailMutationKey("dataset-a", 1, ThumbnailMutationType.DELETE) to ThumbnailMutationState.Running,
            ThumbnailMutationKey("dataset-a", 2, ThumbnailMutationType.UPDATE) to ThumbnailMutationState.Error("Update failed"),
            ThumbnailMutationKey("dataset-b", 3, ThumbnailMutationType.DELETE) to ThumbnailMutationState.Running
        )

        assertEquals(
            setOf(1),
            thumbnailMutationIds(states, "dataset-a", ThumbnailMutationType.DELETE)
        )
        assertEquals(
            mapOf(2 to "Update failed"),
            thumbnailMutationErrors(states, "dataset-a", ThumbnailMutationType.UPDATE)
        )
    }
}
