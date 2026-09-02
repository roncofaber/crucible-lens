package crucible.lens.data.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class CacheEpoch {
    private val value = MutableStateFlow(0L)

    fun capture(): Long = value.value

    fun advance() {
        value.update { it + 1L }
    }

    fun isCurrent(epoch: Long): Boolean = value.value == epoch
}
