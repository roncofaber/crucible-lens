package crucible.lens.ui.instruments

import crucible.lens.ui.common.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InstrumentDetailStateTest {

    @Test
    fun instrumentErrorsDistinguishNotFoundAndAccessFailures() {
        assertEquals("Instrument not found", instrumentLoadError(404))
        assertEquals("Sign in again to view this instrument", instrumentLoadError(401))
        assertEquals("You do not have permission to view this instrument", instrumentLoadError(403))
        assertEquals("Crucible service error (503)", instrumentLoadError(503))
    }

    @Test
    fun datasetErrorsDistinguishAccessAndServiceFailures() {
        assertEquals("Sign in again to refresh datasets", datasetLoadError(401))
        assertEquals("You do not have permission to view these datasets", datasetLoadError(403))
        assertEquals("Crucible service error (502)", datasetLoadError(502))
        assertEquals("Could not load datasets (429)", datasetLoadError(429))
    }

    @Test
    fun initialFailureUsesFullErrorState() {
        val state = instrumentDetailFailureState<List<String>>(null, "Unavailable")

        assertIs<LoadState.Error>(state)
        assertEquals("Unavailable", state.message)
    }

    @Test
    fun refreshFailureRetainsSuccessfulContent() {
        val previous = LoadState.Success(
            data = listOf("dataset"),
            isRefreshing = true,
            fromCache = true
        )

        val state = assertIs<LoadState.Success<List<String>>>(
            instrumentDetailFailureState(previous, "Unavailable")
        )

        assertEquals(listOf("dataset"), state.data)
        assertFalse(state.isRefreshing)
        assertTrue(state.fromCache)
        assertEquals("Unavailable", state.refreshError)
    }

    @Test
    fun paginationAllowsOnlyOneExplicitRequestAtATime() {
        assertTrue(InstrumentDatasetPaginationState(nextCursor = "next").canLoadMore)
        assertFalse(InstrumentDatasetPaginationState(nextCursor = "next", isLoadingMore = true).canLoadMore)
        assertFalse(InstrumentDatasetPaginationState().canLoadMore)
    }
}
