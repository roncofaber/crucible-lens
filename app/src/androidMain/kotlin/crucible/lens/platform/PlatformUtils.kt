package crucible.lens.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.net.toUri
import crucible.lens.data.model.CrucibleResource
import crucible.lens.ui.common.ShareCardGenerator
import kotlin.math.roundToInt
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

actual fun copyToClipboard(context: PlatformContext, text: String, label: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "ID copied", Toast.LENGTH_SHORT).show()
}

actual fun openUrl(context: PlatformContext, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

actual fun shareText(context: PlatformContext, text: String, subject: String) {
    context.startActivity(
        Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                type = "text/plain"
            }, "Share via"
        )
    )
}

actual fun showToast(context: PlatformContext, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

actual fun currentIsoDateTime(): String {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    return now.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
}

actual fun shareResource(
    context: PlatformContext,
    resource: CrucibleResource,
    shareText: String,
    subject: String,
    darkTheme: Boolean,
    bannerColorValue: Long
) {
    val c = ComposeColor(bannerColorValue.toULong())
    val bannerColorInt = android.graphics.Color.argb(
        (c.alpha * 255).roundToInt(),
        (c.red   * 255).roundToInt(),
        (c.green * 255).roundToInt(),
        (c.blue  * 255).roundToInt()
    )
    val imageUri = ShareCardGenerator.generate(context, resource, bannerColorInt, darkTheme)
    context.startActivity(
        Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (imageUri != null) {
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }, "Share via"
        )
    )
}
