package crucible.lens.data.network

import kotlinx.coroutines.flow.StateFlow

expect object ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
    fun init(context: Any)
}
