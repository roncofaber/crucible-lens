package crucible.lens.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun appVersionName(): String

@Composable
expect fun AppLogo(isDarkTheme: Boolean, modifier: Modifier = Modifier)
