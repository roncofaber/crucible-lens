package crucible.lens.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    private val projectId = "11111111-2222-3333-4444-555555555555"
    private val resourceId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val projectMfid = "01k4abcdefghjkmnpqrstvwxyz"
    private val projectSlug = "project-alpha"

    @Test
    fun parsesProjectLink() {
        assertEquals(
            DeepLinkTarget.Project(projectId),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectId")
        )
    }

    @Test
    fun parsesProjectMfidLink() {
        assertEquals(
            DeepLinkTarget.Project(projectMfid),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectMfid")
        )
    }

    @Test
    fun parsesProjectSlugLink() {
        assertEquals(
            DeepLinkTarget.Project(projectSlug),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectSlug")
        )
    }

    @Test
    fun parsesSampleLink() {
        assertEquals(
            DeepLinkTarget.Resource(resourceId),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectId/samples/$resourceId")
        )
    }

    @Test
    fun parsesDatasetLinkWithQueryAndFragment() {
        assertEquals(
            DeepLinkTarget.Resource(resourceId),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectId/datasets/$resourceId?tab=files#details")
        )
    }

    @Test
    fun parsesResourceLinkUnderProjectSlug() {
        assertEquals(
            DeepLinkTarget.Resource(resourceId),
            parseDeepLink("https://crucible.lbl.gov/explore/$projectSlug/datasets/$resourceId")
        )
    }

    @Test
    fun rejectsUntrustedOrInsecureHosts() {
        assertNull(parseDeepLink("http://crucible.lbl.gov/explore/$projectId"))
        assertNull(parseDeepLink("https://crucible.lbl.gov:8443/explore/$projectId"))
        assertNull(parseDeepLink("https://example.com/explore/$projectId"))
        assertNull(parseDeepLink("https://crucible.lbl.gov@example.com/explore/$projectId"))
    }

    @Test
    fun rejectsUnsupportedOrMalformedPaths() {
        assertNull(parseDeepLink("https://crucible.lbl.gov/explore"))
        assertNull(parseDeepLink("https://crucible.lbl.gov/explore/no"))
        assertNull(parseDeepLink("https://crucible.lbl.gov/explore/$projectId/instruments/$resourceId"))
        assertNull(parseDeepLink("https://crucible.lbl.gov/explore/$projectId/samples/not-a-uuid"))
    }

    @Test
    fun scannerAcceptsRawResourceReferences() {
        assertEquals(DeepLinkTarget.Resource(resourceId), parseScannedTarget("  $resourceId  "))
        assertEquals(DeepLinkTarget.Resource(projectMfid), parseScannedTarget(projectMfid))
    }

    @Test
    fun scannerExtractsTargetsFromKnownCrucibleLinks() {
        assertEquals(
            DeepLinkTarget.Resource(resourceId),
            parseScannedTarget("https://crucible.lbl.gov/explore/$projectSlug/datasets/$resourceId?tab=files#details")
        )
        assertEquals(
            DeepLinkTarget.Project(projectSlug),
            parseScannedTarget("https://crucible.lbl.gov/explore/$projectSlug")
        )
    }

    @Test
    fun scannerRejectsUnknownTextAndUntrustedLinks() {
        assertNull(parseScannedTarget("not a resource"))
        assertNull(parseScannedTarget("https://example.com/explore/$projectSlug/datasets/$resourceId"))
    }
}
