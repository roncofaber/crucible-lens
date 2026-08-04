# Architecture Notes

Branch: `main`

## Contents

- [Stack](#stack)
- [Source set layout](#source-set-layout)
- [Data models](#data-models-datamodelcrucibleresourcekt)
- [API](#api-dataapi)
- [Caching layers](#caching-layers)
- [ViewModels](#viewmodels)
- [Pull-to-refresh pattern](#pull-to-refresh-pattern)
- [Navigation](#navigation-navgraphkt)
- [ResourceDetailScreen pager](#resourcedetailscreen-pager)
- [iOS entry point](#ios-entry-point)
- [Shared utilities](#shared-utilities-datautil)
- [Preferences](#preferences-datapreferencesapppreferenceskt)
- [Dependency injection (Koin)](#dependency-injection-koin)
- [Common gotchas](#common-gotchas)
- [Known gaps](#known-gaps)

---

## Stack

| Concern | Library |
|---|---|
| Language | Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP) 1.10.3 |
| UI | Jetpack Compose / Material 3 — identical on Android and iOS |
| Networking | Ktor 3.0.3 (OkHttp engine on Android, Darwin engine on iOS) |
| Serialization | kotlinx.serialization 1.7.3 |
| Preferences | DataStore Preferences (Android) / NSUserDefaults via multiplatform-settings (iOS), behind the shared `AppPreferences` interface |
| Date/time | kotlinx-datetime 0.7.1 |
| QR scan + display | easyqrscan (scanning) + qr-kit (display/generation) |
| Image picking | Native `UIImagePickerController`/`PHPickerViewController` (iOS); `ActivityResultContracts` + CameraX (Android) — no third-party picker library |
| WebView (ORCID) | compose-webview-multiplatform 2.0.3 |
| Navigation | org.jetbrains.androidx.navigation 2.9.2 |
| Dependency injection | Koin 4.2.0 — see "Dependency injection (Koin)" below |
| Min SDK | 26 (Android) |
| Target/Compile SDK | 36 (Android) |
| Kotlin | 2.3.21 |
| AGP | 9.2.1 |
| Gradle | 9.5.1 |
| Version catalog | `gradle/libs.versions.toml` — all dependency versions declared once, referenced via `libs.*` |

---

## Source set layout

```
app/src/
├── commonMain/        Shared code — all UI screens, API, models, cache, navigation, DI modules
├── androidMain/       Android actuals (MainActivity, PreferencesManager, camera/QR platform code)
└── iosMain/           iOS actuals (App.kt entry point, IosAppPreferences, native pickers)
androidApp/            Thin Android application shell (signing, ProGuard, manifest) — depends on app/
iosApp/                Xcode project (via XcodeGen) + Swift entry point — depends on app/
```

Migrated from `com.android.library` + `src/main/` to `com.android.kotlin.multiplatform.library` +
`src/androidMain/` — there is no `src/main/` anymore. See "Things that have bitten us before" in
`CLAUDE.md` for details.

`ProjectDetailScreen` is a layout + state container; the sample and dataset list rendering logic
(grouping, sorting, pagination) lives in `ProjectResourceLists.kt` — `SamplesList`, `DatasetsList`,
and the shared `groupedResourceItems` function. This separation keeps list complexity out of the
screen composable and makes the shared patterns reusable.

### Package layout (commonMain)

```
crucible.lens
├── data
│   ├── api/          CrucibleApiService (Ktor), ApiClient, ApiResult sealed class
│   ├── cache/        ObservableCache (generic in-memory TTL cache), PersistentProjectCache
│   ├── model/        CrucibleResource.kt — all data classes
│   ├── network/      ConnectivityObserver (expect/actual)
│   ├── preferences/  AppPreferences interface, PreferencesFactory (expect/actual)
│   ├── repository/   CrucibleRepository — single point of contact for resource/project/instrument fetches
│   ├── sync/         DataSyncManager — background cache preload via CrucibleRepository
│   └── util/         SearchExtensions, DateTimeUtils, SortUtils, FormatUtils, CryptoUtils,
│                     DuplicateHolder, SearchPickerConstants
├── di/               AppModule (Koin module), KoinInit (initKoin())
└── ui
    ├── common/       LoadState, AppTopBar (+ CollapsingAppTopBar), AppIcons, AppAnimations,
    │                 ResourceCard, ResourceListComponents (ResourceRow, ListRowDividerInset,
    │                 ResourceControlsBar, EmptyListCard), SectionHeader, SearchPicker,
    │                 SwipeToHideItem, NotificationDot, UserComponents, MetadataEditor,
    │                 SaveableStateMap, QrCodeDialog, LazyColumnScrollbar, …
    ├── create/       CreateSampleScreen, CreateDatasetScreen, CreateEditViewModels, AddFilesScreen
    ├── detail/       ResourceDetailScreen, ResourceDetailViewModel, EditResourceScreen, LinkResourceSheet
    ├── history/      HistoryScreen
    ├── home/         HomeScreen, HomeViewModel
    ├── instruments/  InstrumentListScreen/ViewModel, InstrumentDetailScreen/ViewModel, ManageInstrumentScreen/ViewModel
    ├── metadata/     MetadataEditorScreen, MetadataHolder
    ├── navigation/   NavGraph, Screen sealed class
    ├── projects/     ProjectsListScreen/ViewModel, ProjectDetailScreen/ViewModel, ManageProjectScreen/ViewModel,
    │                 ProjectResourceLists (SamplesList, DatasetsList, groupedResourceItems)
    ├── scanner/      QRScannerPlatform (QRCodeScannerView via easyqrscan)
    ├── search/       SearchScreen
    ├── settings/     SettingsScreen, ApiSettingsScreen, AppearanceSettingsScreen, CacheSettingsScreen,
    │                 AboutSettingsScreen, AccountScreen/ViewModel, UserProfileScreen, OrcidLoginScreen
    └── theme/        Theme.kt (CrucibleScannerTheme), Type.kt (Typography), Shape.kt (Shapes)
```

---

## Data models (`data/model/CrucibleResource.kt`)

All JSON models use `@Serializable` + `@SerialName("snake_case")` (kotlinx.serialization — no reflection, no codegen).

### Key types

- `CrucibleResource` — sealed base: `uniqueId`, `name`, `description`, `keywords`
- `Sample` / `Dataset` — both extend `CrucibleResource`; `name` is a computed property
  (`sampleName ?: uniqueId`, `datasetName ?: uniqueId`) so a null API name never crashes
- `ResourceLink` — `{unique_id, resource_type, name?, relationship}` where `relationship`
  is `"parent" | "child" | "associated"` (matches API's Literal type)
- `Instrument`, `Project`, `UserLead`, `AccountResponse`, `MetadataSearchResult`
- `ResourceSearchResult` — the unified row type both search modes produce. Its `projectId` is
  **not** returned by `/resources/metadata/search`; it's populated client-side in name-search mode,
  where `searchSamples`/`searchDatasets` already return full `Sample`/`Dataset` objects carrying it.
  So metadata-mode results have a null `projectId` and the row falls back to showing the mfid —
  an asymmetry to preserve rather than paper over with per-result lookups.
- Request DTOs: `SampleCreateRequest`, `DatasetCreateRequest`, `ThumbnailCreateRequest`,
  `SampleUpdateRequest`, `DatasetUpdateRequest`

JSON decoder uses `ignoreUnknownKeys = true` + `isLenient = true` to tolerate API additions.

---

## API (`data/api/`)

Ktor-based `CrucibleApiService`. Base URL and API key are user-configurable (stored in preferences).
Auth header: `Authorization: Bearer <api_key>`.

All responses are wrapped in `ApiResult<T>`:
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}
```

Key endpoints:
- `GET /samples/{uuid}?include_links=true` — full sample with relationships
- `GET /datasets/{uuid}?include_links=true&include_metadata=true` — dataset + scientific metadata inline
- `GET/POST/PATCH /resources/{unique_id}/metadata` — generic across sample/dataset/instrument, require write access (not just read); all return `{unique_id, scientific_metadata}`, unwrapped to a bare `JsonObject` by `CrucibleApiService`. `POST` creates/replaces (409 if non-empty metadata already exists, unless `?overwrite=true`); `PATCH` shallow-merges `{**existing, **updates}` — new keys added, existing keys overwritten, nested dict values replaced wholesale (not deep-merged), and never 409s. See `CLAUDE.md`'s API rules for how the app decides POST vs PATCH on edit.
- `GET /projects`, `GET /projects/{id}/users` — `GET /projects` (unlike `/projects/search`, below) only ever returns projects the caller is a member of
- `DELETE /projects/{id}/users/{orcid}` — admin, the project lead, or the member themselves (self-removal); the lead cannot remove themselves (409) — must transfer leadership first (`PATCH /projects/{id}` with a new `project_lead_username`)
- `GET /projects/search`, `GET /projects/{id}` — readable by any authenticated user, not just members. `lead` is full `UserRead` (with email) and `scientific_metadata` populated only for members/admins; non-members get `lead` as `UserPublicRead` (no email) and `scientific_metadata` always null, regardless of `?include_metadata=`. This is what makes discover-search (`SearchScreen`'s "Discover" chip) and the non-member view in `ProjectDetailScreen` possible.
- `GET /instruments`, `GET /instruments/{id}`
- `GET /datasets?instrument_name=X&limit=N` — datasets by instrument
- `GET /idtype/{uuid}` — resolve resource type before fetching
- `POST /deletion_requests` — soft-delete request
- `POST /access_groups/{group_name}/join` — request to join a project (`group_name` is always a `project_id` for now); 409 if already a member or already has a pending request
- `GET /join_requests?group_name=&status=&requester_id=` — list join requests. Passing `group_name` requires being that project's lead or an admin (403 otherwise). Omitting `group_name`: admins see everything; a non-admin lead gets auto-scoped to every project they lead in one call (returns an empty list, not a 403, if they lead nothing) — this auto-scoping is what makes the bulk pending-count preload below possible with a single request
- `PATCH /join_requests/{request_id}` — approve/reject a pending request; admin or the request's project lead only; approval adds the requester as a project member server-side
- `GET /account/join_requests?status=` — the caller's own join-request history across all projects

Join-request calls (`requestToJoinProject`, `reviewJoinRequest`, `getMyJoinRequests`) are one-shot mutations/lookups called directly from the owning ViewModel/screen via `apiClient.service.*` — no `CrucibleRepository` wrapper, since there's no caching need shared across them. The exception is `getJoinRequests`, whose *pending count per project* is cached (see "Caching layers" above) to drive the lead-facing pending-request dot indicator on Home's pinned cards, Projects list, and `ProjectDetailScreen`'s overflow/Manage-project icons — that one goes through `CrucibleRepository.fetchPendingJoinRequestCounts()`, not a direct `apiClient` call, since the same counts need to be read from three different screens. The indicator shows the actual pending count, sized down to `12.dp` (see `NotificationDot`) — the exact number was previously one tap away in Manage Project, now visible at a glance.

`DataSyncManager.syncAll()` fetches all pending counts in a single `getJoinRequests(status = "pending")` call (no `group_name`), then buckets the results client-side by `groupName` against the caller's own led-project list (`projectLeadOrcid == currentUserOrcid`) — one request regardless of how many projects the user leads. Projects with zero pending requests are written as `0` (not left absent) so a resolved request clears its badge on the next sync.

`syncAll()` only runs once per session (app start) plus a resume after an interrupted resource refresh — it is deliberately too heavyweight (forces the whole projects/instruments/sample/dataset preload) to call from a single pull-to-refresh. `ProjectsListScreen` and `ProjectDetailScreen` instead call `CrucibleRepository.fetchPendingJoinRequestCounts()` directly from their own pull-to-refresh/menu-refresh/retry actions (scoped to the refreshed project list's led projects, or just `projectId` on the detail screen) so the badge doesn't go stale for a whole session if a request arrives mid-session — the underlying API call has no `group_name` filter, so it always returns every led project's pending requests regardless of what's passed in, making this refresh as cheap either way.

---

## Caching layers

`CrucibleRepository` (`data/repository/CrucibleRepository.kt`) is the primary cache — every read that's worth caching goes through it, backed by one `ObservableCache<K, V>` per data type (in-memory, 10-min TTL, LRU eviction), each exposing `observeX()` (reactive `Flow`), `fetchX(forceRefresh)` (cache-first network fetch), and `getCachedX()` (synchronous read):

```
CrucibleRepository
  ├── resourceObservableCache    ObservableCache<uuid, CrucibleResource>
  ├── resourceTypeObservableCache ObservableCache<uuid, String>             — "sample"/"dataset", for
  │                                                                          screens that only have a UUID
  ├── thumbnailObservableCache   ObservableCache<uuid, List<Thumbnail>>
  ├── projectsObservableCache    ObservableCache<Unit, List<Project>>       — member projects list
  ├── projectObservableCache     ObservableCache<projectId, Project>        — per-project, incl. non-member
  │                                                                          projects reached via discover-search
  ├── projectMembersObservableCache ObservableCache<projectId, List<User>>  — fetched alongside the
  │                                  project itself (ProjectDetailScreen's load effect); shared by the
  │                                  collapsing header's member count and rememberOwnerNames's owner-groupby
  │                                  resolution, so GET /projects/{id}/users only ever runs once per project
  ├── instrumentsObservableCache ObservableCache<Unit, List<Instrument>>
  ├── instrumentDatasetsObservableCache ObservableCache<instrumentName, List<Dataset>>
  ├── projectSamplesObservableCache   ObservableCache<projectId, List<Sample>>
  ├── projectDatasetsObservableCache  ObservableCache<projectId, List<Dataset>>
  ├── pendingJoinRequestCountObservableCache  ObservableCache<projectId, Int> — only ever populated
  │                                            for projects the caller leads (see below)
  └── datasetFilesObservableCache ObservableCache<datasetUuid, List<AssociatedFile>>

PersistentProjectCache  (disk, 24h TTL)   — project summary lists only, needs a PlatformContext so it
                                             stays outside CrucibleRepository; HomeViewModel reads it on
                                             cold start and calls repository.seedProjects() to warm the
                                             in-memory cache from it (HomeScreen supplies the
                                             PlatformContext, obtained via the @Composable-only
                                             getPlatformContext(), since the ViewModel itself can't call it)
```

`CrucibleRepository.fetchFileUrl(mfid)` — the one exception to "cache everything": signed download URLs are deliberately **not** cached and always fetched fresh. It's only ever called on-demand from a share/download tap, never from a background preload, so there's no repeated-read case a cache would help with — and reusing a stale-but-not-yet-expired signed URL has no upside over asking again.

`CrucibleRepository.invalidateAll()` clears every `ObservableCache` field above in one call — used on logout, API key change, and the Cache settings screen's "Clear All Cache" button (which also clears `PersistentProjectCache` separately, since that's a different tier this method doesn't own). `CrucibleRepository.getCacheStats()` returns a snapshot (project/instrument/resource/sample/dataset/dataset-file counts) for that same screen.

There used to be a second, independent in-memory cache (`CacheManager`) that a handful of screens read/wrote directly instead of going through `CrucibleRepository` — this was a real bug source (two caches for the same data, neither aware of the other) and has been fully merged into `CrucibleRepository`; `CacheManager` no longer exists.

---

## ViewModels

Every list/detail/manage/create screen has its own `ViewModel` (commonMain, platform-agnostic),
constructor-injected via Koin (see "Dependency injection (Koin)" below): `ResourceDetailViewModel`,
`ProjectsListViewModel`, `ProjectDetailViewModel`, `ManageProjectViewModel`, `InstrumentListViewModel`,
`InstrumentDetailViewModel`, `ManageInstrumentViewModel`, `AccountViewModel`, `CreateSampleViewModel`,
`CreateDatasetViewModel`, `EditResourceViewModel`, `HomeViewModel`.

Most ViewModels expose a single `StateFlow<LoadState<T>>` (`ui/common/LoadState.kt`) rather than
separate loading/error/data/refreshing flags — see `CLAUDE.md`'s "Key architecture decisions".
`HomeViewModel` predates that convention's application to this screen and instead exposes three
separate flows (`projects`, `fetchError`, `isPreloading`) plus a background `preload()` step with
its own failure-tolerant batching (stops after 5 consecutive project fetch failures) — this mirrors
the screen's three genuinely independent concerns (the project list itself, a foreground fetch
error, and a background prefetch that fails silently by design) rather than forcing them into one
`LoadState`.

`ResourceDetailViewModel` is the exception and the most involved — it drives the resource detail pager:
- `uiState: StateFlow<UiState>` — `Idle | Loading | Success(uuid, isRefreshing) | Error(message)`.
  Note `Success` carries **only the uuid**, not the resource or its thumbnails: the screen and every
  pager page read those from `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)`
  directly, so there is no second copy of resource state to keep in sync.
- `isSyncing: StateFlow<Boolean>` — true while `DataSyncManager.syncAll()` is running (drives home screen spinner)
- `fetchResource(uuid)` — shows cached version immediately, always fetches fresh for full detail
- `refreshResource(uuid)` / `refreshThumbnails(uuid)` — force-refresh through the repository; observers pick up the fresh value
- `getCardState` / `setCardState` — persists expand/collapse state across pager pages (LRU-capped at `MAX_CARD_STATE_ENTRIES`)
- `startBackgroundSync()` / sync is paused during user-initiated refresh and resumed after
- `reset()` — clears state when leaving the detail screen

---

## Pull-to-refresh pattern

All screens use `PullToRefreshBox` (M3). Screens with a `LoadState`-based ViewModel read the
refresh flag straight off the state — `LoadState<T>.isRefreshingNow` (`this is Success &&
isRefreshing`) — rather than keeping a separate screen-local flag; `ProjectsListScreen`,
`InstrumentListScreen`, `ProjectDetailScreen`, and `InstrumentDetailScreen` all do this.

`ResourceDetailScreen` is the one screen with an extra local flag, `localRefreshState`: pulling
to refresh on a *sibling* page fetches that sibling inline through the repository without
involving the ViewModel (whose `isRefreshing` tracks only the navigated-to resource), so the
spinner for that case needs its own flag.

In both cases the flag is set `true` before the coroutine's work and cleared in a `finally`
block, so the spinner appears immediately on pull and always clears — including on error.

Content does **not** move during pull-to-refresh — the M3 `PullToRefreshBox` indicator overlays the content. This matches the standard Material 3 and iOS `UIRefreshControl` behavior.

---

## Navigation (`NavGraph.kt`)

`Screen` sealed class with `route` strings. Optional args use query params `?argName={argName}`.  
Special characters in route segments encoded via `encodeRouteSegment()`.

All 24 routes (see `Screen.kt` for the exact list): `Home`, `Scanner`, `Detail`, `EditResource`,
`History`, `Search`, `Projects`, `ProjectDetail`, `ManageProject`, `Instruments`, `InstrumentDetail`,
`ManageInstrument`, `Settings`, `SettingsApi`, `SettingsAppearance`, `SettingsCache`, `SettingsAbout`,
`SettingsAccount`, `OrcidLogin`, `CreateSample`, `CreateDataset`, `AddFiles`, `MetadataEditor`, `UserProfile`

---

## ResourceDetailScreen pager

Siblings are all samples (or datasets) of the same type within the same project, drawn from the project cache (`sameTypeSamples` / `sameTypeDatasets` params).

- `pageCount = siblingList.size`, `initialPage = siblingIndex` — pager opens at the correct position immediately (a `LaunchedEffect` scroll only covers the cold-start case where the sibling list wasn't resolved yet). It is a plain bounded pager: no virtual `Int.MAX_VALUE` page count, no wrap-around.
- **No manual preload or eviction windows.** Each page is `key(pageUuid)`'d and self-contained: it observes `repository.observeResource(pageUuid)`/`.observeThumbnails(pageUuid)` and kicks off its own `LaunchedEffect(pageUuid) { repository.fetchResourceByUuid(pageUuid) }`. `HorizontalPager` decides which pages exist; `ObservableCache`'s TTL + LRU decides what's evicted. There are no `loadedResources`/`enrichedUuids`/`failedEnrichmentUuids` maps and no ±N distance math — earlier versions had all of that and it was the source of several stale/flashing-content bugs.
- A page renders its lightweight sibling-list stub immediately and swaps in the enriched resource in place once the fetch lands, so there is no per-page spinner or content flash. Only the page-local `enrichmentFailed` flag distinguishes a failed enrichment.
- Swiping is a pure UI gesture — the ViewModel is not updated; `UiState.Success.uuid` always stays the navigated-to resource.
- Pull-to-refresh on a sibling calls `fetchResourceByUuid(uuid, forceRefresh = true)` (not invalidate-then-fetch, so observers keep seeing the existing value until the fresh one lands) and that page's own observer picks it up — no reload-trigger counter.

---

## iOS entry point

`iosMain/App.kt` → `iosMain/MainViewController.kt` → `iosApp/ContentView.swift` → `iOSApp.swift`

`App.kt` mirrors `MainActivity` but using `IosAppPreferences` (NSUserDefaults via multiplatform-settings).
ConnectivityObserver uses NWPathMonitor on iOS (no context needed).

See `dev/platform-parity.md` for Xcode project setup instructions.

---

## Shared utilities (`data/util/`)

| File | Contents |
|---|---|
| `SearchExtensions.kt` | `matchesSearch()` for Sample, Dataset, Instrument, JsonObject, Project |
| `DateTimeUtils.kt` | `MONTH_NAMES`, `dateGroupKey(String?)` — ISO timestamp → "Mon YYYY" |
| `SortUtils.kt` | `SortField` enum, `SortState`, `List<T>.applySortState()` |
| `FormatUtils.kt` | File size / date formatting helpers |
| `CryptoUtils.kt` | `PlatformCrypto.sha256Hex()` (expect/actual) for upload dedup |
| `DuplicateHolder.kt` | In-memory clipboard for sample/dataset duplication flow |

`fetchProjectData(projectId)` (parallel sample+dataset fetch with a per-project mutex) used to live in
a standalone `ProjectFetcher.kt`; it is now a method on `CrucibleRepository` — see
"Dependency injection (Koin)" below.

---

## Preferences (`data/preferences/AppPreferences.kt`)

All app configuration is persisted in `AppPreferences` — a platform-agnostic interface with concrete implementations on Android (DataStore) and iOS (NSUserDefaults via multiplatform-settings). Key preferences:

| Preference | Type | Key | Notes |
|---|---|---|---|
| API key | `StateFlow<String?>` | `api_key` | |
| API base URL | `StateFlow<String>` | `api_base_url` | Defaults to `https://crucible.lbl.gov/api/v2/` |
| Graph Explorer URL | `StateFlow<String>` | `graph_explorer_url` | Defaults to `https://crucible.lbl.gov/explore/` |
| Theme mode | `StateFlow<String>` | `theme_mode` | `system` / `light` / `dark` |
| Accent colour | `StateFlow<String>` | `accent_color` | Named palette (blue, purple, green, etc.) or custom hex |
| Dynamic colour | `StateFlow<Boolean>` | `use_dynamic_color` | Android 12+ only; forced false on iOS |
| Last visited resource | `StateFlow<String?>` | `last_visited_resource` / `last_visited_resource_name` | |
| Floating scan button | `StateFlow<Boolean>` | `floating_scan_button` | |
| Pinned/hidden projects & instruments | `StateFlow<Set<String>>` | `pinned_projects` / `hidden_projects` / `pinned_instruments` / `hidden_instruments` | |
| User ORCID | `StateFlow<String?>` | `user_orcid` | |
| User profile | `StateFlow<User?>` | `user_profile` | JSON-serialized |
| Resource history | `StateFlow<List<HistoryItem>>` | `resource_history` | |
| Sample group-by | `StateFlow<String>` | `sample_group_by` | Default: `TYPE` — persists ProjectDetailScreen's Samples tab grouping choice |
| Dataset group-by | `StateFlow<String>` | `dataset_group_by` | Default: `MEASUREMENT` — persists ProjectDetailScreen's Datasets tab grouping choice |
| Instrument group-by | `StateFlow<String>` | `instrument_group_by` | Default: `MEASUREMENT` — persists InstrumentDetailScreen's grouping choice |
| Default project tab | `StateFlow<String>` | `default_project_tab` | `SAMPLES` / `DATASETS` |

---

## Dependency injection (Koin)

The app uses [Koin](https://insert-koin.io/) for dependency injection. `ApiClient` was converted from
a Kotlin `object` singleton to a plain class and is registered as a Koin `single` in `di/AppModule.kt`,
giving it the same effective app-lifetime-singleton behavior it had before, but as a
constructor-injectable dependency rather than a globally-reachable static.

- **`di/AppModule.kt`** — the shared module: `ApiClient`, `CrucibleRepository`,
  `DataSyncManager`, and every ViewModel are registered here via `single { ... }` / `viewModelOf(::X)`.
- **`di/KoinInit.kt`** — `initKoin(platformModule)` starts Koin with `appModule` plus a
  platform-supplied module. `AppPreferences` is *not* in `appModule` because Android's implementation
  needs a `PlatformContext` to construct — each platform's entry point provides it via its own module
  and calls `initKoin(...)` once, guarded by `KoinPlatformTools.defaultContext().getOrNull() == null`.
- **Entry points**: `MainActivity.onCreate()` (Android) and `App()` (iOS, `iosMain/App.kt`) both call
  `initKoin(...)` before rendering `NavGraph`.
- **In Compose**: ViewModels are obtained via `koinViewModel<T>()` (replaces the old
  `viewModel()`/`viewModel<T>()`/manual-factory calls). Non-ViewModel singletons are obtained via
  `koinInject<T>()`.
- **`CrucibleRepository`** (`data/repository/CrucibleRepository.kt`) is constructor-injected with just
  `ApiClient` and is the single point of contact for resource/project/instrument fetch-with-cache
  logic. Every ViewModel that fetches Crucible data is expected to go through it or, for one-shot
  mutations (create/update/delete) that don't share caching logic with anything else, to take
  `ApiClient` directly via constructor injection — both are acceptable; reaching for the global object
  instead of the constructor parameter is not.

**Leaf-composable exception (accepted, not a gap):** `InstrumentPickerField`, `FilterSheet`, and
`AssociatedFilesCard` call `koinInject<ApiClient>()` (`AssociatedFilesCard` uses `CrucibleRepository`
instead, for its cached file-list/download-URL reads) directly from within the composable rather than
through an owning ViewModel. This is intentional: each of these
components is reused from multiple, unrelated parent screens with no single owning ViewModel
(e.g. `InstrumentPickerField` appears in both `CreateDatasetScreen` and `EditResourceScreen`).
Introducing a per-use-site ViewModel, or threading callback props through every parent, would add
real wiring complexity for no benefit — `koinInject` still gives these components a real, swappable
dependency rather than a global static, which was the actual problem being solved. Do not "fix" this
by half-threading callbacks through call sites; if a genuine need for shared state across these
components arises, revisit then.

**Unused KSP plugin — removed.** `com.google.devtools.ksp` was applied in `app/build.gradle.kts` with
no `ksp(...)` dependency anywhere (leftover from an abandoned codegen experiment). It has been removed
from both `build.gradle.kts` and `gradle/libs.versions.toml`.

---

## Common gotchas

**Phantom gaps in LazyColumn**: `Arrangement.spacedBy(N.dp)` adds N dp between *every* slot
including zero-height `AnimatedVisibility` items. Fix: use `Arrangement.Top` and add
`padding(bottom = N.dp)` inside each item's visible content.

**expect/actual defaults**: default parameter values must be declared on the `expect` side only.
The `actual` implementation must not repeat them, or the compiler will reject it.

**`getPlatformContext()` is `@Composable`**: capture it at composable scope (`val ctx = getPlatformContext()`)
before passing to lambdas — it cannot be called inside `onClick`, `remember {}`, or `LaunchedEffect {}`.

**iOS targets disabled on Linux**: Kotlin/Native iOS targets cannot build on Linux.
Add `kotlin.native.ignoreDisabledTargets=true` to `gradle.properties` to suppress warnings.
Build for iOS on macOS only.

**API auth**: header is `Authorization: Bearer <key>` (FastAPI HTTPBearer scheme).
Not `Api-Key` or `Token`.

**Plain `remember` state doesn't survive navigating to another screen and back** — Navigation-Compose
only composes the top of the back stack, so pushing a new destination fully disposes the
composable underneath it; on `popBackStack()`, that composable recomposes from scratch and any
plain `remember`ed value silently resets to its initial default. This bit
`ProjectDetailScreen`'s per-group expand/pagination state, which reset after visiting a resource
detail screen and coming back — fixed via `rememberSaveable` + `stateMapSaver()`
(`ui/common/SaveableStateMap.kt`). Any screen-level state that must survive a push-and-pop round
trip — not just scroll position, which `rememberLazyListState()` already saves for free — needs
`rememberSaveable`, not `remember`.

An earlier, hand-rolled version of `ProjectDetailScreen`'s collapsing header hit this same class of
bug: its custom scroll-offset state reset to 0 on the way back from a resource detail screen while
the list's own scroll position stayed put, so the header rendered fully-expanded on top of an
already-scrolled list — worked around at the time by manually backing that state with
`rememberSaveable`. That whole hand-rolled mechanism (`CollapsingHeaderState`,
`Modifier.layout{}` height-shrinking, a custom `NestedScrollConnection`) was later replaced by
`CollapsingAppTopBar` driven by `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` (see
`dev/style.md`'s "Collapsing top bar" section for why it's a custom composable rather than
`MediumTopAppBar`) — which sidesteps this gotcha entirely, since `rememberTopAppBarState()` is
already `rememberSaveable` internally; nothing extra to wire up.

This same bug also explains why **`EditResourceScreen` is a full nav destination (`Screen.EditResource`),
not a bottom sheet** — it used to be `EditResourceSheet`, a `ModalBottomSheet` local to
`ResourceDetailScreen`, whose "Scientific metadata" section still had to navigate out to the real
`MetadataEditorScreen` destination and back. That mismatch (a screen-local sheet routing out to a
real screen) meant `ResourceDetailScreen`'s sheet-visibility flags got disposed on the way there and
never restored on the way back, so the sheet simply never reopened and the metadata edit was
silently discarded — patched at the time with `rememberSaveable` sheet flags plus a singleton
`EditDraftHolder` to ferry in-progress field values across the round trip, then fully resolved by
converting the sheet into `EditResourceScreen`, a real page that survives its own composition being
torn down and rebuilt via `rememberSaveable` alone (same pattern `CreateSampleScreen`/
`CreateDatasetScreen` already used correctly) — no singleton relay needed once it's a real screen.
Prefer a full nav destination over a bottom sheet for anything whose content itself needs to open
another nav destination.

---

## Known gaps

- iOS: no deep-link/URL-scheme handling, no launch screen configured — see `dev/platform-parity.md`. Not blocking; iOS distribution isn't active yet.
