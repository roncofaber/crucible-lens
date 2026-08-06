package crucible.lens.data.util

import crucible.lens.data.model.Project
import kotlin.test.Test
import kotlin.test.assertEquals

private fun project(id: String, title: String? = null, lead: String? = null) =
    Project(projectId = id, title = title, projectLeadOrcid = lead)

class SyncSuggestionsTest {

    @Test
    fun suggestsProjectsTheUserLeads() {
        val projects = listOf(project("a", lead = "orcid-1"), project("b", lead = "orcid-2"))
        assertEquals(setOf("a"), suggestedProjectIds(projects, "orcid-1", emptySet()))
    }

    @Test
    fun suggestsPinnedProjects() {
        val projects = listOf(project("a"), project("b"))
        assertEquals(setOf("b"), suggestedProjectIds(projects, null, setOf("b")))
    }

    @Test
    fun suggestsUnionOfLedAndPinnedWithoutDuplicates() {
        val projects = listOf(project("a", lead = "orcid-1"), project("b"), project("c"))
        assertEquals(setOf("a", "b"), suggestedProjectIds(projects, "orcid-1", setOf("a", "b")))
    }

    @Test
    fun suggestsNothingWithNoSignals() {
        val projects = listOf(project("a"), project("b"))
        assertEquals(emptySet(), suggestedProjectIds(projects, null, emptySet()))
    }

    @Test
    fun ignoresPinnedIdsThatAreNotInTheProjectList() {
        val projects = listOf(project("a"))
        assertEquals(emptySet(), suggestedProjectIds(projects, null, setOf("ghost")))
    }

    @Test
    fun sortsSuggestedFirstThenAlphabeticallyByTitle() {
        val projects = listOf(
            project("c", title = "Charlie"),
            project("a", title = "Alpha"),
            project("b", title = "Bravo")
        )
        val sorted = sortForPicker(projects, setOf("c"))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.projectId })
    }

    @Test
    fun sortsByProjectIdWhenTitleIsNull() {
        val projects = listOf(project("zeta"), project("alpha"))
        assertEquals(listOf("alpha", "zeta"), sortForPicker(projects, emptySet()).map { it.projectId })
    }

    @Test
    fun sortsCaseInsensitively() {
        val projects = listOf(project("a", title = "beta"), project("b", title = "Alpha"))
        assertEquals(listOf("b", "a"), sortForPicker(projects, emptySet()).map { it.projectId })
    }
}
