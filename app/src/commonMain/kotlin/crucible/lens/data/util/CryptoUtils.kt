package crucible.lens.data.util

expect object PlatformCrypto {
    fun sha256Hex(bytes: ByteArray): String
}
