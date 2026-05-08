package crucible.lens.data.util

expect object PlatformCrypto {
    fun sha256Hex(bytes: ByteArray): String
}

// CRC32C (Castagnoli) — pure Kotlin, no platform dependency.
// Used for the X-Goog-Hash header required by GCS resumable uploads.
private val CRC32C_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0x82F63B78.toInt() xor (c ushr 1)) else (c ushr 1) }
    c
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun crc32cBase64(data: ByteArray): String {
    var crc = -1
    for (b in data) crc = CRC32C_TABLE[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
    val value = (crc xor -1).toLong() and 0xFFFFFFFFL
    val bytes = ByteArray(4) { ((value ushr (24 - it * 8)) and 0xFF).toByte() }
    return kotlin.io.encoding.Base64.encode(bytes)
}
