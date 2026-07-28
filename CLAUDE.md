# Crucible Lens — Project Instructions

## What this is

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing scientific sample/dataset metadata from Lawrence Berkeley Lab's Molecular Foundry Crucible system.

- **Package**: `crucible.lens` — Min SDK 26, compileSdk 36
- **Stack**: Kotlin 2.3.x, CMP 1.10.x, M3 1.4.x, Ktor 3.x, AndroidX ViewModel, DataStore, Koin 4.2.0
- **Architecture**: MVVM + Repository, single-module, Koin for DI
- **Main branch**: `main`

## Build

```bash
# Compile check (always use this before committing)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"

# Debug APK (arm64-v8a only, signed with debug key, installable)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug

# Release APK (signed — reads keystore from env vars or local.properties, see "Signing" below)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleRelease

# Release AAB (signed, for Play Console upload)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:bundleRelease
```

APK/AAB output: `androidApp/build/outputs/apk/{debug,release}/androidApp-{debug,release}.apk`, `androidApp/build/outputs/bundle/release/androidApp-release.aab`
Drive folder: `/home/roncofaber/WORK/Crucible/App/apk/` (symlink to `~/Insync/GDrive_LBL/WORK/Crucible/App/apk/`)
Naming convention: `crucible-lens-v{version}-debug.apk`, `crucible-lens-v{version}-release.aab`

Expected build output: `BUILD SUCCESSFUL` with no warnings. The AGP/KMP deprecation warning is resolved — migrated to `com.android.kotlin.multiplatform.library`.

Verify a release build is actually signed before uploading anywhere: `apksigner verify --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk` (or unzip the `.aab` and check for `META-INF/*.RSA`) — a silently-unsigned release previously shipped undetected because `signingConfig` no-ops instead of failing when the keystore path resolves to `null`.

`.github/workflows/release.yml` builds and signs a release APK/AAB in CI on a pushed `v*.*.*` tag (or manual `workflow_dispatch`), using `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` repo secrets, and includes the same signature verification step.

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
    di/             AppModule.kt (Koin module), KoinInit.kt (initKoin())
    ui/
      common/       AppIcons.kt, AppAnimations.kt, LoadState.kt, ErrorCard.kt, FilterSheet.kt, ...
      detail/       ResourceDetailScreen.kt, ResourceDetailViewModel.kt, components/
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
- **Koin DI** — `ApiClient`, `CacheManager`, `CrucibleRepository`, and `DataSyncManager` are registered as Koin `single`s; ViewModels are registered via `viewModelOf(::MyViewModel)`, all in `di/AppModule.kt`. Screens obtain them via `koinInject<T>()` / `koinViewModel()`. `initKoin(platformModule)` runs once at app start (guarded against double-init) — see `dev/ARCHITECTURE.md`'s "Dependency injection (Koin)" section for the platform-module pattern and the small set of leaf composables (`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`) that call `koinInject` directly instead of receiving dependencies as parameters (documented architectural debt, not an oversight).
- **ViewModels** — all feature screens that load data have a ViewModel. Data loading lives in `viewModelScope`, not in composables. State is `StateFlow<LoadState<T>>` (see `ui/common/LoadState.kt`). Screens that currently lack a ViewModel still use `remember`-based state.
- **`LoadState<T>`** — sealed class replacing the `isLoading/error/data/fromCache/isRefreshing` five-variable pattern. States: `Loading`, `Error(message)`, `Success(data, isRefreshing, fromCache)`.
- **`NavGraph`** takes 6 parameters: `navController`, `prefs`, `deepLinkUuid`, `openScanner`, `onScannerOpened`, `viewModel`. All preference flows are collected internally via `collectAsStateWithLifecycle`. All save operations call `prefs.saveXxx()` directly inside NavGraph.
- **`ResourceDetailScreen`** takes a `uuid: String`, not a resource object — it and every pager page observe `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly. There is no resource-object-keyed Compose state anywhere in this screen; "enriched" is derived from `resource?.links != null`, not tracked in a separate set.
- **`CrucibleRepository`** is the single source of truth for cached reads — resources, projects (list + per-project, including non-member projects reached via discover-search), instruments, project/instrument sample/dataset lists, and thumbnails are all backed by `ObservableCache<K, V>` (10-min TTL, LRU eviction) and exposed via `observeX()`/`fetchX()`/`getCachedX()`. `CacheManager` (10-min TTL, LRU eviction, cleared on sign-out) still exists for a few screens/ViewModels not yet migrated (e.g. `HomeScreen`'s pinned-projects preload), and will be deleted once those move over. One-shot mutation/lookup calls that don't need caching (add/remove member, review a join request, `getProjectUsers`) go straight through `ApiClient` from the owning ViewModel instead — that's an accepted pattern, not debt.
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
  - `GET /{datasets,samples,projects,instruments}/search?q=` — fuzzy search, flat list. `GET /projects/search` and `GET /projects/{proj_id}` are readable by any authenticated user (not just members) — `lead` is full `UserRead` (with email) and `scientific_metadata` is populated only for members/admins; non-members get `lead` as `UserPublicRead` (no email) and `scientific_metadata` always null, regardless of `?include_metadata=`.
  - `POST /resources/{id}/metadata` — create or replace scientific metadata (`?overwrite=true` to replace)
  - `PATCH /resources/{id}/metadata` — merge update (safe even if no metadata exists yet)
  - `POST /projects/{id}/users/0?username=` — add member by username
  - `DELETE /projects/{id}/users/{orcid}` — remove member by ORCID
  - `GET /users/by-username/{username}` — public profile lookup
  - `POST /users/resolve` — batch resolve ORCIDs/usernames to public profiles
  - `POST /access_groups/{group_name}/join` — request to join a project (`group_name` is always a `project_id` for now); 409 if already a member or already has a pending request
  - `GET /join_requests?group_name=&status=&requester_id=` — list join requests; admin or the project's lead only (lead must pass `group_name`)
  - `PATCH /join_requests/{request_id}` — approve/reject a pending request; admin or the request's project lead only; approval adds the requester as a project member server-side
  - `GET /account/join_requests?status=` — the caller's own join-request history across all projects

- **Scientific metadata** always uses dedicated `/resources/{id}/metadata` routes — it is NOT part of `SampleUpdateRequest` or `DatasetUpdateRequest`. Create/edit flows make two API calls: one structural PATCH, one metadata POST.
- **Join requests** are one-shot mutations/lookups called directly from ViewModels (`ManageProjectViewModel`, `AccountViewModel`, `ProjectDetailScreen`) via `apiClient.service.*` — no `CrucibleRepository` wrapper, since there's no shared caching need across them.

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
- **Instrument**: `AppIcons.Instrument` — everywhere, no exceptions. Static filled/solid glyph (`ic_biotech.xml`, matching `Project`'s visual weight) — not a stateful toggle like `Pinned`, since there's no real on/off state to represent.
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

## Theming

- **`ui/theme/Theme.kt`** — `CrucibleScannerTheme` composable. Passes `colorScheme`, `typography = Typography`, and `shapes = Shapes` to `MaterialTheme`.
- **`ui/theme/Type.kt`** — full M3 type scale declared explicitly (all 15 roles), matching Compose Material3 1.4.0's `TypographyTokens` defaults, so the type ramp is visible and intentional rather than implicit. `bodyLarge` is the one deliberate customization (`FontFamily.Default` instead of the M3-default `SansSerif`).
- **`ui/theme/Shape.kt`** — full M3 shape scale declared explicitly (`extraSmall` 4dp, `small` 8dp, `medium` 12dp, `large` 16dp, `extraLarge` 28dp), matching Compose Material3 1.4.0's `ShapeTokens` defaults. Always reference `MaterialTheme.shapes.X` in UI code — never a hardcoded `RoundedCornerShape(N.dp)`.
- **Accent colors**: 10 named palettes (blue, purple, green, orange, red, teal, pink, indigo, amber, brown) plus a custom-hex path, each with light/dark `ColorScheme` variants — all defined in `Theme.kt`. Dynamic color (`dynamicLightColorScheme`/`dynamicDarkColorScheme`, Android 12+/API 31+) takes priority when enabled.
- **No adaptive/window-size-class layout** — the app targets phones only; this is a deliberate scope decision, not an oversight. `ProjectDetailScreen`/`ResourceDetailScreen` use a single fixed-width column with no multi-pane list-detail behavior on large screens/tablets.

## Version management

Single source of truth: `gradle.properties`

```
app.versionName=0.7.0
app.versionCode=11
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
