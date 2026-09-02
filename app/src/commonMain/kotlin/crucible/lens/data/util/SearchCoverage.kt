package crucible.lens.data.util

enum class SearchEndpointCategory(val label: String) {
    PEOPLE("People"),
    PROJECTS("Projects"),
    SAMPLES("Samples"),
    DATASETS("Datasets")
}

sealed interface SearchCoverage {
    object Complete : SearchCoverage
    data class Partial(val message: String) : SearchCoverage
    object Failed : SearchCoverage
}

fun searchCoverage(
    requested: Set<SearchEndpointCategory>,
    failed: Set<SearchEndpointCategory>
): SearchCoverage = when {
    failed.isEmpty() -> SearchCoverage.Complete
    failed.containsAll(requested) -> SearchCoverage.Failed
    else -> SearchCoverage.Partial(
        "Could not load ${failed.sortedBy { it.ordinal }.joinToString { it.label }}"
    )
}
