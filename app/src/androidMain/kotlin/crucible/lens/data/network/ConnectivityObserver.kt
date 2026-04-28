package crucible.lens.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object ConnectivityObserver {

    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    actual fun init(context: Any) {
        val ctx = context as Context
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Seed with current state
        _isOnline.value = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }
            override fun onLost(network: Network) {
                _isOnline.value = false
            }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        })
    }
}
