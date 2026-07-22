# Crucible Lens — Project Instructions

## What this is

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing scientific sample/dataset metadata from Lawrence Berkeley Lab's Molecular Foundry Crucible system.

- **Package**: `crucible.lens` — Min SDK 26, compileSdk 36
- **Stack**: Kotlin 2.3.x, CMP 1.10.x, M3 1.4.x, Ktor 3.x, AndroidX ViewModel, DataStore
- **Architecture**: MVVM + Repository, single-module, no DI framework
- **Main branch**: `main`

## Build

```bash
# Compile check (always use this before committing)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"

# Debug APK (arm64-v8a only, signed with debug key, installable)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug

# Release APK (unsigned, needs signing before install)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleRelease
```

APK output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
Drive folder: `/home/roncofaber/WORK/Crucible/App/apk/`
Naming convention: `crucible-lens-v{version}-debug.apk`

Expected build output: `BUILD SUCCESSFUL` with no warnings. The AGP/KMP deprecation warning is resolved — migrated to `com.android.kotlin.multiplatform.library`.

## Project structure

```
app/src/
  commonMain/kotlin/crucible/lens/
    data/
      api/          CrucibleApiService.kt, ApiClient.kt
      cache/        CacheManager.kt, PersistentProjectCache.kt
      model/        CrucibleResource.kt (all models), ResourceSearchResult
      preferences/  AppPreferences.kt (interface)
      repository/   CrucibleRepository.kt
      sync/         DataSyncManager.kt
      util/         DateTimeUtils.kt, SearchExtensions.kt, SortUtils.kt, ...
    ui/
      common/       AppIcons.kt, AppAnimations.kt, LoadState.kt, ErrorCard.kt, FilterSheet.kt, ...
      detail/       ResourceDetailScreen.kt, ResourceDetailViewModel.kt, ResourceDetailCache.kt, components/
      home/         HomeScreen.kt
      instruments/  InstrumentListScreen/ViewModel, InstrumentDetailScreen/ViewModel, ManageInstrumentScreen/ViewModel
      navigation/   NavGraph.kt, Screen.kt
      projects/     ProjectsListScreen/ViewModel, ProjectDetailScreen/ViewModel, ManageProjectScreen/ViewModel
      search/       SearchScreen.kt
      settings/     AccountScreen/ViewModel, ApiSettingsScreen, AppearanceSettingsScreen, ...
  androidMain/      Android actuals (camera picker, Base64, preferences, AppBuildConfig)
  iosMain/          iOS actuals (Base64, NSUserDefaults preferences)
androidApp/         Shell application module (depends on composeApp)
```

## Key architecture decisions

- **`composeApp`** is the KMP library module with all app logic. **`androidApp`** is a thin application shell. Always build `:androidApp:assembleDebug`.
- **No DI framework** — `ApiClient` and `CacheManager` are singletons. ViewModels that need `AppPreferences` take it via constructor: `viewModel { MyViewModel(prefs) }`. Simple ViewModels with no dependencies use `viewModel()` directly.
- **ViewModels** — all feature screens that load data have a ViewModel. Data loading lives in `viewModelScope`, not in composables. State is `StateFlow<LoadState<T>>` (see `ui/common/LoadState.kt`). Screens that currently lack a ViewModel still use `remember`-based state.
- **`LoadState<T>`** — sealed class replacing the `isLoading/error/data/fromCache/isRefreshing` five-variable pattern. States: `Loading`, `Error(message)`, `Success(data, isRefreshing, fromCache)`.
- **`NavGraph`** takes 6 parameters: `navController`, `prefs`, `deepLinkUuid`, `openScanner`, `onScannerOpened`, `viewModel`. All preference flows are collected internally via `collectAsStateWithLifecycle`. All save operations call `prefs.saveXxx()` directly inside NavGraph.
- **`ResourceDetailCache`** — bundles the 5 sibling-pager state maps owned by `ResourceDetailViewModel` into a single typed object passed to `ResourceDetailScreen`.
- **`CacheManager`** is a singleton in-memory cache (10-min TTL, LRU eviction). Cleared on sign-out.
- **`userProfile`** stored as a JSON-serialized `User` object in DataStore under key `user_profile`. `userProfile?.uniqueId` is the source of truth for ORCID.
- **`ApiResult<T>`** sealed class wraps all API calls via `safeCall { }`. Always `is ApiResult.Success` / `is ApiResult.Error`.
- **Pagination**: list endpoints use `fetchAllPagesCursor` (datasets/samples use keyset cursor) or `fetchAllPages` (offset-based). Search endpoints return a flat list.

## API

- Base URL default: `https://crucible.lbl.gov/api/v2/`
- Auth: `Authorization: Bearer {apiKey}` on every request
- Two HTTP clients: `httpClient` (authenticated, 30s timeouts) and `gcsClient` (no auth, 10-min timeouts for large file uploads)
- Key endpoints to know:
  - `GET /account/profile` / `PATCH /account/profile` — own profile
  - `GET /datasets/{id}?include_owner=true&include_links=true` — enriched dataset
  - `GET /samples/{id}?include_owner=true&include_links=true` — enriched sample
  - `POST /datasets/{dsid}/upload/initiate` → `PUT {resumable_uri}` → `POST /datasets/{dsid}/upload/complete` → `POST /files/{mfid}/ingest`
  - `GET /{datasets,samples,projects,instruments}/search?q=` — fuzzy search, flat list
  - `POST /resources/{id}/metadata` — create or replace scientific metadata (`?overwrite=true` to replace)
  - `PATCH /resources/{id}/metadata` — merge update (safe even if no metadata exists yet)
  - `POST /projects/{id}/users/0?username=` — add member by username
  - `DELETE /projects/{id}/users/{orcid}` — remove member by ORCID
  - `GET /users/by-username/{username}` — public profile lookup
  - `POST /users/resolve` — batch resolve ORCIDs/usernames to public profiles

- **Scientific metadata** always uses dedicated `/resources/{id}/metadata` routes — it is NOT part of `SampleUpdateRequest` or `DatasetUpdateRequest`. Create/edit flows make two API calls: one structural PATCH, one metadata POST.

## User identity conventions

- Display format for owners: `F. LastName (@username)` — abbreviated first name, full last name, @username in parens
- Username is the preferred identifier throughout; ORCID is shown as a secondary/tappable link
- `User` model has: `firstName`, `lastName`, `email`, `uniqueId` (ORCID), `username`, `isServiceAccount`
- Shared user UI components: `UserAvatar`, `UserSearchField`, `UserResultItem` in `ui/common/UserComponents.kt`
- Owner rows in detail cards navigate to `UserProfileScreen` — tapping opens the profile, not orcid.org directly

## Icon conventions

All icons use `AppIcon(AppIcons.X)` — never `Icon(Icons.Default.*)`. `AppIcons` is defined in `ui/common/AppIcons.kt`. Icons are Material Symbols Rounded XML in `commonMain/composeResources/drawable/ic_*.xml`.

- **Sample**: `AppIcons.Sample`
- **Dataset**: `AppIcons.Dataset`
- **Instrument**: `AppIcons.Instrument` — everywhere, no exceptions
- **Project**: `AppIcons.Project`
- **Expand in-place**: `ExpandChevron` composable from `AppAnimations.kt` (collapsed = -90°, expanded = 0°)
- **Navigate elsewhere**: `AppIcons.NavigateNext` / `AppIcons.ChevronRight` — different meanings, don't mix
- **Username**: `AppIcons.Username`
- **Session**: `AppIcons.Tag`

Tokens with filled variants (e.g. `AppIcons.Pinned`) support `AppIcon(icon, filled = true)` to switch outline → filled.

## Motion / Animation

All animation specs are defined in `ui/common/AppAnimations.kt` — never inline tweens in UI code.

- **Spatial springs** (movement, rotation, size): `SpatialDefaultSpring`, `SpatialFastSpring`, `SpatialDefaultSizeSpring`, `SpatialFastSizeSpring`
- **Effects springs** (opacity, color): `EffectsDefaultSpring`, `EffectsFastSpring`
- **Backward-compat aliases**: `StandardAnim`, `FastAnim`, `StandardSizeAnim`, `FastSizeAnim` — all spring-based
- **Nav timing constants**: `NavEnterDuration = 300`, `NavExitDuration = 200` — also in `AppAnimations.kt`
- **`ExpandChevron`**: the single composable for all expand/collapse chevrons — use `fast = true` for nested elements
- Navigation screen transitions keep tween-based specs (`NavEnterDuration`/`NavExitDuration`)

## Version management

Single source of truth: `gradle.properties`

```
app.versionName=0.5.0
app.versionCode=8
```

Both `composeApp` and `androidApp` read from these properties. Never hardcode version in build files.

## Signing

- Debug builds: auto-signed with local debug keystore, installable via sideload
- Release builds: reads keystore from env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) or `local.properties`
- `*.jks` and `*.keystore` are git-ignored — never commit

## M3 dependency

No version force needed. CMP 1.10.x naturally resolves to `androidx.compose.material3:material3:1.4.0`. Do not add a `resolutionStrategy { force(...) }` block.

## iOS

Build via XcodeGen (`iosApp/project.yml`). iOS actuals use Foundation for Base64 and `NSUserDefaults`-backed `IosAppPreferences` for preferences. The iOS preferences implementation uses `MutableStateFlow` updated in-place on write.

Note: iOS compilation (`compileKotlinIosArm64`) runs on Linux and verifies Kotlin correctness but cannot produce a runnable app — that requires macOS + Xcode.

## Things that have bitten us before

- `sed` on multiline Kotlin produces literal `\n` — use Python for multiline string replacement
- `kotlinx.serialization`: all `@SerialName` annotations must be explicit on multi-word field names AND single-word fields that need to be consistent (e.g., `@SerialName("email")`)
- Migrated from `com.android.library` to `com.android.kotlin.multiplatform.library` — Android resources/manifest now live in `src/androidMain/` (not `src/main/`). Build config constants generated via `generateAppBuildConfig` Gradle task → `AppBuildConfig.kt` in `androidMain`. Always use `:androidApp:assembleDebug` for the installable app.
- `collectAsState` vs `collectAsStateWithLifecycle` — prefer `collectAsStateWithLifecycle` in NavGraph for lifecycle awareness
- Scientific metadata (`scientific_metadata`) is NOT accepted in `SampleUpdateRequest`, `DatasetUpdateRequest`, `SampleCreateRequest`, or `DatasetCreateRequest`. Always use `POST /resources/{id}/metadata` or `PATCH /resources/{id}/metadata`.
- `SearchBar` in `SearchScreen` uses the deprecated `expanded/onExpandedChange` API (the new `SearchBarState` API requires M3 1.5.0+ which is still alpha). This is intentional — migrate when M3 1.5.0 stabilises.
- `rememberSwipeToDismissBoxState(confirmValueChange)` is deprecated without a clean replacement yet — suppressed with `@Suppress("DEPRECATION")` in `ProjectsListScreen` and `InstrumentListScreen`.
