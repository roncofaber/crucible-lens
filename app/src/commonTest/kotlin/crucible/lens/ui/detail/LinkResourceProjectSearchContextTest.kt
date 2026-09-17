package crucible.lens.ui.detail

import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ProjectReference
import crucible.lens.data.model.ProjectScope
import crucible.lens.data.model.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkResourceProjectSearchContextTest {
    @Test
    fun expandedProjectUsesCanonicalAssignedScope() {
        val resource = Sample(
            uniqueId = "sample-mfid",
            projectId = "legacy-slug",
            project = ProjectReference(
                uniqueId = "0tf1adtg65vf9000fw8t5ddy0g",
                projectId = "current-slug",
                title = "Current project"
            )
        )

        assertEquals(
            LinkProjectSearchContext(
                projectId = null,
                projectMfid = "0tf1adtg65vf9000fw8t5ddy0g",
                projectScope = ProjectScope.Assigned
            ),
            resource.linkProjectSearchContext()
        )
    }

    @Test
    fun legacyProjectSlugRetainsAssignedScope() {
        val resource = Dataset(uniqueId = "dataset-mfid", projectId = "legacy-slug")

        assertEquals(
            LinkProjectSearchContext(
                projectId = "legacy-slug",
                projectMfid = null,
                projectScope = ProjectScope.Assigned
            ),
            resource.linkProjectSearchContext()
        )
    }

    @Test
    fun unassignedResourceDoesNotSendProjectScope() {
        val resource = Sample(uniqueId = "sample-mfid")

        assertEquals(
            LinkProjectSearchContext(projectId = null, projectMfid = null, projectScope = null),
            resource.linkProjectSearchContext()
        )
    }
}
