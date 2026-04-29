package crucible.lens.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

actual fun appVersionName(): String = "0.3.0" // TODO: read from Info.plist or build config

@Composable
actual fun AppLogo(isDarkTheme: Boolean, modifier: Modifier) {
    Text(
        text = "Crucible Lens",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}
