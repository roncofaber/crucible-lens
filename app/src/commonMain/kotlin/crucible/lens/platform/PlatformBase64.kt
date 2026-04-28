package crucible.lens.platform

expect object PlatformBase64 {
    fun encode(bytes: ByteArray): String
    fun decode(str: String): ByteArray
}
