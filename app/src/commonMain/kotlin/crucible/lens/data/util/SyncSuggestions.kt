package crucible.lens.data.util

import crucible.lens.data.model.Project

/**
 * Projects worth offering as a starting sync set: the ones the user leads, plus the ones they
 * pinned. Recency is deliberately not a signal - HistoryItem carries no projectId, so deriving it
 * would mean resolving 20 resource uuids through a cache that may be cold.
 */
fun suggestedProjectIds(
    projects: List<Project>,
    currentUserOrcid: String?,
    pinnedProjects: Set<String>
): Set<String> = projects
    .filter { it.projectId in pinnedProjects || (currentUserOrcid != null && it.projectLeadOrcid == currentUserOrcid) }
    .map { it.projectId }
    .toSet()

fun sortForPicker(projects: List<Project>, suggested: Set<String>): List<Project> =
    projects.sortedWith(
        compareByDescending<Project> { it.projectId in suggested }
            .thenBy { (it.title ?: it.projectId).lowercase() }
    )
