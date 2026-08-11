# Platform Parity: Android vs iOS

All UI lives in `commonMain` and renders identically on both platforms. Platform differences are
isolated to:

- `app/src/androidMain/kotlin/crucible/lens/` - Android actuals
- `app/src/iosMain/kotlin/crucible/lens/` - iOS actuals
- `androidApp/` - thin Android shell (signing, ProGuard, manifest, entry point)
- `iosApp/` - Xcode project + Swift entry point

The iOS entry point is `iosMain/App.kt`, reached via `MainViewController.kt` → `ContentView.swift` →
`iOSApp.swift`.

---

## Fully shared (commonMain)

| Area | Notes |
|---|---|
| All UI screens | Same composables, layout, and Material 3 theme on both platforms |
| Navigation | Single `NavGraph.kt`; every route reachable on both |
| API client + caching | `CrucibleApiService`, `CrucibleRepository`, `ObservableCache`, `PersistentProjectCache`, all data models |
| QR scan + display | `easyqrscan` and `qr-kit` composables |
| ORCID login WebView | `compose-webview-multiplatform` |
| Theme / colour schemes | Identical M3 theme, dark/light, accent colours |
| Preferences reactivity | Both expose `StateFlow` - DataStore on Android, NSUserDefaults on iOS |
| App logo | Both render the real image resource (`crucible_text_dark`/`_light`) - no text fallback |
| Animations, `HorizontalPager`, `LazyColumnScrollbar` | Identical behaviour |
| Pull-to-refresh | `PullToRefreshBox`; the indicator overlays content rather than shifting it, matching M3 and iOS `UIRefreshControl`. Don't slide content via `distanceFraction` - it conflates the user gesture with the refresh animation and bounces |

---

## Platform differences

### Android-only

| Feature | Android | iOS |
|---|---|---|
| **Splash screen** | `androidx.core:core-splashscreen` | None - needs an Xcode launch screen |
| **Deep links** | `intent.data` parsed in `MainActivity` | `deepLinkUuid = null`; needs URL-scheme registration |
| **Dynamic colour** | Android 12+ | Forced `false`. Not a bug - Appearance settings hides the toggle where `supportsDynamicColor()` is false, so no dead control |

### Same feature, different implementation

| Feature | Android | iOS |
|---|---|---|
| Preferences persistence | DataStore (reactive, file-backed) | NSUserDefaults via `multiplatform-settings` |
| Connectivity monitoring | `ConnectivityManager.NetworkCallback` | None - `ConnectivityObserver.isOnline` is hardcoded `true`, assuming iOS reconnects on its own |
| Clipboard | `ClipboardManager` | `UIPasteboard` |
| URL opening | `Intent.ACTION_VIEW` | `UIApplication.openURL` |
| Share sheet | `Intent.ACTION_SEND` via chooser | `UIActivityViewController` |
| Image picker | CameraX + `ActivityResultContracts` | `UIImagePickerController` (camera) + `PHPickerViewController` (gallery) - no third-party library on either platform |
| Toasts | `Toast.makeText` | `showToast()` posts to `platform.ToastBus` (`MutableSharedFlow<String>`), rendered by `ui.common.ToastHost` in `NavGraph`'s root `BoxWithConstraints` |
| App version string | `AppBuildConfig.VERSION_NAME` (generated at build time) | `NSBundle.mainBundle`'s `CFBundleShortVersionString`, falling back to a hardcoded string only if that Info.plist key is missing |
| Camera permission | Explicit permission-request flow with rationale UI | System prompt directly, no custom rationale |

---

## Known gaps on iOS

1. **Deep links** - need a URL scheme (or universal link) in `Info.plist` plus parsing in
   `MainViewController`/`App.kt`.
2. **Splash screen** - add via Xcode (`LaunchScreen.storyboard` or the `UILaunchScreen` Info.plist key).

The app ships on Android only today; neither gap blocks that. Both are tracked here for whenever iOS
distribution becomes a priority.

---

## Building for iOS

**Prerequisites**: macOS with Xcode 16+, Java 17+ on PATH. Kotlin/Native iOS targets cannot build on
Linux - `compileKotlinIosArm64` verifies Kotlin correctness there, but a runnable app needs macOS.

```bash
# 1. Build the debug XCFramework (all simulator + device slices)
#    Output: app/build/XCFrameworks/debug/ComposeApp.xcframework
./gradlew :composeApp:assembleDebugXCFramework

# 2. Generate the Xcode project from iosApp/project.yml (XcodeGen)
xcodegen generate --spec iosApp/project.yml

# 3. Open the .xcodeproj and press ▶, or build the simulator binaries directly
./gradlew :composeApp:iosSimulatorArm64Binaries
```

The framework must be added to the Xcode target as **Embed & Sign** (Project settings → General →
Frameworks, Libraries, Embedded Content). `project.yml` handles this; only a hand-built project needs
it set manually.

### Pointing at a local API

In Settings → API, set the base URL to your Mac's LAN IP, or loopback - the simulator shares the
Mac's localhost:

```
http://192.168.x.x:7778/testapi/
http://127.0.0.1:7778/testapi/
```

### Gradle properties

Already set in `gradle.properties` to suppress iOS-on-Linux warnings:

```properties
kotlin.native.ignoreDisabledTargets=true
android.suppressUnsupportedCompileSdk=36
```

---

## Files that differ between platforms

**Android-only** (`app/src/androidMain/`)
- `MainActivity.kt` - Activity entry point, preference collection, splash screen
- `data/preferences/PreferencesManager.kt` - DataStore implementation

**iOS-only** (`app/src/iosMain/`)
- `App.kt` - composable entry point (replaces the Activity)
- `MainViewController.kt` - bridges Compose to `UIViewController`
- `data/preferences/IosAppPreferences.kt` - NSUserDefaults implementation
- `platform/PlatformInfo.kt` - `AppLogo` (image resource) and `appVersionName()` (`NSBundle` read)
