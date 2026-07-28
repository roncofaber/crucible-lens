# Platform Parity: Android vs iOS

Branch: `main`
Last updated: 2026-07-27

This document captures where Android and iOS implementations differ, what is fully shared, and what remains incomplete on iOS. It also covers the local iOS build/test setup, since the two are closely related.

---

## Architecture overview

The app uses **Kotlin Multiplatform + Compose Multiplatform**. All UI screens live in `commonMain` and render identically on both platforms. Platform differences are isolated to:

- `app/src/androidMain/kotlin/crucible/lens/` — Android actuals
- `app/src/iosMain/kotlin/crucible/lens/` — iOS actuals
- `androidApp/` — thin Android application shell (signing, ProGuard, manifest, entry point)
- `iosApp/` — Xcode project + Swift entry point

The iOS entry point is `iosMain/App.kt` (called via `MainViewController.kt` → `ContentView.swift` → `iOSApp.swift`).

---

## What is fully shared (commonMain)

| Area | Notes |
|---|---|
| All UI screens, including `CreateDatasetScreen` | Same composables, same layout, same Material 3 theme, on both platforms |
| Navigation | Single `NavGraph.kt` — all 23 routes reachable on both platforms |
| API client | Ktor-based `CrucibleApiService`, `CrucibleRepository`, all data models |
| Caching | `CrucibleRepository`'s `ObservableCache`s, `CacheManager`, `PersistentProjectCache` |
| QR scanning | `easyqrscan` composable — same scanner on both platforms |
| QR code display | `qr-kit` `rememberQrKitPainter` — same on both platforms |
| ORCID login WebView | `compose-webview-multiplatform` — same on both platforms |
| Image picker | Native `UIImagePickerController`/`PHPickerViewController` on iOS; `ActivityResultContracts` + CameraX on Android — no third-party image-picker library on either platform |
| Theme / colour schemes | Identical Material 3 theme, dark/light, accent colours |
| Preferences reactivity | Both platforms expose `StateFlow` — Android via DataStore, iOS via NSUserDefaults (`multiplatform-settings`) |
| App logo | Both platforms render the actual logo image resource (`crucible_text_dark`/`crucible_text_light`) — no plain-text fallback on either platform |
| App version string | Android reads `AppBuildConfig.VERSION_NAME` (generated at build time); iOS reads `NSBundle.mainBundle`'s `CFBundleShortVersionString`, falling back to a hardcoded string only if that Info.plist key is missing |
| Toast notifications | Native `Toast.makeText` on Android; `showToast()` posts to `platform.ToastBus` (`MutableSharedFlow<String>`) on iOS, rendered by `ui.common.ToastHost` — a Compose banner hosted once in `NavGraph`'s root `BoxWithConstraints` |

---

## Platform differences

### Features fully implemented on Android, not on iOS

| Feature | Android | iOS |
|---|---|---|
| **Splash screen** | `androidx.core:core-splashscreen` | None configured — needs an Xcode launch screen |
| **Deep links** | `intent.data` parsed in `MainActivity` | `deepLinkUuid = null` (future: URL scheme registration) |

### Features with different underlying implementation

| Feature | Android | iOS |
|---|---|---|
| Preferences persistence | DataStore Preferences (reactive, file-backed) | NSUserDefaults via `multiplatform-settings` |
| Connectivity monitoring | `ConnectivityManager.NetworkCallback` | `NWPathMonitor` |
| Clipboard | `ClipboardManager` | `UIPasteboard` |
| URL opening | `Intent.ACTION_VIEW` | `UIApplication.openURL` |
| Share sheet | `Intent.ACTION_SEND` via chooser | `UIActivityViewController` |
| Image picker | CameraX + `ActivityResultContracts` | `UIImagePickerController` (camera) + `PHPickerViewController` (gallery) |

---

## Known gaps on iOS

1. **Deep links** — need iOS URL scheme (or universal link) registration in `Info.plist` plus parsing in `MainViewController`/`App.kt`.
2. **Splash screen** — no iOS launch screen configured. Add via Xcode project settings (`LaunchScreen.storyboard` or the newer `UILaunchScreen` Info.plist key).
3. **Dynamic colour** — forced `false` on iOS (Android 12+-only feature). Not a bug: the Appearance settings screen hides the dynamic-colour toggle entirely on platforms where `supportsDynamicColor()` returns false, so there's no dead control shown to iOS users.

This app is currently submitted to app stores on Android only; the iOS gaps above do not block that submission and are tracked here for whenever iOS distribution becomes a priority.

---

## UI consistency audit

All screens use the same composables from `commonMain`. The theme (`CrucibleScannerTheme`) applies identically. Specific observations:

- **Floating action button (scanner)** — visible on iOS; tapping it opens the shared QR scanner composable. Camera permission handling on iOS uses the system prompt directly (no custom rationale UI), simpler than Android's explicit permission-request flow.
- **Pull-to-refresh** — uses `PullToRefreshBox` from Material 3 1.4+, works identically on both. Content deliberately does not shift during the pull gesture — the indicator overlays content instead, matching M3 and iOS `UIRefreshControl` convention (an earlier content-slide attempt via `distanceFraction` produced bounce artifacts, since that API conflates user gesture with internal refresh-state animation).
- **Animations** — all `AnimatedVisibility`, `AnimatedContent`, spring animations work identically.
- **HorizontalPager** (resource detail siblings) — works identically.
- **Scrollbars** — `LazyColumnScrollbar` is a custom composable in `commonMain`, renders the same.

---

## Building for iOS

### Prerequisites

- macOS with Xcode 16+ installed
- Java 17+ on PATH (for Gradle)

Note: Kotlin/Native iOS targets cannot build on Linux. `compileKotlinIosArm64` etc. verify Kotlin correctness on Linux, but producing a runnable app requires macOS + Xcode.

### Building the KMP framework

```bash
cd crucible-lens

# Build the debug XCFramework (includes all iOS simulator + device slices)
./gradlew :composeApp:assembleDebugXCFramework

# The output is at:
# app/build/XCFrameworks/debug/ComposeApp.xcframework
```

### Xcode project setup

The `iosApp/` directory is generated via XcodeGen (`iosApp/project.yml`):

```bash
# On macOS:
cd crucible-lens
xcodegen generate --spec iosApp/project.yml   # generates the .xcodeproj
```

If setting up from scratch (no `project.yml` yet), create the Xcode project manually:

1. Open Xcode → New Project → App (iOS)
2. Product name: `Crucible Lens`, bundle ID: `crucible.lens`
3. Save into `iosApp/`
4. Delete the default `ContentView.swift` and replace with the existing one
5. Add the built `ComposeApp.xcframework` to the project:
   - Project settings → General → Frameworks, Libraries, Embedded Content → `+`
   - Navigate to `app/build/XCFrameworks/debug/ComposeApp.xcframework`
   - Set to **Embed & Sign**
6. In `iOSApp.swift`, the `@main` entry point is already set up

The Swift entry point is wired as:
`iOSApp.swift` → `ContentView` → `ComposeView` → `MainViewControllerKt.MainViewController()` → `App()` (Kotlin, `iosMain/App.kt`, wires the full `NavGraph` with `IosAppPreferences`).

### Running on simulator

```bash
# Or build directly via Gradle (requires Xcode command line tools)
./gradlew :composeApp:iosSimulatorArm64Binaries
```

Then open the `.xcodeproj` in Xcode and press ▶.

### Pointing at a local API

In the app's Settings → API, set the API Base URL to your Mac's local network IP (not localhost — the simulator runs on the Mac but with a different network stack):

```
http://192.168.x.x:7778/testapi/
```

Or use the loopback directly if testing on the simulator (simulator shares the Mac's localhost):
```
http://127.0.0.1:7778/testapi/
```

### Gradle properties to suppress iOS warnings

Already set in `gradle.properties`:

```properties
kotlin.native.ignoreDisabledTargets=true
android.suppressUnsupportedCompileSdk=36
```

---

## Files that differ between platforms

### Android-only (`app/src/androidMain/`)
- `MainActivity.kt` — Activity entry point, preference collection, splash screen
- `data/preferences/PreferencesManager.kt` — DataStore implementation

### iOS-only (`app/src/iosMain/`)
- `App.kt` — Composable entry point (replaces Activity)
- `MainViewController.kt` — bridges Compose to `UIViewController`
- `data/preferences/IosAppPreferences.kt` — NSUserDefaults implementation
- `platform/PlatformInfo.kt` — `AppLogo` (image resource) and `appVersionName()` (`NSBundle` read)
