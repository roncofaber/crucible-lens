package crucible.lens.data.util

import java.security.MessageDigest

actual object PlatformCrypto {
    actual fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
