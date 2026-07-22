package crucible.lens.data.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

data class CachedEntry<V>(val value: V, val timestamp: Long)

/**
 * Generic in-memory TTL cache backed by a [MutableStateFlow], so callers can either
 * read synchronously ([get]) or observe changes reactively ([observe]). Expiry is
 * checked lazily on read/observe — there is no background eviction timer.
 */
class ObservableCache<K, V>(
    private val ttlMillis: Long,
    private val maxSize: Int,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val state = MutableStateFlow<Map<K, CachedEntry<V>>>(emptyMap())

    private fun CachedEntry<V>.isExpired(): Boolean = now() - timestamp > ttlMillis

    fun get(key: K): V? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        return entry.value
    }

    fun observe(key: K): Flow<V?> = state.map { map ->
        val entry = map[key] ?: return@map null
        if (entry.isExpired()) null else entry.value
    }

    fun put(key: K, value: V) {
        state.value = state.value.let { current ->
            val withoutEvicted = if (current.size >= maxSize && key !in current) {
                val oldestKey = current.entries.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) current - oldestKey else current
            } else current
            withoutEvicted + (key to CachedEntry(value, now()))
        }
    }

    fun invalidate(key: K) {
        state.value = state.value - key
    }

    fun invalidateAll() {
        state.value = emptyMap()
    }

    fun ageMillis(key: K): Long? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        return now() - entry.timestamp
    }
}
