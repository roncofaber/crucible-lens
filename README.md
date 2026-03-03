<p align="center">
  <img src="app/src/main/res/drawable/crucible_text_light.png" alt="Crucible Lens" width="640">
</p>

Android app for browsing and scanning samples and datasets from the [Molecular Foundry](https://foundry.lbl.gov/)'s Crucible data system.

## Features

- 📷 QR code scanning and manual UUID lookup
- 🔍 Full-text search across samples and datasets
- 📁 Project browser with pinning and archiving
- 📊 Sample and dataset detail views with swipe-based sibling navigation
- 🖼️ Dataset thumbnails and scientific metadata explorer
- 🔗 Parent/child relationship navigation and Graph Explorer integration
- 📤 QR code sharing for any resource
- 🕐 Browsing history and last-visited shortcut
- 🏠 Home screen widget for quick scanner access
- 🌙 Light/dark theme with accent color picker and switchable app icon

## Requirements

- Android 8.0 (API 26) or higher
- Crucible API key — [get yours here](https://crucible.lbl.gov/api/v1/user_apikey)

## Installation

### For users

Crucible Lens is distributed as an APK (not on the Play Store).

1. On your Android device, go to **Settings → Apps → Special app access → Install unknown apps**
2. Allow your browser or file manager to install apps from unknown sources
3. Download the latest APK from the [Releases](../../releases) page
4. Open the downloaded file and tap **Install**
5. Launch the app → **Settings → API Settings** → enter your API key

### For developers

1. Clone the repository
2. Open in Android Studio (Hedgehog 2023.1.1 or newer)
3. Let Gradle sync complete
4. Run on a device or emulator (API 26+)
5. On first launch, go to **Settings → API Settings** and enter your Crucible API key

## Tech Stack

Kotlin · Jetpack Compose · Material 3 · CameraX · ML Kit · Retrofit · Moshi · Coil

## License

BSD-3-Clause — developed by the Data Group at the [Molecular Foundry](https://foundry.lbl.gov/), Lawrence Berkeley National Laboratory.
