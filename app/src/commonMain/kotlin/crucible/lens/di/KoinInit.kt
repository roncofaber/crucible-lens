package crucible.lens.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin with the shared [appModule] plus a platform module supplying
 * AppPreferences (needs a PlatformContext on Android, nothing on iOS).
 * Call exactly once per process — Android from MainActivity.onCreate()
 * before setContent{}, iOS from App() guarded so it only runs on first call.
 */
fun initKoin(platformModule: Module, config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModule, platformModule)
    }
}
