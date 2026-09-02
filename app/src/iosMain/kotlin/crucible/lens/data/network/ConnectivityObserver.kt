package crucible.lens.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

actual object ConnectivityObserver {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    private var monitor: nw_path_monitor_t = null

    actual fun init(context: Any) {
        if (monitor != null) return
        val pathMonitor = nw_path_monitor_create() ?: return
        nw_path_monitor_set_update_handler(pathMonitor) { path ->
            _isOnline.value = path != null && nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(pathMonitor, dispatch_get_main_queue())
        nw_path_monitor_start(pathMonitor)
        monitor = pathMonitor
    }
}
