package crucible.lens.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class CrucibleWebUrlTest {
    @Test
    fun buildsResourceUrlFromBaseWithTrailingSlash() {
        assertEquals(
            "https://crucible.lbl.gov/explore/project-id/samples/resource-id",
            buildCrucibleWebUrl(
                "https://crucible.lbl.gov/explore/",
                "project-id",
                "samples",
                "resource-id"
            )
        )
    }

    @Test
    fun buildsProjectUrlFromBaseWithoutTrailingSlash() {
        assertEquals(
            "https://crucible.lbl.gov/explore/project-id",
            buildCrucibleWebUrl("https://crucible.lbl.gov/explore", "project-id")
        )
    }

    @Test
    fun normalizesBoundarySlashes() {
        assertEquals(
            "https://crucible.lbl.gov/explore/project-id/datasets/resource-id",
            buildCrucibleWebUrl(
                "https://crucible.lbl.gov/explore///",
                "/project-id/",
                "/datasets/",
                "/resource-id/"
            )
        )
    }

    @Test
    fun returnsBlankForBlankBaseUrl() {
        assertEquals("", buildCrucibleWebUrl("", "project-id"))
    }
}
