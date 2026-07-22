package crucible.lens.ui.common

/**
 * Unified state machine for screens that load data from the network.
 * Replaces the common pattern of separate isLoading/error/data/isRefreshing/fromCache variables.
 */
sealed class LoadState<out T> {
    data object Loading : LoadState<Nothing>()
    data class Error(val message: String) : LoadState<Nothing>()
    data class Success<T>(
        val data: T,
        val isRefreshing: Boolean = false,
        val fromCache: Boolean = false
    ) : LoadState<T>()

    /** True only when data is loaded and a pull-to-refresh is in progress. */
    val isRefreshingNow: Boolean get() = this is Success && this.isRefreshing
}
