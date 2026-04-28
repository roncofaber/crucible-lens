# iOS Migration Status

Branch: `ios-development`

## Architecture

Kotlin Multiplatform + Compose Multiplatform.
- **commonMain** — shared business logic + Compose UI (in progress)
- **androidMain** — Android-specific implementations
- **iosMain** — iOS stubs / actual implementations
- **iosApp/** — Xcode project (Swift entry point)

## Completed

- [x] KMP build system (Gradle, AGP 8.5.2, Kotlin 2.1.0, CMP 1.7.3)
- [x] Data models migrated to `kotlinx.serialization` (`@Serializable`)
- [x] API service migrated from Retrofit to Ktor
- [x] `CacheManager`, `ProjectFetcher`, `SearchExtensions` in commonMain
- [x] `expect`/`actual` for `ConnectivityObserver`
- [x] `expect`/`actual` for `QRCodeScannerView` (iOS stub)
- [x] `expect`/`actual` for `PlatformContext`
- [x] `App` expect/actual entry point
- [x] iOS `MainViewController.kt` (Compose UI bridge)
- [x] iOS `ContentView.swift` + `iOSApp.swift` (Xcode entry)

## In Progress / TODO

### High priority
- [ ] Move all Compose UI screens to `commonMain`
  - Most screens are pure Compose with no Android imports — ready to move
  - Exceptions: screens using `Context`, `Toast`, `Intent` directly
- [ ] Replace `Toast` with multiplatform snackbar/notification
- [ ] Replace `Intent`-based sharing/clipboard with `expect`/`actual`
- [ ] `PreferencesManager` — create multiplatform interface
  - Android: keep DataStore
  - iOS: use `multiplatform-settings` (backed by NSUserDefaults)
- [ ] `PersistentProjectCache` — create `expect`/`actual`
- [ ] `PersistentThumbnailCache` — create `expect`/`actual`

### Medium priority
- [ ] QR scanner iOS implementation (AVFoundation)
- [ ] Camera/photo picker iOS implementation
- [ ] FileProvider → iOS equivalent for thumbnail upload
- [ ] Clipboard iOS implementation (`UIPasteboard`)
- [ ] Share sheet iOS (`UIActivityViewController`)

### Low priority / Polish
- [ ] Splash screen iOS equivalent
- [ ] App icon switching (iOS doesn't support dynamic icons the same way)
- [ ] Xcode project configuration (signing, bundle ID, etc.)

## Building for iOS

Prerequisites: macOS with Xcode 15+

```bash
# Build the KMP framework
./gradlew :composeApp:assembleDebugFramework

# Then open iosApp/ in Xcode and run
```

## Key Library Swaps

| Android | Multiplatform |
|---------|---------------|
| Retrofit + Moshi | Ktor + kotlinx.serialization ✅ |
| DataStore | multiplatform-settings (TODO) |
| Coil 2 | Coil 3 (configured in build) |
| CameraX | AVFoundation stub (TODO) |
| ConnectivityManager | NWPathMonitor stub (TODO) |
