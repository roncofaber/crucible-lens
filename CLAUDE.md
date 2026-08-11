# Crucible Lens - Project Instructions

Android + iOS app (KMP + Compose Multiplatform) for browsing and managing sample/dataset metadata
from LBNL's Molecular Foundry Crucible system.

- **Package** `crucible.lens` · **Main branch** `main` · min SDK 26, compileSdk 36
- **Stack** Kotlin 2.3.x, CMP 1.10.x, M3 1.4.x, Ktor 3.x, AndroidX ViewModel, DataStore, Koin 4.2.0
- **Architecture** MVVM + Repository, single-module, Koin DI

## Documentation map

This file is the fast-reference layer; `dev/*.md` is the deep reference. **Change something a `dev/`
file describes, update that file in the same change.**

| File | Covers | Update when you... |
|---|---|---|
| `dev/architecture.md` | Package layout, data models, endpoints, caching, ViewModels, navigation, Koin, testing, gotchas | Add/rename a package, add an endpoint, change caching or DI, add a ViewModel or test |
| `dev/style.md` | Compose opt-ins, spacing, cards, elevation, typography, animation, collapsing top bar, search/pick, dialogs | Establish or change a UI convention |
| `dev/platform-parity.md` | Shared vs platform-specific, iOS gaps, iOS build and Xcode setup | Add a platform-specific feature, close an iOS gap, change the iOS build |
| `dev/icons.md` | Material Symbols manifest: icon name and fill variant per `AppIcons` token | Add an icon token |

Skills in `.claude/skills/`, loaded on demand:

| Skill | Use it to... |
|---|---|
| `/release` | Cut a tagged release. **Never release by hand** - one shipped unsigned that way |
| `add-screen` | Add a screen: route + `encodeRouteSegment`, NavGraph entry, ViewModel, Koin registration |
| `add-api-endpoint` | Add/change an API call: `safeCall`/`ApiResult`, pagination, `ObservableCache` wiring |
| `/audit-docs` | Re-verify this file and `dev/*.md` against the code |

## Build

```bash
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew <task>
```

| Task | Purpose |
|---|---|
| `:composeApp:compileAndroidMain` | Compile check. Pipe through `grep -E "^w:\|^e:\|BUILD"` |
| `:composeApp:testAndroidHostTest` | Unit tests (~2s) |
| `:androidApp:assembleDebug` | Installable debug APK (arm64-v8a, debug-signed) |

Expect `BUILD SUCCESSFUL` with **no warnings**.

`composeApp` (the `app/` module) is the KMP library holding all app logic; `androidApp` is a thin
shell. Build `:androidApp:assembleDebug`, never `:app:assembleDebug`.

`.claude/hooks/pre-commit-check.sh` runs the compile check and the tests on every `git commit` issued
through the Bash tool and blocks on failure, reporting compile errors and failing test names.
Commits made by hand in another terminal are not gated.

## Tests

Location `app/src/commonTest/kotlin/`, mirroring the production package path. Framework is
`kotlin.test` plus `kotlinx-coroutines-test` (`runTest`) for anything returning a `Flow`. Not JUnit;
no mocking library. A red suite blocks both a commit (pre-commit hook) and a release
(`scripts/release.sh` verify step).

Scope today is deterministic pure logic in `data/` only: `ObservableCache`, `CrucibleRepository`'s
no-network paths, `FormatUtils`, sync suggestions. Nothing in `ui/` is tested; there is no
instrumented or screenshot suite.

**Add a test when you add or change pure logic in `data/`** - cache/TTL behavior, formatting,
sorting, grouping, any pure function with branches. Patterns in `dev/architecture.md`.

## Versioning

`gradle.properties` (`app.versionName`, `app.versionCode`) is the single source of truth; both
modules read it. Never hardcode a version in a build file. Never bump one outside `/release`.

## Changelog discipline

Add a `CHANGELOG.md` entry under `## [Unreleased]` **in the same change** that makes something
user-visible, not at release time. Entries are never backfilled from git history; unwritten means
lost. Create `## [Unreleased]` at the top if absent.

Group under `### Added`/`### Changed`/`### Fixed`, only the sections that apply. One line per entry,
under ~12 words, hard cap one sentence. State *what* changed for the user, never *why* or *how* - no
root cause, no file/class/hex-color/package names, no parentheticals; that belongs in the commit
message. Internal refactors get no entry, but a bug fix that fell out of one does.

- Bad: "A debug build could fail to install (\"App not installed\") over an existing release install, since both shared the same package ID with different signing keys - debug builds now use `gov.lbl.crucible.debug`."
- Good: "Fixed debug builds failing to install over a release build."

## Privacy policy discipline

`PRIVACY.md` is a legal representation published on the Play listing, not internal docs. It is served
at <https://roncofaber.github.io/crucible-lens/privacy/> and linked from `AboutSettingsScreen.kt`;
keep both in sync if either moves.

**Never edit it without asking first.** Unlike the changelog, do not update it in the same change -
raise the impact, propose wording, wait for a decision.

Ask whether it needs updating when a change: stores something new on the device or changes how long
it is kept; sends data to a host it does not already list; adds a permission or platform capability;
adds a dependency that phones home (the "no analytics, crash-reporting, advertising" claim is true
and verifiable today); changes what personal data the app shows or sends about *other* users; touches
`backup_rules.xml` or `data_extraction_rules.xml`.

**Scope is the client only**: what is stored on the device, which servers are contacted, what is
sent. Never claim how the Crucible backend stores, retains, secures, or deletes data - LBNL controls
that. Write "the app sends X", never "the server keeps X for Y days". LBNL is named in exactly two
load-bearing places, the non-affiliation disclaimer and the one statement of who operates the API;
elsewhere "the organization that operates Crucible" is deliberate. Prefer "the app does not" over
"never", which is a warranty that must hold across every future release.

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
  `onScannerOpened`, `viewModel`. `AppPreferences` is **not** one - obtain it internally via
  `koinInject<AppPreferences>()`, collect with `collectAsStateWithLifecycle`, call `prefs.saveXxx()`
  directly.
- **`ResourceDetailScreen`** takes a `uuid: String`, not a resource object. It and every pager page
  observe `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly; "enriched"
  is derived from `resource?.links != null`, not tracked separately.
- **`userProfile`** - a JSON-serialized `User` in DataStore. `userProfile?.uniqueId` is the source of
  truth for ORCID.

## API rules

Full endpoint list in `dev/architecture.md`, procedure in `add-api-endpoint`. These hold even if that
skill never loads:

- Auth header is `Authorization: Bearer {apiKey}` on every request - not `Api-Key`, not `Token`.
- **`scientific_metadata` is silently dropped from `SampleUpdateRequest`, `DatasetUpdateRequest`,
  `SampleCreateRequest`, and `DatasetCreateRequest`.** It only moves via `POST`/`PATCH
  /resources/{id}/metadata`, so create and edit flows always make two calls. Both return
  `ApiResult<JsonObject>`, never `ApiResult<Unit>`.
- **Join requests** are called directly from the owning ViewModel via `apiClient.service.*`,
  deliberately without a repository wrapper, since there is nothing to cache.
- `GET /projects/search` and `GET /projects/{id}` are readable by **any** authenticated user;
  `lead`/`scientific_metadata` populate only for members and admins. This is what makes
  discover-search and non-member browsing work.
- **Pagination**: `fetchAllPagesCursor` (datasets/samples, keyset) or `fetchAllPages` (offset). Search
  endpoints return a flat list.

## User identity conventions

- **Display format** for someone already in context (owner rows, project leads, member and requester
  lists): full name via `userDisplayName()` (`data/util/FormatUtils.kt`), no username by default.
  Falls back to `@username`, then raw ORCID. Every such row is one tap from the full profile.
- **Exception**: `UserProfileScreen`'s own header stays username-primary (`@username` as page title).
  Search-result rows (`UserResultItem`, `UserPickerItemContent`) are name-first with `@username`
  smaller below; the server matches first and last name too, so username no longer disambiguates.
- `User` has `firstName`, `lastName`, `email`, `uniqueId` (ORCID), `username`, `isServiceAccount`.
- Shared components in `ui/common/UserComponents.kt`: `UserAvatar`, `UserIdentityRow`,
  `UserResultItem`, `UserPickerItemContent`. `UserIdentityRow` is the default for a "name + avatar,
  tap for profile" row; use `userDisplayName()` directly only when a custom layout rules it out.
- `UserAvatar`'s background derives deterministically from `orcid` - **always pass
  `orcid = user.uniqueId`**; omitting it silently falls back to a static color.
- Owner rows navigate to `UserProfileScreen`, not to orcid.org.

## UI conventions

Full detail in `dev/style.md`. The non-negotiables:

- **Icons**: `AppIcon(AppIcons.X)`, never `Icon(Icons.Default.*)`. Tokens in `ui/common/AppIcons.kt`,
  backed by Material Symbols Rounded XML in `composeResources/drawable/`. Sample → `Sample`,
  Dataset → `Dataset`, Project → `Project`, Instrument → `Instrument`, everywhere, no exceptions.
  Tokens with filled variants take `AppIcon(icon, filled = true)`.
- **Chevrons**: expand in place → `ExpandChevron` (`AppAnimations.kt`); navigate elsewhere →
  `AppIcons.NavigateNext`. Don't mix the two.
- **Motion**: all specs in `ui/common/AppAnimations.kt`, never an inline tween. Spatial springs for
  movement/rotation/size, effects springs for opacity/color. Navigation transitions keep tween specs
  (`NavEnterDuration = 300`, `NavExitDuration = 200`).
- **Elevation**: `AppElevation.Level0`-`Level5` (0/1/3/6/8/12dp), never an inline `Xdp`.
  `Level4`/`Level5` are hover/focus/drag only, never a resting value.
- **Shapes**: always `MaterialTheme.shapes.X`, never a hardcoded `RoundedCornerShape(N.dp)`.
- **Typography**: stock M3 ramp only, declared in `ui/theme/Type.kt`. No `fontSize`/`fontWeight`
  outside that file.
- **No adaptive/window-size-class layout** - phones only, a deliberate scope decision.
- **Theming** (`ui/theme/`): 12 named accents in `accents/`, each a hand-curated static `ColorScheme`
  from the Material Theme Builder (3 contrast variants × 2 modes = 6 schemes per accent, no runtime
  generation). `resolveAccentColorScheme()` is a plain lookup. Accent and contrast are picked
  independently in Settings → Appearance (`AppPreferences.accentColor`/`accentContrast`). Dynamic
  color (Android 12+) derives its own scheme from the wallpaper and takes priority when enabled.
- **M3 dependency**: CMP 1.10.x resolves `material3:1.4.0` naturally. Do not add a
  `resolutionStrategy { force(...) }` block.

## iOS

`compileKotlinIosArm64` runs on Linux and verifies Kotlin correctness but cannot produce a runnable
app; that needs macOS + Xcode. Full setup in `dev/platform-parity.md`.

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
  icon *contrast* is not automatic. `MainActivity.kt` sets `isAppearanceLightStatusBars`/
  `isAppearanceLightNavigationBars` from the **resolved** `darkTheme`, not raw system dark mode, so
  they follow the in-app Light/Dark/System preference.
- Compose lifecycle traps (`remember` vs `rememberSaveable`, `getPlatformContext()` being
  `@Composable`, expect/actual defaults) - see `dev/architecture.md`'s "Common gotchas".
