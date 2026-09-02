package crucible.lens

import crucible.lens.ui.navigation.DeepLinkTarget
import crucible.lens.ui.navigation.parseDeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object IosDeepLinkHandler {
    private val _deepLinkTarget = MutableStateFlow<DeepLinkTarget?>(null)
    val deepLinkTarget = _deepLinkTarget.asStateFlow()

    fun handleUrl(url: String) {
        _deepLinkTarget.value = parseDeepLink(url)
    }

    fun clear() {
        _deepLinkTarget.value = null
    }
}
