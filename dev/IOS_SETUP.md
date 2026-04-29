# iOS Setup Guide

## Prerequisites

- macOS with Xcode 16+ installed
- The `ios-development` branch checked out
- Java 17+ on PATH (for Gradle)

## Building the KMP framework

```bash
cd crucible-lens

# Build the debug XCFramework (includes all iOS simulator + device slices)
./gradlew :composeApp:assembleDebugXCFramework

# The output is at:
# app/build/XCFrameworks/debug/ComposeApp.xcframework
```

## Creating the Xcode project

The `iosApp/` directory has the two Swift entry-point files but no `.xcodeproj`. Create one in Xcode:

1. Open Xcode → New Project → App (iOS)
2. Product name: `Crucible Lens`, bundle ID: `crucible.lens`
3. Save into `iosApp/`
4. Delete the default `ContentView.swift` and replace with the existing one
5. Add the built `ComposeApp.xcframework` to the project:
   - Project settings → General → Frameworks, Libraries, Embedded Content → `+`
   - Navigate to `app/build/XCFrameworks/debug/ComposeApp.xcframework`
   - Set to **Embed & Sign**
6. In `iOSApp.swift`, the `@main` entry point is already set up

## Running on simulator

```bash
# Or build directly via Gradle (requires Xcode command line tools)
./gradlew :composeApp:iosSimulatorArm64Binaries
```

Then open the `.xcodeproj` in Xcode and press ▶.

## Pointing at a local API

In the app's Settings → API, set the API Base URL to your Mac's local network IP (not localhost — the simulator runs on the Mac but with a different network stack):

```
http://192.168.x.x:7778/testapi/
```

Or use the loopback directly if testing on the simulator (simulator shares the Mac's localhost):
```
http://127.0.0.1:7778/testapi/
```

## Known limitations on iOS (see PLATFORM_PARITY.md for full list)

- No splash screen configured
- App logo shows plain text instead of the branded PNG
- Create Dataset screen is an empty stub
- Toast messages are invisible (no UI feedback for clipboard copy, etc.)
- Dynamic colour toggle in Appearance settings has no effect

## Gradle properties to suppress iOS warnings

Add to `gradle.properties`:

```properties
kotlin.native.ignoreDisabledTargets=true
android.suppressUnsupportedCompileSdk=35
```
