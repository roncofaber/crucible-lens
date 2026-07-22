package crucible.lens.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ToastBus {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun post(message: String) {
        _messages.tryEmit(message)
    }
}
