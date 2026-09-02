package crucible.lens.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SingleFlight<K, V> {
    private val mutex = Mutex()
    private val requests = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        var leader = false
        val request = mutex.withLock {
            requests[key] ?: CompletableDeferred<V>().also {
                requests[key] = it
                leader = true
            }
        }
        if (!leader) return request.await()

        try {
            return block().also(request::complete)
        } catch (error: Throwable) {
            request.completeExceptionally(error)
            throw error
        } finally {
            mutex.withLock {
                if (requests[key] === request) requests.remove(key)
            }
        }
    }
}
