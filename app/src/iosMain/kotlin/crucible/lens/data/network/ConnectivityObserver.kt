package crucible.lens.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.DISPATCH_QUEUE_SERIAL

actual object ConnectivityObserver {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline

    actual fun init(context: Any) {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_queue_create("connectivity", DISPATCH_QUEUE_SERIAL)
        nw_path_monitor_set_update_handler(monitor) { path ->
            _isOnline.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }
}
