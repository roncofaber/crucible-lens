package crucible.lens.platform

import android.util.Base64

actual object PlatformBase64 {
    actual fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    actual fun decode(str: String): ByteArray =
        Base64.decode(str, Base64.DEFAULT)
}
