package crucible.lens.data.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

data class CachedEntry<V>(val value: V, val timestamp: Long, val accessOrder: Long = 0L)

/**
 * Generic in-memory cache with freshness-aware reads and stale-visible observation.
 * [get] enforces the TTL, while [peek] and [observe] retain the last value until replacement,
 * explicit invalidation, or capacity eviction.
 */
class ObservableCache<K, V>(
    private val ttlMillis: Long,
    private val maxSize: Int,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val state = MutableStateFlow<Map<K, CachedEntry<V>>>(emptyMap())
    private val accessSequence = MutableStateFlow(0L)

    /** Number of entries currently held, including any that have expired but not yet been read/evicted. */
    val size: Int get() = state.value.size

    private fun CachedEntry<V>.isExpired(): Boolean = now() - timestamp > ttlMillis

    fun get(key: K): V? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        touch(key, entry)
        return entry.value
    }

    fun peek(key: K): V? {
        val entry = state.value[key] ?: return null
        touch(key, entry)
        return entry.value
    }

    fun observe(key: K): Flow<V?> = state.map { map -> map[key]?.value }.distinctUntilChanged()

    fun put(key: K, value: V) {
        state.update { current ->
            val withoutEvicted = if (current.size >= maxSize && key !in current) {
                val oldestKey = current.entries.minByOrNull { it.value.accessOrder }?.key
                if (oldestKey != null) current - oldestKey else current
            } else current
            withoutEvicted + (key to CachedEntry(value, now(), nextAccessOrder()))
        }
    }

    fun invalidate(key: K) {
        state.update { it - key }
    }

    fun invalidateAll() {
        state.value = emptyMap()
    }

    fun ageMillis(key: K): Long? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        return now() - entry.timestamp
    }

    private fun touch(key: K, entry: CachedEntry<V>) {
        val accessOrder = nextAccessOrder()
        state.update { current ->
            if (current[key] === entry) current + (key to entry.copy(accessOrder = accessOrder)) else current
        }
    }

    private fun nextAccessOrder(): Long {
        var next = 0L
        accessSequence.update { current ->
            (current + 1L).also { next = it }
        }
        return next
    }
}
