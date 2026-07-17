# Crucible Lens — Project Instructions

## What this is

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing scientific sample/dataset metadata from Lawrence Berkeley Lab's Molecular Foundry Crucible system.

- **Package**: `crucible.lens` — Min SDK 34, compileSdk 36
- **Stack**: Kotlin 2.x, CMP 1.7.x, Ktor 3.x, AndroidX ViewModel, DataStore
- **Architecture**: MVVM + Repository, single-module, no DI framework
- **Main branch**: `main`

## Build

```bash
# Compile check (always use this before committing)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileDebugKotlin 2>&1 | grep -E "^w:|^e:|BUILD"

# Debug APK (86 MB — arm64-v8a only, signed with debug key, installable)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug

# Release APK (13 MB — unsigned, needs signing before install)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleRelease
```

APK output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
Drive folder: `/home/roncofaber/WORK/Crucible/App/apk/`
Naming convention: `crucible-lens-v{version}-debug.apk`

Expected build output: `BUILD SUCCESSFUL` with only the known AGP/KMP compatibility warning (`org.jetbrains.kotlin.multiplatform` deprecated with `com.android.library`). That warning is harmless and not actionable.

## Project structure

```
app/src/
  commonMain/kotlin/crucible/lens/
    data/
      api/          CrucibleApiService.kt, ApiClient.kt
      cache/        CacheManager.kt
      model/        CrucibleResource.kt (all models)
      preferences/  AppPreferences.kt (interface)
      repository/   CrucibleRepository.kt
      sync/         DataSyncManager.kt
      util/         DateTimeUtils.kt, SearchExtensions.kt, ...
    ui/
      common/       Shared composables (UserComponents, ErrorCard, FilterSheet, ...)
      detail/       ResourceDetailScreen + components/
      home/         HomeScreen
      instruments/  InstrumentListScreen, InstrumentDetailScreen, ManageInstrumentScreen
      navigation/   NavGraph.kt, Screen.kt
      projects/     ProjectsListScreen, ProjectDetailScreen, ManageProjectScreen
      search/       SearchScreen
      settings/     AccountScreen, ApiSettingsScreen, AppearanceSettingsScreen, ...
  androidMain/      Android actuals (camera picker, Base64, preferences)
  iosMain/          iOS actuals (moko-media camera, Base64, NSUserDefaults preferences)
androidApp/         Shell application module (depends on composeApp)
```

## Key architecture decisions

- **`composeApp`** is the KMP library module with all app logic. **`androidApp`** is a thin application shell that just depends on it. Always build `:androidApp:assembleDebug`, not `:composeApp`.
- **No DI framework** — `ApiClient` is a singleton, ViewModels take `AppPreferences` via constructor and are created with `viewModel { MyViewModel(prefs) }` factory lambda.
- **`CacheManager`** is a singleton in-memory cache (10-min TTL, LRU eviction). Cleared on sign-out.
- **`userProfile`** stored as a JSON-serialized `User` object in DataStore under key `user_profile`. `userOrcid` (legacy key) is no longer written — `userProfile?.uniqueId` is the source of truth.
- **`ApiResult<T>`** sealed class wraps all API calls via `safeCall { }`. Always `is ApiResult.Success` / `is ApiResult.Error`.
- **Pagination**: list endpoints use `fetchAllPagesCursor` (datasets/samples use keyset cursor) or `fetchAllPages` (offset-based). Search endpoints return a flat list — no pagination handling needed.

## API

- Base URL default: `https://crucible.lbl.gov/api/v2/`
- Auth: `Authorization: Bearer {apiKey}` on every request
- Two HTTP clients: `httpClient` (authenticated, 30s timeouts) and `gcsClient` (no auth, 10-min timeouts for large file uploads)
- Key endpoints to know:
  - `GET /account/profile` / `PATCH /account/profile` — own profile
  - `GET /datasets/{id}?include_owner=true&include_links=true` — enriched dataset
  - `GET /samples/{id}?include_owner=true&include_links=true` — enriched sample
  - `POST /datasets/{dsid}/upload/initiate` → `PUT {resumable_uri}` → `POST /datasets/{dsid}/upload/complete` → `POST /files/{mfid}/ingest`
  - `GET /{datasets,samples,projects,instruments}/search?q=` — fuzzy search, returns flat list
  - `POST /projects/{id}/users/0?username=` — add member by username
  - `DELETE /projects/{id}/users/{orcid}` — remove member by ORCID

## User identity conventions

- Display format for owners: `F. LastName (@username)` — abbreviated first name, full last name, @username in parens
- Username is the preferred identifier throughout; ORCID is shown as a secondary/tappable link
- `User` model (was `UserLead`) has: `firstName`, `lastName`, `email`, `uniqueId` (ORCID), `username`, `isServiceAccount`
- Shared user UI components: `UserAvatar`, `UserSearchField`, `UserResultItem` in `ui/common/UserComponents.kt`

## Icon conventions

- **Sample**: `Icons.Default.Science`
- **Dataset**: `Icons.Default.Dataset`
- **Instrument**: `Icons.Default.Biotech` — everywhere, no exceptions
- **Project**: `Icons.Default.Folder`
- **Expand in-place**: `ExpandMore` (collapsed) / `ExpandLess` (expanded)
- **Navigate elsewhere**: `ChevronRight` — these have different meanings, don't mix them
- **Username**: `Icons.Default.Badge`
- **Session**: `Icons.Default.Tag`
- **Created timestamp**: `CalendarToday`; **Timestamp field**: `Schedule`; **Modified**: `Update`

## M3 dependency conflict (important)

`moko-media` and `moko-permissions` transitively pull in an older `material3`. Fixed by forcing M3 version in both `app/build.gradle.kts` and `androidApp/build.gradle.kts`:

```kotlin
configurations.configureEach {
    resolutionStrategy { force("androidx.compose.material3:material3:1.3.1") }
}
```

Do not remove this — it will crash with `NoSuchMethodError: ExposedDropdownMenuBox`.

## Version management

Single source of truth: `gradle.properties`

```
app.versionName=0.4.2
app.versionCode=7
```

Both `composeApp` and `androidApp` read from these properties. Never hardcode version in build files.

## Signing

- Debug builds: auto-signed with local debug keystore, installable via sideload
- Release builds: reads keystore from env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) or `local.properties`
- `*.jks` and `*.keystore` are git-ignored — never commit

## iOS

Build via XcodeGen (`iosApp/project.yml`). iOS actuals use `moko-media` for camera/gallery, Foundation for Base64, `NSUserDefaultsSettings` for preferences. The iOS preferences implementation uses `MutableStateFlow` updated in-place on write (see `IosAppPreferences.kt`).

## Things that have bitten us before

- `sed` on multiline Kotlin produces literal `\n` — use Python for multiline string replacement
- `kotlinx.serialization`: all `@SerialName` annotations must be explicit on multi-word field names AND single-word fields that need to be consistent (e.g., `@SerialName("email")`)
- `composeApp` is `com.android.library` — its intermediates contain a `.apk` for testing but it is NOT the installable app. Always use `:androidApp:assembleDebug`
- The AGP/KMP deprecation warning about `com.android.library` is expected and harmless until we migrate to `com.android.kotlin.multiplatform.library`
- `collectAsState` vs `collectAsStateWithLifecycle` — prefer `collectAsStateWithLifecycle` in NavGraph for lifecycle awareness
