package crucible.lens.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual object PlatformCrypto {
    @OptIn(ExperimentalForeignApi::class)
    actual fun sha256Hex(bytes: ByteArray): String {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
        bytes.usePinned { src ->
            digest.usePinned { dst ->
                CC_SHA256(src.addressOf(0), bytes.size.toUInt(), dst.addressOf(0))
            }
        }
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
