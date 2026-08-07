# Crucible Lens - Project Instructions

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing scientific sample/dataset
metadata from LBNL's Molecular Foundry Crucible system.

- **Package** `crucible.lens` · **Main branch** `main` · min SDK 26, compileSdk 36
- **Stack** Kotlin 2.3.x, CMP 1.10.x, M3 1.4.x, Ktor 3.x, AndroidX ViewModel, DataStore, Koin 4.2.0
- **Architecture** MVVM + Repository, single-module, Koin DI

## Documentation map

This file is the fast-reference layer. Deep reference lives in `dev/*.md`. **When you change
something one of these files describes, update that file in the same change** - two sources of truth
for one fact is exactly how this file went stale before.

| File | Covers | Update it when you... |
|---|---|---|
| `dev/architecture.md` | Stack, package layout, data models, endpoints, caching (`CrucibleRepository`/`ObservableCache`), ViewModels, pull-to-refresh, navigation, Koin details, testing, gotchas | Add/rename a package, add an endpoint, change caching, add a ViewModel, change DI wiring, add a test |
| `dev/style.md` | Compose `@OptIn` conventions, spacing, rows/cards, elevation, typography, animation, collapsing top bar, search/pick patterns, dialogs | Establish or change a project-wide UI convention |
| `dev/platform-parity.md` | What's shared vs. platform-specific, iOS gaps, iOS build/Xcode setup | Add a platform-specific feature, close an iOS gap, change the iOS build |
| `dev/icons.md` | Material Symbols download manifest - icon names and fill variants per `AppIcons` token | Add a new icon token |

Multi-step workflows are skills in `.claude/skills/`, loaded on demand:

| Skill | Use it to... |
|---|---|
| `/release` | Cut a tagged release. **Never release by hand** - a past one silently shipped unsigned, and the skill's ordering exists to catch that |
| `add-screen` | Add a screen: route + `encodeRouteSegment`, NavGraph entry, ViewModel, Koin registration, scaffold |
| `add-api-endpoint` | Add/change an API call: `safeCall`/`ApiResult`, pagination helper, `ObservableCache` wiring, metadata routes |
| `/audit-docs` | Periodically re-verify this file and `dev/*.md` against the code |

## Build

```bash
# Compile check
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"

# Unit tests (~2s)
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:testAndroidHostTest

# Debug APK (arm64-v8a, debug-signed, installable)
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :androidApp:assembleDebug
```

Expected output: `BUILD SUCCESSFUL` with **no warnings**.

`.claude/hooks/pre-commit-check.sh` runs **both** the compile check and the tests on every `git
commit` issued through the Bash tool, and blocks on failure, so neither needs remembering. It reports
compile errors and failing test names directly. (~0.8s when Gradle is up to date; ~0.02s on
non-commit commands. Commits made by hand in another terminal are not gated.)

`composeApp` (the `app/` module) is the KMP library holding all app logic; `androidApp` is a thin
shell. Always build `:androidApp:assembleDebug`, never `:app:assembleDebug`.

## Tests

There **is** a test suite. `.claude/hooks/pre-commit-check.sh` blocks a commit whose tests fail, and
`scripts/release.sh` runs them again in its verify step, so a red suite blocks both a commit and a
release.

- **Location**: `app/src/commonTest/kotlin/`, mirroring the production package path.
- **Framework**: `kotlin.test` (`@Test`, `assertEquals`, `assertTrue`) plus
  `kotlinx-coroutines-test` (`runTest`) for anything returning a `Flow`. Not JUnit; no mocking library.
- **Scope today**: deterministic pure logic in `data/` only, currently `ObservableCache`,
  `CrucibleRepository`'s no-network paths, `FormatUtils`, and sync suggestions. Nothing in `ui/` is
  tested and there is no instrumented or screenshot suite.

**Add a test when you add or change pure logic in `data/`** - cache/TTL behavior, formatting,
sorting, grouping, or any pure function with branches. Details and patterns in `dev/architecture.md`.

## Version management

Single source of truth: `gradle.properties` (`app.versionName`, `app.versionCode`). Both modules read
from these. Never hardcode a version in a build file, and never bump one outside the `/release` skill.

## Changelog discipline

Add a `CHANGELOG.md` entry under `## [Unreleased]` **as part of the same change** that makes something
user-visible - not at release time. Entries are never backfilled from git history; if it isn't
written when the change lands, it's lost. Create `## [Unreleased]` at the top of the file if absent.

**Style**: one short line per entry, under ~12 words, hard cap one sentence. State *what* changed for
the user, never *why* or *how* - no root cause, no file/class/hex-color/package names, no
parentheticals. That detail belongs in the commit message. Group under `### Added`/`### Changed`/
`### Fixed`, only the sections that apply. Purely internal refactors get no entry, but a bug fix that
fell out of one does.

- Bad: "A debug build could fail to install (\"App not installed\") over an existing release install, since both shared the same package ID with different signing keys - debug builds now use `gov.lbl.crucible.debug`."
- Good: "Fixed debug builds failing to install over a release build."

## Key architecture decisions

- **Koin DI** - `ApiClient`, `CrucibleRepository`, `DataSyncManager` are `single`s; ViewModels use
  `viewModelOf(::X)`. All in `di/AppModule.kt`. Screens use `koinInject<T>()` / `koinViewModel()`.
  **A missing registration compiles fine and throws at runtime.**
- **ViewModels** - every feature screen that loads data has one. Loading lives in `viewModelScope`,
  never in a composable.
- **`LoadState<T>`** (`ui/common/LoadState.kt`) - `Loading` / `Error(message)` /
  `Success(data, isRefreshing, fromCache)`. `isRefreshingNow` is the pull-to-refresh flag.
- **`CrucibleRepository`** is the single source of truth for in-memory caching, via
  `ObservableCache<K, V>`. There is no separate legacy cache.
- **`ApiResult<T>`** wraps every API call via `safeCall { }` - always branch
  `is ApiResult.Success` / `is ApiResult.Error`.
- **`NavGraph`** takes 5 parameters: `navController`, `deepLinkUuid`, `openScanner`,
  `onScannerOpened`, `viewModel`. `AppPreferences` is **not** one of them - obtain it internally via
  `koinInject<AppPreferences>()`, collect with `collectAsStateWithLifecycle`, and call
  `prefs.saveXxx()` directly.
- **`ResourceDetailScreen`** takes a `uuid: String`, not a resource object. It and every pager page
  observe `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly; "enriched"
  is derived from `resource?.links != null`, not tracked separately.
- **`userProfile`** - a JSON-serialized `User` in DataStore. `userProfile?.uniqueId` is the source of
  truth for ORCID.

## API rules

Full endpoint list in `dev/architecture.md`; procedure in the `add-api-endpoint` skill. These must
hold even if that skill never loads:

- Auth header is `Authorization: Bearer {apiKey}` on every request - not `Api-Key`, not `Token`.
- **`scientific_metadata` is silently dropped from `SampleUpdateRequest`, `DatasetUpdateRequest`,
  `SampleCreateRequest`, and `DatasetCreateRequest`.** It only moves via `POST`/`PATCH
  /resources/{id}/metadata`, so create and edit flows always make two calls. Both return
  `ApiResult<JsonObject>`, never `ApiResult<Unit>`.
- **Join requests** are called directly from the owning ViewModel via `apiClient.service.*` - no
  repository wrapper, deliberately, since there's nothing to cache.
- `GET /projects/search` and `GET /projects/{id}` are readable by **any** authenticated user;
  `lead`/`scientific_metadata` populate only for members and admins. That's what makes discover-search
  and non-member browsing work.
- **Pagination**: `fetchAllPagesCursor` (datasets/samples, keyset) or `fetchAllPages` (offset). Search
  endpoints return a flat list.

## User identity conventions

- **Display format** for someone already in context (owner rows, project leads, member/requester
  lists): full name via `userDisplayName()` (`data/util/FormatUtils.kt`) - no username by default.
  Falls back to `@username`, then the raw ORCID. Every such row is one tap from the full profile.
- **Exception**: `UserProfileScreen`'s own header stays username-primary (`@username` as the page
  title). Search-result rows (`UserResultItem`, `UserPickerItemContent`) are name-first like
  everywhere else, with `@username` smaller below - the server matches first/last name too, so
  username is no longer the disambiguator.
- `User` has `firstName`, `lastName`, `email`, `uniqueId` (ORCID), `username`, `isServiceAccount`.
- Shared components in `ui/common/UserComponents.kt`: `UserAvatar`, `UserIdentityRow`,
  `UserResultItem`, `UserPickerItemContent`. `UserIdentityRow` is the default for a "name + avatar,
  tap for profile" row; use `userDisplayName()` directly only when a custom layout rules it out.
- `UserAvatar`'s background derives deterministically from `orcid` - **always pass
  `orcid = user.uniqueId`**; omitting it silently falls back to a static color.
- Owner rows navigate to `UserProfileScreen`, not to orcid.org.

## UI conventions

Full detail in `dev/style.md`. The non-negotiables:

- **Icons**: `AppIcon(AppIcons.X)`, never `Icon(Icons.Default.*)`. Tokens live in
  `ui/common/AppIcons.kt`, backed by Material Symbols Rounded XML in `composeResources/drawable/`.
  Sample → `Sample`, Dataset → `Dataset`, Project → `Project`, Instrument → `Instrument` (everywhere,
  no exceptions). Tokens with filled variants take `AppIcon(icon, filled = true)`.
- **Chevrons**: expand in place → the `ExpandChevron` composable (`AppAnimations.kt`); navigate
  elsewhere → `AppIcons.NavigateNext`. Don't mix the two.
- **Motion**: all specs in `ui/common/AppAnimations.kt` - never inline a tween. Spatial springs for
  movement/rotation/size, effects springs for opacity/color. Navigation transitions keep tween specs
  (`NavEnterDuration = 300`, `NavExitDuration = 200`).
- **Elevation**: `AppElevation.Level0`-`Level5` (0/1/3/6/8/12dp) - never an inline `Xdp`.
  `Level4`/`Level5` are hover/focus/drag only, never a resting value.
- **Shapes**: always `MaterialTheme.shapes.X`, never a hardcoded `RoundedCornerShape(N.dp)`.
- **Typography**: stock M3 ramp only, declared in `ui/theme/Type.kt`. No `fontSize`/`fontWeight`
  outside that file.
- **No adaptive/window-size-class layout** - phones only. A deliberate scope decision.

**Theming** (`ui/theme/`): 12 named accents in `accents/`, each a hand-curated static `ColorScheme`
exported from the Material Theme Builder - 3 contrast variants × 2 modes = 6 static schemes per
accent, no runtime color generation. `resolveAccentColorScheme()` is a plain lookup. The user picks
accent and contrast independently in Settings → Appearance (`AppPreferences.accentColor`/
`accentContrast`). Dynamic color (Android 12+) is a separate system that derives its own scheme from
the wallpaper and takes priority when enabled.

**M3 dependency**: CMP 1.10.x resolves `material3:1.4.0` naturally. Do not add a
`resolutionStrategy { force(...) }` block.

## iOS

`compileKotlinIosArm64` runs on Linux and verifies Kotlin correctness but cannot produce a runnable
app - that needs macOS + Xcode. Full setup in `dev/platform-parity.md`.

## Things that have bitten us before

- **`sed` on multiline Kotlin produces literal `\n`** - use Python for multiline string replacement.
- **kotlinx.serialization**: `@SerialName` must be explicit on multi-word field names *and* on
  single-word fields that need to stay stable (e.g. `@SerialName("email")`). Relying on the Kotlin
  property name is how a field silently stops deserializing after a rename.
- **Build config**: constants are generated by the `generateAppBuildConfig` Gradle task into
  `AppBuildConfig.kt` in `androidMain`.
- **`collectAsStateWithLifecycle`, not `collectAsState`**, in NavGraph.
- **`SearchScreen`'s `SearchBar`** deliberately uses the deprecated `expanded`/`onExpandedChange`
  API; the replacement `SearchBarState` needs M3 1.5.0, still alpha. Migrate when it stabilises.
- **Swipe-to-hide** lives in one shared `ui/common/SwipeToHideItem.kt`: bare
  `rememberSwipeToDismissBoxState()` plus `SwipeToDismissBox`'s `onDismiss`. Don't reintroduce the
  deprecated `confirmValueChange` overload. Re-arm a dismissed item by changing the caller's `key()`,
  never by resetting the state (see that file's KDoc).
- **The only `@Suppress("DEPRECATION")` in `ui/`** is `DateTimePickerField.kt` (kotlinx-datetime's
  `monthNumber`/`dayOfMonth`). Keep it that way.
- **Edge-to-edge**: targeting SDK 35+ means the OS enforces it unconditionally, but status/nav bar
  icon *contrast* isn't automatic. `MainActivity.kt` sets `isAppearanceLightStatusBars`/
  `isAppearanceLightNavigationBars` from the **resolved** `darkTheme`, not raw system dark mode, so
  they follow the in-app Light/Dark/System preference.
- Compose lifecycle traps (`remember` vs `rememberSaveable`, `getPlatformContext()` being
  `@Composable`, expect/actual defaults) - see `dev/architecture.md`'s "Common gotchas".
