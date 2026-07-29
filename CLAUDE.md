# Crucible Lens — Project Instructions

## What this is

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing scientific sample/dataset metadata from Lawrence Berkeley Lab's Molecular Foundry Crucible system.

- **Package**: `crucible.lens` — Min SDK 26, compileSdk 36
- **Stack**: Kotlin 2.3.x, CMP 1.10.x, M3 1.4.x, Ktor 3.x, AndroidX ViewModel, DataStore, Koin 4.2.0
- **Architecture**: MVVM + Repository, single-module, Koin for DI
- **Main branch**: `main`

## Documentation map

Deep reference material (concepts, design decisions, full structure) lives in `dev/*.md`, not here — this file is the fast-reference/operational layer. **When you change something one of these files describes, update that file in the same change**, not just this one; two sources of truth for the same fact is exactly how this file went stale before (Koin, signing, join requests all drifted here at once).

| File | Covers | Update it when you... |
|---|---|---|
| `dev/architecture.md` | Full stack table, package-by-package layout, data models, full API endpoint list, caching layers (`CrucibleRepository`/`ObservableCache` vs legacy `CacheManager`), ViewModels, pull-to-refresh pattern, navigation routes, Koin DI details, common gotchas, known gaps | Add/rename a package, add an API endpoint, change caching behavior, add a ViewModel, change DI wiring |
| `dev/style.md` | Compose `@OptIn` conventions, spacing/layout values, card/typography styles, `AnimatedVisibility` list-item pattern, "no comments" rule | Establish or change a UI styling convention that should apply project-wide |
| `dev/platform-parity.md` | What's shared vs. Android/iOS-only, known iOS gaps, iOS build/Xcode setup instructions | Add a platform-specific feature, close an iOS gap, change the iOS build process |
| `dev/icons.md` | Material Symbols download manifest — exact icon names/fill variants per `AppIcons` token | Add a new icon token |

## Build

```bash
# Compile check (always use this before committing)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"

# Debug APK (arm64-v8a only, signed with debug key, installable)
JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug
```

Expected build output: `BUILD SUCCESSFUL` with no warnings.

## Release process

Follow all of these steps, in order, every time — not just when explicitly asked to build:

1. Bump `gradle.properties` (`app.versionName`, `app.versionCode`) and add a `CHANGELOG.md` entry.
   - **Changelog style**: one line per entry, what changed for the user — not why, not implementation detail. If a rationale or root cause matters, it belongs in the commit message, not here. Group under `### Added`/`### Changed`/`### Fixed` (only the sections that apply). Above the groups, add a one- or two-sentence summary paragraph of the release — longer only if genuinely necessary — since the GitHub release notes lead with it (see step 6).
2. Verify: `:composeApp:compileAndroidMain`, `:composeApp:testAndroidHostTest`, `:composeApp:compileKotlinIosArm64`.
3. Build both release artifacts:
   ```bash
   JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug :androidApp:bundleRelease
   ```
   Output: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`, `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
4. **Verify the release bundle is actually signed** before going any further: `apksigner verify --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk` (or unzip the `.aab` and check for `META-INF/*.RSA`). A past release silently shipped unsigned because `signingConfig` no-ops instead of failing when the keystore path resolves to `null` — don't skip this.
5. **Always copy both artifacts to the Drive folder** — every release, not just on request:
   ```bash
   cp androidApp/build/outputs/apk/debug/androidApp-debug.apk \
     ~/WORK/Crucible/App/apk/crucible-lens-v{version}-debug.apk
   cp androidApp/build/outputs/bundle/release/androidApp-release.aab \
     ~/WORK/Crucible/App/apk/crucible-lens-v{version}-release.aab
   ```
   (`~/WORK` is a symlink to `~/Insync/GDrive_LBL/WORK`.)
6. Commit, then push. `.github/workflows/release.yml` builds and signs a release APK/AAB in CI on a pushed `v*.*.*` tag (or manual `workflow_dispatch`), using `KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` repo secrets, and includes the same signature-verification step. The draft GitHub release's notes are generated from `CHANGELOG.md`: the version's summary paragraph, followed by a link to `CHANGELOG.md` for the full list — not a copy of every bullet. The workflow fails the release if that version has no summary paragraph, so step 1's changelog style isn't optional.

## Project structure

Top-level module layout only — see `dev/architecture.md` for the full package-by-package breakdown.

```
app/src/commonMain/  Shared code — all UI screens, API, models, cache, navigation, DI modules
app/src/androidMain/ Android actuals (camera picker, Base64, preferences, AppBuildConfig)
app/src/iosMain/     iOS actuals (Base64, NSUserDefaults preferences)
androidApp/          Thin Android application shell (signing, manifest) — depends on app/
iosApp/              Xcode project (via XcodeGen) — depends on app/
```

`composeApp` (the `app/` module) is the KMP library with all app logic; `androidApp` is a thin shell. Always build `:androidApp:assembleDebug`, never `:app:assembleDebug`.

## Key architecture decisions

- **Koin DI** — `ApiClient`, `CacheManager`, `CrucibleRepository`, and `DataSyncManager` are registered as Koin `single`s; ViewModels are registered via `viewModelOf(::MyViewModel)`, all in `di/AppModule.kt`. Screens obtain them via `koinInject<T>()` / `koinViewModel()`. Full platform-module pattern and the leaf-composable exceptions (`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`) are documented in `dev/architecture.md`.
- **ViewModels** — all feature screens that load data have a ViewModel. Data loading lives in `viewModelScope`, not in composables. State is `StateFlow<LoadState<T>>` (see `ui/common/LoadState.kt`). Screens that currently lack a ViewModel still use `remember`-based state.
- **`LoadState<T>`** — sealed class replacing the `isLoading/error/data/fromCache/isRefreshing` five-variable pattern. States: `Loading`, `Error(message)`, `Success(data, isRefreshing, fromCache)`.
- **`NavGraph`** takes 6 parameters: `navController`, `prefs`, `deepLinkUuid`, `openScanner`, `onScannerOpened`, `viewModel`. All preference flows are collected internally via `collectAsStateWithLifecycle`. All save operations call `prefs.saveXxx()` directly inside NavGraph.
- **`ResourceDetailScreen`** takes a `uuid: String`, not a resource object — it and every pager page observe `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly. There is no resource-object-keyed Compose state anywhere in this screen; "enriched" is derived from `resource?.links != null`, not tracked in a separate set.
- **`CrucibleRepository`** is the single source of truth for cached reads (resources, projects, instruments, sample/dataset lists, thumbnails) via `ObservableCache<K, V>`. Legacy `CacheManager` still backs a few not-yet-migrated call sites. Full breakdown, including which caches exist and what's left to migrate, is in `dev/architecture.md`'s "Caching layers" section.
- **`userProfile`** stored as a JSON-serialized `User` object in DataStore under key `user_profile`. `userProfile?.uniqueId` is the source of truth for ORCID.
- **`ApiResult<T>`** sealed class wraps all API calls via `safeCall { }`. Always `is ApiResult.Success` / `is ApiResult.Error`.
- **Pagination**: list endpoints use `fetchAllPagesCursor` (datasets/samples use keyset cursor) or `fetchAllPages` (offset-based). Search endpoints return a flat list.

## API

Full endpoint list is in `dev/architecture.md`. The rules that matter while coding:

- Auth header is `Authorization: Bearer {apiKey}` on every request — not `Api-Key` or `Token`.
- **Scientific metadata** always uses dedicated `POST`/`PATCH /resources/{id}/metadata` routes — it is NOT part of `SampleUpdateRequest`, `DatasetUpdateRequest`, `SampleCreateRequest`, or `DatasetCreateRequest`. Create/edit flows make two API calls: one structural PATCH/POST, one metadata call.
- **Join requests** (`requestToJoinProject`, `getJoinRequests`, `reviewJoinRequest`, `getMyJoinRequests`) are one-shot mutations/lookups called directly from the owning ViewModel/screen via `apiClient.service.*` — no `CrucibleRepository` wrapper, since there's no caching need shared across them.
- `GET /projects/search` and `GET /projects/{proj_id}` are readable by any authenticated user, not just members — `lead`/`scientific_metadata` are populated only for members/admins. This is what makes discover-search and non-member project browsing possible.

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

`compileKotlinIosArm64` runs on Linux and verifies Kotlin correctness but cannot produce a runnable app — that requires macOS + Xcode. Full iOS build/Xcode setup and platform-parity details: `dev/platform-parity.md`.

## Things that have bitten us before

- `sed` on multiline Kotlin produces literal `\n` — use Python for multiline string replacement
- `kotlinx.serialization`: all `@SerialName` annotations must be explicit on multi-word field names AND single-word fields that need to be consistent (e.g., `@SerialName("email")`)
- Migrated from `com.android.library` to `com.android.kotlin.multiplatform.library` — Android resources/manifest now live in `src/androidMain/` (not `src/main/`). Build config constants generated via `generateAppBuildConfig` Gradle task → `AppBuildConfig.kt` in `androidMain`. Always use `:androidApp:assembleDebug` for the installable app.
- `collectAsState` vs `collectAsStateWithLifecycle` — prefer `collectAsStateWithLifecycle` in NavGraph for lifecycle awareness
- Scientific metadata (`scientific_metadata`) is NOT accepted in `SampleUpdateRequest`, `DatasetUpdateRequest`, `SampleCreateRequest`, or `DatasetCreateRequest`. Always use `POST /resources/{id}/metadata` or `PATCH /resources/{id}/metadata`.
- `SearchBar` in `SearchScreen` uses the deprecated `expanded/onExpandedChange` API (the new `SearchBarState` API requires M3 1.5.0+ which is still alpha). This is intentional — migrate when M3 1.5.0 stabilises.
- `rememberSwipeToDismissBoxState(confirmValueChange)` is deprecated without a clean replacement yet — suppressed with `@Suppress("DEPRECATION")` in `ProjectsListScreen` and `InstrumentListScreen`.
