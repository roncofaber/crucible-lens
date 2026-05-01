package crucible.lens.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import crucible.lens.composeapp.generated.resources.Res
import crucible.lens.composeapp.generated.resources.crucible_text_dark
import crucible.lens.composeapp.generated.resources.crucible_text_light
import org.jetbrains.compose.resources.painterResource
import platform.Foundation.NSBundle

actual fun appVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.3.0"

@Composable
actual fun AppLogo(isDarkTheme: Boolean, modifier: Modifier) {
    Image(
        painter = painterResource(if (isDarkTheme) Res.drawable.crucible_text_dark else Res.drawable.crucible_text_light),
        contentDescription = "Crucible",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
