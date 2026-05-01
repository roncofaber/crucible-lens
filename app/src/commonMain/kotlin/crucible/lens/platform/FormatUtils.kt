package crucible.lens.platform

import kotlin.math.pow
import kotlin.math.round

fun formatDecimal(value: Double, places: Int): String {
    val factor = 10.0.pow(places.toDouble())
    val rounded = round(value * factor) / factor
    val str = rounded.toString()
    val dotIdx = str.indexOf('.')
    return if (dotIdx < 0) {
        "$str.${"0".repeat(places)}"
    } else {
        val dec = str.substring(dotIdx + 1).padEnd(places, '0').take(places)
        "${str.substring(0, dotIdx)}.$dec"
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "${formatDecimal(bytes / 1_073_741_824.0, 2)} GB"
    bytes >= 1_048_576     -> "${formatDecimal(bytes / 1_048_576.0, 2)} MB"
    bytes >= 1_024         -> "${formatDecimal(bytes / 1_024.0, 1)} KB"
    else                   -> "$bytes B"
}
