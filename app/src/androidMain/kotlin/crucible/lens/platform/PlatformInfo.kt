package crucible.lens.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import crucible.lens.AppBuildConfig
import crucible.lens.R

actual fun appVersionName(): String = AppBuildConfig.VERSION_NAME

@Composable
actual fun AppLogo(isDarkTheme: Boolean, modifier: Modifier) {
    Image(
        painter = painterResource(
            id = if (isDarkTheme) R.drawable.crucible_text_dark else R.drawable.crucible_text_light
        ),
        contentDescription = "Crucible",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
