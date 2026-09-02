package crucible.lens.data.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCoverageTest {
    private val requested = SearchEndpointCategory.entries.toSet()

    @Test
    fun completeWhenEveryEndpointSucceeds() {
        assertEquals(SearchCoverage.Complete, searchCoverage(requested, emptySet()))
    }

    @Test
    fun partialFailureNamesUnavailableCategories() {
        assertEquals(
            SearchCoverage.Partial("Could not load People, Datasets"),
            searchCoverage(
                requested,
                setOf(SearchEndpointCategory.DATASETS, SearchEndpointCategory.PEOPLE)
            )
        )
    }

    @Test
    fun failedWhenEveryRequestedEndpointFails() {
        assertEquals(SearchCoverage.Failed, searchCoverage(requested, requested))
    }
}
