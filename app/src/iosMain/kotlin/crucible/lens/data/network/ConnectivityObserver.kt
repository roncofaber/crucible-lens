package crucible.lens.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual object ConnectivityObserver {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline
    actual fun init(context: Any) {
        // TODO: implement with NWPathMonitor
    }
}
