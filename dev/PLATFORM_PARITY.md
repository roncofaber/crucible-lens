# Platform Parity: Android vs iOS

Branch: `main`
Last updated: 2026-07-21

This document captures where Android and iOS implementations differ, what is fully shared, and what remains incomplete on iOS.

---

## Architecture overview

The app uses **Kotlin Multiplatform + Compose Multiplatform**. All UI screens live in `commonMain` and render identically on both platforms. Platform differences are isolated to:

- `app/src/androidMain/kotlin/crucible/lens/` — Android actuals + Android-only screens
- `app/src/iosMain/kotlin/crucible/lens/` — iOS actuals
- `androidApp/` — thin Android application shell (signing, ProGuard, manifest, entry point)
- `iosApp/` — Xcode project + Swift entry point

The iOS entry point is `iosMain/App.kt` (called via `MainViewController.kt` → `ContentView.swift` → `iOSApp.swift`).

---

## What is fully shared (commonMain)

| Area | Notes |
|---|---|
| All UI screens, including `CreateDatasetScreen` | Same composables, same layout, same Material 3 theme, on both platforms |
| Navigation | Single `NavGraph.kt` — all 20 routes reachable on both platforms |
| API client | Ktor-based `CrucibleApiService`, `CrucibleRepository`, all data models |
| Caching | `CacheManager` (in-memory, 10 min TTL), `PersistentProjectCache` |
| QR scanning | `easyqrscan` composable — same scanner on both platforms |
| QR code display | `qr-kit` `rememberQrKitPainter` — same on both platforms |
| ORCID login WebView | `compose-webview-multiplatform` — same on both platforms |
| Image picker | Native `UIImagePickerController`/`PHPickerViewController` on iOS; `ActivityResultContracts` + CameraX on Android — no third-party image-picker library on either platform |
| Theme / colour schemes | Identical Material 3 theme, dark/light, accent colours |
| Preferences reactivity | Both platforms expose `StateFlow` — Android via DataStore, iOS via NSUserDefaults (`multiplatform-settings`) |
| App logo | Both platforms render the actual logo image resource (`crucible_text_dark`/`crucible_text_light`) — no plain-text fallback on either platform |
| App version string | Android reads `AppBuildConfig.VERSION_NAME` (generated at build time); iOS reads `NSBundle.mainBundle`'s `CFBundleShortVersionString`, falling back to a hardcoded string only if that Info.plist key is missing |

---

## Platform differences

### Features fully implemented on Android, not on iOS

| Feature | Android | iOS |
|---|---|---|
| **Splash screen** | `androidx.core:core-splashscreen` | None configured — needs an Xcode launch screen |
| **Deep links** | `intent.data` parsed in `MainActivity` | `deepLinkUuid = null` (future: URL scheme registration) |
| **Toast notifications** | `Toast.makeText` | `println` only — see gap below |

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

### High priority
1. **Toast notifications** — `showToast()` on iOS currently calls `println` only. Any save confirmation, copy feedback, or error toast is invisible to the user. Fix: replace with a Compose `SnackbarHost` overlay hosted in `AppScaffold`, or a small platform-native banner.

### Low priority
2. **Deep links** — need iOS URL scheme (or universal link) registration in `Info.plist` plus parsing in `MainViewController`/`App.kt`.
3. **Splash screen** — no iOS launch screen configured. Add via Xcode project settings (`LaunchScreen.storyboard` or the newer `UILaunchScreen` Info.plist key).

This app is currently submitted to app stores on Android only; the iOS gaps above do not block that submission and are tracked here for whenever iOS distribution becomes a priority.

---

## UI consistency audit

All screens use the same composables from `commonMain`. The theme (`CrucibleScannerTheme`) applies identically. Specific observations:

- **Dynamic colour** — forced `false` on iOS (Android 12+ feature, gated by `supportsDynamicColor()`). The Appearance settings screen hides the dynamic-colour toggle entirely on platforms where `supportsDynamicColor()` returns false, so there is no dead control shown to iOS users.
- **Floating action button (scanner)** — visible on iOS; tapping it opens the shared QR scanner composable. Camera permission handling on iOS uses the system prompt directly (no custom rationale UI), simpler than Android's explicit permission-request flow.
- **Pull-to-refresh** — uses `PullToRefreshBox` from Material 3 1.4+, works identically on both.
- **Animations** — all `AnimatedVisibility`, `AnimatedContent`, spring animations work identically.
- **HorizontalPager** (resource detail siblings) — works identically.
- **Scrollbars** — `LazyColumnScrollbar` is a custom composable in `commonMain`, renders the same.

---

## Xcode project setup (required before iOS testing)

The `iosApp/` directory is generated via XcodeGen (`iosApp/project.yml`) and contains the Swift entry point:

```bash
# On macOS:
cd crucible-lens
xcodegen generate --spec iosApp/project.yml   # generates the .xcodeproj
```

The Swift entry point is wired as:
- `iOSApp.swift` → `ContentView` → `ComposeView` → `MainViewControllerKt.MainViewController()` → `App()` (Kotlin)
- `App.kt` (`iosMain`) wires the full `NavGraph` with `IosAppPreferences`

Note: Kotlin/Native iOS targets cannot build on Linux. `compileKotlinIosArm64` etc. verify Kotlin correctness on Linux, but producing a runnable app requires macOS + Xcode. See `dev/IOS_SETUP.md` for details.

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
