package crucible.lens.data.cache

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheEpochTest {
    @Test
    fun advancingEpochRejectsOlderWrites() {
        val epoch = CacheEpoch()
        val captured = epoch.capture()

        epoch.advance()

        assertFalse(epoch.isCurrent(captured))
        assertTrue(epoch.isCurrent(epoch.capture()))
    }
}
