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
| `dev/architecture.md` | Full stack table, package-by-package layout, data models, full API endpoint list, caching layers (`CrucibleRepository`/`ObservableCache`), ViewModels, pull-to-refresh pattern, navigation routes, Koin DI details, common gotchas, known gaps | Add/rename a package, add an API endpoint, change caching behavior, add a ViewModel, change DI wiring |
| `dev/style.md` | Compose `@OptIn` conventions, spacing/layout values, row/card and typography styles, elevation levels, `AnimatedVisibility` list-item pattern, "no comments" rule | Establish or change a UI styling convention that should apply project-wide |
| `dev/platform-parity.md` | What's shared vs. Android/iOS-only, known iOS gaps, iOS build/Xcode setup instructions | Add a platform-specific feature, close an iOS gap, change the iOS build process |
| `dev/icons.md` | Material Symbols download manifest — exact icon names/fill variants per `AppIcons` token | Add a new icon token |

Multi-step workflows live in `.claude/skills/` and load on demand rather than sitting in this file:

| Skill | Use it to... |
|---|---|
| `/release` | Cut a tagged release — version bump, changelog promotion, signed build, tag, push. **Never do a release by hand**; a past one silently shipped unsigned, and the skill's ordering exists to catch that. |
| `add-screen` | Add a screen: route + `encodeRouteSegment`, NavGraph entry, ViewModel, Koin registration, scaffold |
| `add-api-endpoint` | Add/change an API call: `safeCall`/`ApiResult`, pagination helper, `ObservableCache` wiring, metadata routes |
| `/audit-docs` | Periodically re-verify this file and `dev/*.md` against the code |

## Build

```bash
# Compile check
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"

# Debug APK (arm64-v8a only, signed with debug key, installable)
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :androidApp:assembleDebug
```

Expected build output: `BUILD SUCCESSFUL` with no warnings.

The compile check no longer needs to be remembered before a commit: `.claude/hooks/compile-check.sh`
runs it on every `git commit` issued through the Bash tool and blocks the commit on failure. It adds
~0.5s when Gradle is up to date and nothing at all to non-commit commands. Commits made by hand in
another terminal are not gated.

For ad-hoc debug APKs and anything release-related, use the `/release` skill — it covers artifact naming and the Drive folder convention.

## Changelog discipline

Add a `CHANGELOG.md` entry under `## [Unreleased]` **as part of the same change** that makes something user-visible — not just when preparing a release. If `## [Unreleased]` doesn't exist yet, add it at the very top of the file, above the latest version section. Entries are not backfilled from git history at release time; if it's not written when the change lands, it's easy to lose.

**Changelog style**: one short line per entry — aim for under ~12 words, hard cap one sentence. State *what* changed for the user, never *why* or *how*: no root cause, no file/class/hex-color/package-name specifics, no parenthetical asides. That detail belongs in the commit message, not here. Group under `### Added`/`### Changed`/`### Fixed` (only the sections that apply). Purely internal refactors with no user-visible effect don't get an entry — but a bug fix that fell out of one does.

Bad: "A debug build could fail to install (\"App not installed\", no explanation) over an existing release install, since both shared the same package ID with different signing keys — debug builds now use a distinct package ID (`gov.lbl.crucible.debug`), name (\"Crucible Lens (Dev)\"), and launcher icon color so they're both installable alongside a release build."
Good: "Fixed debug builds failing to install over a release build."

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

## Version management

Single source of truth: `gradle.properties` (`app.versionName`, `app.versionCode`). Both `composeApp` and `androidApp` read from these — never hardcode a version in a build file, and never bump one outside the `/release` skill.

## Key architecture decisions

- **Koin DI** — `ApiClient`, `CrucibleRepository`, `DataSyncManager` are `single`s; ViewModels use `viewModelOf(::MyViewModel)`, all in `di/AppModule.kt`. Screens obtain them via `koinInject<T>()` / `koinViewModel()`. A missing registration compiles fine and throws at runtime. Platform-module pattern and the leaf-composable exceptions (`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`) are in `dev/architecture.md`.
- **ViewModels** — every feature screen that loads data has one. Loading lives in `viewModelScope`, never in a composable. Screens that still lack a ViewModel use `remember`-based state.
- **`LoadState<T>`** (`ui/common/LoadState.kt`) — `Loading` / `Error(message)` / `Success(data, isRefreshing, fromCache)`, replacing the old five-variable pattern. `isRefreshingNow` is the pull-to-refresh flag.
- **`NavGraph`** takes 5 parameters: `navController`, `deepLinkUuid`, `openScanner`, `onScannerOpened`, `viewModel`. `AppPreferences` is *not* a parameter — it's obtained internally via `koinInject<AppPreferences>()`, its flows collected with `collectAsStateWithLifecycle`, and `prefs.saveXxx()` called directly inside NavGraph.
- **`ResourceDetailScreen`** takes a `uuid: String`, not a resource object — it and every pager page observe `CrucibleRepository.observeResource(uuid)` / `.observeThumbnails(uuid)` directly. No resource-object-keyed Compose state anywhere in this screen; "enriched" is derived from `resource?.links != null`, not tracked separately.
- **`CrucibleRepository`** is the single source of truth for all in-memory caching, via `ObservableCache<K, V>`. There is no separate legacy cache. Breakdown in `dev/architecture.md`'s "Caching layers".
- **`userProfile`** — a JSON-serialized `User` in DataStore under key `user_profile`. `userProfile?.uniqueId` is the source of truth for ORCID.
- **`ApiResult<T>`** wraps all API calls via `safeCall { }`. Always branch `is ApiResult.Success` / `is ApiResult.Error`.

## API

Full endpoint list in `dev/architecture.md`; procedure in the `add-api-endpoint` skill. The rules that must hold even if that skill never loads:

- Auth header is `Authorization: Bearer {apiKey}` on every request — not `Api-Key` or `Token`.
- **`scientific_metadata` is NOT accepted in `SampleUpdateRequest`, `DatasetUpdateRequest`, `SampleCreateRequest`, or `DatasetCreateRequest`** — it is silently dropped. It only moves via `POST` / `PATCH /resources/{id}/metadata`, so create and edit flows always make two calls. Both metadata calls return `ApiResult<JsonObject>`, never `ApiResult<Unit>`. See the `add-api-endpoint` skill for the `diffMetadataWrite` PATCH-vs-POST rules.
- **Join requests** (`requestToJoinProject`, `getJoinRequests`, `reviewJoinRequest`, `getMyJoinRequests`) are called directly from the owning ViewModel via `apiClient.service.*` — no repository wrapper, deliberately, since there's nothing to cache.
- `GET /projects/search` and `GET /projects/{proj_id}` are readable by any authenticated user, not just members — `lead`/`scientific_metadata` populate only for members/admins. That's what makes discover-search and non-member browsing work.
- **Pagination**: `fetchAllPagesCursor` (datasets/samples, keyset) or `fetchAllPages` (offset-based). Search endpoints return a flat list.

## User identity conventions

- Display format for someone already "in context" (owner rows, project leads, member/requester lists): full name via `userDisplayName()` (`data/util/FormatUtils.kt`) — no username by default. Falls back to `@username`, then the raw ORCID. Every such row is one tap from the full profile, so there's no need to spend space on the username up front.
- Exception — `UserProfileScreen`'s own header stays username-primary (`@username` as the page title). Search-result rows (`UserResultItem`, `UserPickerItemContent`) are name-first like everywhere else; the server search matches first/last name too, so username is no longer the disambiguator. `@username` still shows, smaller, below the name.
- `User` has: `firstName`, `lastName`, `email`, `uniqueId` (ORCID), `username`, `isServiceAccount`.
- Shared components in `ui/common/UserComponents.kt`: `UserAvatar`, `UserIdentityRow`, `UserResultItem`, `UserPickerItemContent`. `UserIdentityRow` is the default for a "name + avatar, tap for profile" row; use `userDisplayName()` directly when a custom layout rules it out. See `dev/style.md`'s "Search & pick patterns" for the `SearchPickerField`/`SearchPickerSheet` the search rows plug into.
- `UserAvatar`'s background color derives deterministically from `orcid` (pass `user.uniqueId`) — see `dev/style.md`'s "Avatar colors". Always pass it explicitly; omitting it silently falls back to a static color.
- Owner rows in detail cards navigate to `UserProfileScreen`, not to orcid.org.

## Icon conventions

All icons use `AppIcon(AppIcons.X)` — never `Icon(Icons.Default.*)`. `AppIcons` is in `ui/common/AppIcons.kt`; the drawables are Material Symbols Rounded XML in `commonMain/composeResources/drawable/ic_*.xml`.

- **Sample**: `AppIcons.Sample` · **Dataset**: `AppIcons.Dataset` · **Project**: `AppIcons.Project`
- **Instrument**: `AppIcons.Instrument` — everywhere, no exceptions. Static filled glyph (`ic_biotech.xml`, matching `Project`'s visual weight), not a stateful toggle like `Pinned`.
- **Expand in-place**: the `ExpandChevron` composable from `AppAnimations.kt` (collapsed = -90°, expanded = 0°)
- **Navigate elsewhere**: `AppIcons.NavigateNext` — distinct from the in-place expand chevron; don't mix the two
- **Username**: `AppIcons.Username` · **Session**: `AppIcons.Tag`

Tokens with filled variants (e.g. `AppIcons.Pinned`) support `AppIcon(icon, filled = true)`.

## Motion / Animation

All specs live in `ui/common/AppAnimations.kt` — never inline a tween in UI code.

- **Spatial springs** (movement, rotation, size): `SpatialDefaultSpring`, `SpatialFastSpring`, `SpatialDefaultSizeSpring`, `SpatialFastSizeSpring`
- **Effects springs** (opacity, color): `EffectsDefaultSpring`, `EffectsFastSpring`
- **Backward-compat aliases**: `StandardAnim`, `FastAnim`, `StandardSizeAnim`, `FastSizeAnim` — all spring-based
- **Nav timing**: `NavEnterDuration = 300`, `NavExitDuration = 200`. Navigation screen transitions keep tween-based specs.
- **`ExpandChevron`**: the single composable for every expand/collapse chevron — `fast = true` for nested elements

## Elevation

All levels live in `ui/common/AppElevation.kt` (`Level0`–`Level5` = 0/1/3/6/8/12dp, M3's 6 canonical values) — never inline a one-off `Xdp` on `tonalElevation`/`shadowElevation`/`CardDefaults.cardElevation()`/`FloatingActionButtonDefaults.elevation()`. `Level4`/`Level5` are hover/focus/drag-only per M3 spec — don't use them as a resting value. See `dev/style.md`'s Elevation section for the full component → level mapping and the tonal-elevation-is-a-no-op-once-you-set-`color` gotcha.

## Theming

- **`ui/theme/Theme.kt`** — `CrucibleScannerTheme`, passing `colorScheme`, `typography`, and `shapes` to `MaterialTheme`.
- **`ui/theme/Type.kt`** — all 15 M3 type roles declared explicitly, matching M3 1.4.0's `TypographyTokens` defaults exactly — zero deviations from the stock ramp.
- **`ui/theme/Shape.kt`** — full M3 shape scale declared explicitly (4/8/12/16/28dp). Always reference `MaterialTheme.shapes.X`, never a hardcoded `RoundedCornerShape(N.dp)`.
- **Accent colors**: 12 named accents (`ui/theme/accents/` — `Carmine`/`Cerulean`/`Emerald`/`Evergreen`/`Flamingo`/`Midnight`/`Mimosa`/`Mocha`/`Onyx`/`Plum`/`Pumpkin`/`Vermillon`), each a fully hand-curated, static `ColorScheme` exported from the Material Theme Builder (material-foundation.github.io/material-theme-builder) — every role explicitly assigned, no runtime color generation, no custom-hex input. Each accent ships 3 contrast variants (Standard/Medium/High, matching M3's real contrast-level spec) × 2 theme modes = 6 static schemes; the user picks accent + contrast independently in Settings → Appearance, persisted as `AppPreferences.accentColor`/`accentContrast`. `Theme.kt`'s `resolveAccentColorScheme()` is a plain lookup, not a computation. Dynamic color (Android 12+/API 31+) takes priority when enabled and is unrelated to this system — it already derives its own scheme from the wallpaper.
- **No adaptive/window-size-class layout** — phones only. A deliberate scope decision, not an oversight.

## M3 dependency

No version force needed. CMP 1.10.x naturally resolves to `androidx.compose.material3:material3:1.4.0`. Do not add a `resolutionStrategy { force(...) }` block.

## iOS

`compileKotlinIosArm64` runs on Linux and verifies Kotlin correctness but cannot produce a runnable app — that needs macOS + Xcode. Full setup: `dev/platform-parity.md`.

## Things that have bitten us before

- `sed` on multiline Kotlin produces literal `\n` — use Python for multiline string replacement
- `kotlinx.serialization`: all `@SerialName` annotations must be explicit on multi-word field names AND single-word fields that need to be consistent (e.g., `@SerialName("email")`)
- Migrated from `com.android.library` to `com.android.kotlin.multiplatform.library` — Android resources/manifest now live in `src/androidMain/` (not `src/main/`). Build config constants generated via `generateAppBuildConfig` Gradle task → `AppBuildConfig.kt` in `androidMain`. Always use `:androidApp:assembleDebug` for the installable app.
- `collectAsState` vs `collectAsStateWithLifecycle` — prefer `collectAsStateWithLifecycle` in NavGraph for lifecycle awareness
- Scientific metadata (`scientific_metadata`) is NOT accepted in `SampleUpdateRequest`, `DatasetUpdateRequest`, `SampleCreateRequest`, or `DatasetCreateRequest`. Always use `POST /resources/{id}/metadata` or `PATCH /resources/{id}/metadata`.
- `SearchBar` in `SearchScreen` uses the deprecated `expanded/onExpandedChange` API (the new `SearchBarState` API requires M3 1.5.0+ which is still alpha). This is intentional — migrate when M3 1.5.0 stabilises.
- Swipe-to-hide lives in one shared `ui/common/SwipeToHideItem.kt` (used by `ProjectsListScreen`/`InstrumentListScreen`): bare `rememberSwipeToDismissBoxState()` plus `SwipeToDismissBox`'s `onDismiss` callback — don't reintroduce the deprecated `confirmValueChange` overload. Re-arming a dismissed item is done by changing the caller's `key()`, never by resetting the state (see that file's KDoc).
- The only `@Suppress("DEPRECATION")` in `ui/` is `DateTimePickerField.kt` (kotlinx-datetime's `monthNumber`/`dayOfMonth`).
- Targeting SDK 35+ means the OS enforces edge-to-edge unconditionally, but status/nav bar icon *contrast* isn't automatic — `MainActivity.kt` sets `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` from the resolved `darkTheme` (not raw system dark mode) so they stay legible and follow the in-app Light/Dark/System preference, not just the OS setting.
