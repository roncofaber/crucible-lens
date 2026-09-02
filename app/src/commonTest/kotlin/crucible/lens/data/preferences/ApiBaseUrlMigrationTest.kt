package crucible.lens.data.preferences

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiBaseUrlMigrationTest {
    @Test
    fun retiredOfficialUrlMigratesToV3() {
        assertEquals(
            AppPreferences.DEFAULT_API_BASE_URL,
            migrateOfficialApiBaseUrl("https://crucible.lbl.gov/api/v2")
        )
    }

    @Test
    fun customServerUrlIsPreserved() {
        val customUrl = "https://example.test/api/v2/"

        assertEquals(customUrl, migrateOfficialApiBaseUrl(customUrl))
    }
}
