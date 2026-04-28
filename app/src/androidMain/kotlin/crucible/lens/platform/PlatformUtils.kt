package crucible.lens.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

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
