package crucible.lens.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual object PlatformBase64 {
    @OptIn(ExperimentalEncodingApi::class)
    actual fun encode(bytes: ByteArray): String = Base64.encode(bytes)
    @OptIn(ExperimentalEncodingApi::class)
    actual fun decode(str: String): ByteArray = Base64.decode(str)
}
