# Platform Parity: Android vs iOS

Branch: `ios-development`  
Last updated: 2026-04-29 (updated after health checks + model alignment)

This document captures where Android and iOS implementations differ, what is fully shared, and what remains incomplete on iOS.

---

## Architecture overview

The app uses **Kotlin Multiplatform + Compose Multiplatform**. All UI screens live in `commonMain` and render identically on both platforms. Platform differences are isolated to:

- `androidMain/kotlin/crucible/lens/` — Android actuals + Android-only code
- `iosMain/kotlin/crucible/lens/` — iOS actuals
- `main/java/crucible/lens/` — Android entry point (`MainActivity`)

The iOS entry point is `iosMain/.../App.kt` (called via `MainViewController.kt` → `ContentView.swift`).

---

## What is fully shared (commonMain)

| Area | Notes |
|---|---|
| All 16 UI screens | Same composables, same layout, same Material 3 theme |
| Navigation | Single `NavGraph.kt` — all 18 routes reachable on both platforms |
| API client | Ktor-based `CrucibleApiService`, `CrucibleRepository`, all data models |
| Caching | `CacheManager` (in-memory, 10 min TTL), `PersistentThumbnailCache` |
| QR scanning | `qr-kit` composable — same `QrScanner` on both platforms |
| QR code display | `qr-kit` `rememberQrKitPainter` — same on both platforms |
| ORCID login WebView | `compose-webview-multiplatform` — same on both platforms |
| Image picker | `moko-media` on iOS; `ActivityResultContracts` on Android |
| Theme / colour schemes | Identical Material 3 theme, dark/light, accent colours |
| Preferences reactivity | Both platforms use `Flow`-based state; iOS uses `MutableStateFlow` + NSUserDefaults |

---

## Platform differences

### Features fully implemented on Android, not on iOS

| Feature | Android | iOS |
|---|---|---|
| **Home screen widget** | `ScannerWidget` (AppWidgetProvider) | Not planned (WidgetKit would be a separate effort) |
| **Dynamic app icon** | Light/dark icon switching via `PackageManager` | `onAppIconSave` is a no-op |
| **Splash screen** | `androidx.core:core-splashscreen` | None configured |
| **Deep links** | `intent.data` parsed in `MainActivity` | `deepLinkUuid = null` (future: URL schemes) |
| **Create Dataset screen** | Full implementation with camera, file picker, Coil image preview | **Empty stub** — shows nothing |

### Features with different underlying implementation

| Feature | Android | iOS |
|---|---|---|
| Preferences persistence | DataStore Preferences (reactive, file-backed) | NSUserDefaults via `multiplatform-settings` |
| Connectivity monitoring | `ConnectivityManager.NetworkCallback` | `NWPathMonitor` |
| Clipboard | `ClipboardManager` | `UIPasteboard` |
| URL opening | `Intent.ACTION_VIEW` | `UIApplication.openURL` |
| Share sheet | `Intent.ACTION_SEND` via chooser | `UIActivityViewController` |
| Toast notifications | `Toast.makeText` | `println` (no visible UI — **gap**, see below) |
| App logo | PNG drawable (light/dark variants) | Plain `Text("Crucible Lens")` — **no branding** |
| App version string | `BuildConfig.VERSION_NAME` | Hardcoded `"0.3.0"` — needs Info.plist read |

---

## Known gaps on iOS

### High priority
1. **Toast notifications** — `showToast()` on iOS just calls `println`. Any save confirmation, copy feedback, or error toast is invisible to the user. Fix: replace with a Compose `SnackbarHost` overlay in `AppScaffold`.
2. **App logo** — `AppLogo` composable shows plain text on iOS. Android uses a PNG. Fix: add the logo images to CMP's `commonMain/composeResources/` and use `painterResource` from the CMP resources API.
3. **Create Dataset screen** — iOS actual is an empty composable. The Android version uses CameraX + FileProvider for photo capture and `ActivityResultContracts` for file picking. On iOS this needs `PHPickerViewController` + `UIImagePickerController` wired via moko-media (already a dependency).

### Low priority
4. **App version string** — hardcoded. Read from `NSBundle.mainBundle.infoDictionary["CFBundleShortVersionString"]` via Kotlin/Native interop.
5. **Deep links** — need iOS URL scheme registration in `Info.plist` + parsing in `MainViewController`.
6. **Splash screen** — no iOS launch screen configured. Add in Xcode project settings.

---

## UI consistency audit

All screens use the same composables from `commonMain`. The theme (`CrucibleScannerTheme`) applies identically. Specific observations:

- **Dynamic colour** — forced `false` on iOS (Android 12+ feature). Settings screen shows the toggle but it has no effect on iOS; consider hiding it.
- **Floating action button (scanner)** — visible on iOS but tapping it opens `QrScanner` (qr-kit). This works on iOS but camera permission handling is simpler than Android's explicit permission request flow.
- **Pull-to-refresh** — uses `PullToRefreshBox` from Material 3 1.3+, works identically on both.
- **Animations** — all `AnimatedVisibility`, `AnimatedContent`, spring animations work identically.
- **HorizontalPager** (resource detail siblings) — works identically.
- **Scrollbars** — `LazyColumnScrollbar` is a custom composable in commonMain, renders the same.

---

## Xcode project setup (required before iOS testing)

The `iosApp/` directory contains only two Swift files (`ContentView.swift`, `iOSApp.swift`). The `.xcodeproj` file must be created on macOS:

```bash
# On macOS:
cd crucible-lens
./gradlew :composeApp:assembleDebugXCFramework   # builds the KMP framework
# Then create the Xcode project pointing to the built framework
```

The Swift entry point is already wired correctly:
- `iOSApp.swift` → `ContentView` → `ComposeView` → `MainViewControllerKt.MainViewController()` → `App()` (Kotlin)
- `App.kt` (iosMain) now wires the full `NavGraph` with `IosAppPreferences`

---

## Files that differ between platforms

### Android-only (`main/java/`)
- `MainActivity.kt` — Activity entry point, preference collection, icon switching
- `data/preferences/PreferencesManager.kt` — DataStore implementation

### iOS-only (`iosMain/`)
- `App.kt` — Composable entry point (replaces Activity)
- `MainViewController.kt` — bridges Compose to UIViewController
- `data/preferences/IosAppPreferences.kt` — NSUserDefaults implementation
- `platform/PlatformInfo.kt` — `AppLogo` shows text; `appVersionName()` hardcoded

### Android-only screens (`androidMain/`)
- `ui/create/CreateDatasetScreen.kt` — full implementation
- `widget/ScannerWidget.kt` — home screen widget
