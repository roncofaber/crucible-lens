package crucible.lens.data.cache

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObservableCacheTest {

    private fun cacheWithClock(ttlMillis: Long, maxSize: Int, clock: () -> Long) =
        ObservableCache<String, String>(ttlMillis = ttlMillis, maxSize = maxSize, now = clock)

    @Test
    fun getReturnsNullForMissingKey() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.get("missing"))
    }

    @Test
    fun putThenGetReturnsValue() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        assertEquals("value-a", cache.get("a"))
    }

    @Test
    fun getReturnsNullAfterTtlExpires() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.get("a"))
    }

    @Test
    fun getReturnsValueJustBeforeTtlExpires() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 999L
        assertEquals("value-a", cache.get("a"))
    }

    @Test
    fun putEvictsOldestWhenAtCapacity() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 100_000, maxSize = 2) { time }
        cache.put("a", "value-a"); time = 10L
        cache.put("b", "value-b"); time = 20L
        // Cache is now at capacity (2). Inserting a third must evict the oldest ("a").
        cache.put("c", "value-c")
        assertNull(cache.get("a"))
        assertEquals("value-b", cache.get("b"))
        assertEquals("value-c", cache.get("c"))
    }

    @Test
    fun invalidateRemovesEntry() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        cache.invalidate("a")
        assertNull(cache.get("a"))
    }

    @Test
    fun invalidateAllClearsEverything() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        cache.put("b", "value-b")
        cache.invalidateAll()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun ageMillisReturnsNullForMissingKey() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.ageMillis("missing"))
    }

    @Test
    fun ageMillisReturnsElapsedTimeSincePut() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 100_000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 500L
        assertEquals(500L, cache.ageMillis("a"))
    }

    @Test
    fun ageMillisReturnsNullAfterExpiry() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.ageMillis("a"))
    }

    @Test
    fun observeEmitsCurrentValueImmediately() = runTest {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        assertEquals("value-a", cache.observe("a").first())
    }

    @Test
    fun observeEmitsNullForMissingKey() = runTest {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.observe("missing").first())
    }

    @Test
    fun observeEmitsNullForExpiredEntry() = runTest {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.observe("a").first())
    }
}
