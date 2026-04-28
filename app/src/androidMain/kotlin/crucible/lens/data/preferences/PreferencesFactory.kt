package crucible.lens.data.preferences

import crucible.lens.platform.PlatformContext

actual fun createAppPreferences(context: PlatformContext): AppPreferences =
    PreferencesManager(context.androidContext)
