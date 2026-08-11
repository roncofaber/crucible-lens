# Architecture Notes

Deep reference for structure, data flow, and caching. `CLAUDE.md` is the fast-reference layer;
UI conventions live in `dev/style.md`.

---

## Stack

| Concern | Library |
|---|---|
| Language / UI | Kotlin 2.3.21, Compose Multiplatform 1.10.3, Material 3 - identical on Android and iOS |
| Networking | Ktor 3.0.3 (OkHttp on Android, Darwin on iOS) |
| Serialization | kotlinx.serialization 1.7.3 |
| Preferences | DataStore (Android) / NSUserDefaults via multiplatform-settings (iOS), behind `AppPreferences` |
| Date/time | kotlinx-datetime 0.7.1 |
| QR | easyqrscan (scanning) + qr-kit (display) |
| Image picking | Native pickers on both platforms - no third-party library |
| WebView (ORCID) | compose-webview-multiplatform 2.0.3 |
| Navigation | org.jetbrains.androidx.navigation 2.9.2 |
| DI | Koin 4.2.0 |
| Build | AGP 9.2.1, Gradle 9.5.1, min SDK 26, target/compile SDK 36 |

All versions are declared once in `gradle/libs.versions.toml` and referenced via `libs.*`.

---

## Source set layout

```
app/src/
├── commonMain/        Shared code - all UI screens, API, models, cache, navigation, DI modules
├── androidMain/       Android actuals (MainActivity, PreferencesManager, camera/QR platform code)
└── iosMain/           iOS actuals (App.kt entry point, IosAppPreferences, native pickers)
androidApp/            Thin Android application shell (signing, ProGuard, manifest) - depends on app/
iosApp/                Xcode project (via XcodeGen) + Swift entry point - depends on app/
```

There is no `src/main/` - the module uses `com.android.kotlin.multiplatform.library`, so Android
resources and the manifest live in `src/androidMain/`.

### Package layout (commonMain)

```
crucible.lens
├── data
│   ├── api/          CrucibleApiService (Ktor), ApiClient, ApiResult sealed class
│   ├── cache/        ObservableCache (generic in-memory TTL cache), PersistentProjectCache
│   ├── model/        CrucibleResource.kt - all data classes
│   ├── network/      ConnectivityObserver (expect/actual)
│   ├── preferences/  AppPreferences interface, PreferencesFactory (expect/actual)
│   ├── repository/   CrucibleRepository - single point of contact for resource/project/instrument fetches
│   ├── sync/         DataSyncManager - background cache preload via CrucibleRepository
│   └── util/         SearchExtensions, DateTimeUtils, SortUtils, FormatUtils, CryptoUtils,
│                     DuplicateHolder, SearchPickerConstants
├── di/               AppModule (Koin module), KoinInit (initKoin())
└── ui
    ├── common/       LoadState, AppTopBar (+ CollapsingAppTopBar), AppIcons, AppAnimations,
    │                 ResourceCard, ResourceListComponents, SectionHeader, SearchPicker,
    │                 SwipeToHideItem, NotificationDot, UserComponents, MetadataEditor,
    │                 SaveableStateMap, QrCodeDialog, LazyColumnScrollbar, LongPressMenuBox, …
    ├── create/       CreateSampleScreen, CreateDatasetScreen, CreateEditViewModels, AddFilesScreen
    ├── detail/       ResourceDetailScreen, ResourceDetailViewModel, EditResourceScreen, LinkResourceSheet
    ├── history/      HistoryScreen
    ├── home/         HomeScreen, HomeViewModel
    ├── instruments/  InstrumentList/Detail/Manage Screen + ViewModel
    ├── metadata/     MetadataEditorScreen, MetadataHolder
    ├── navigation/   NavGraph, Screen sealed class
    ├── projects/     ProjectsList/ProjectDetail/ManageProject Screen + ViewModel,
    │                 ProjectResourceLists (SamplesList, DatasetsList, groupedResourceItems)
    ├── scanner/      QRScannerPlatform (QRCodeScannerView via easyqrscan)
    ├── search/       SearchScreen
    ├── settings/     Settings, Api, Appearance, Cache, About, Account, UserProfile, OrcidLogin
    └── theme/        Theme.kt, Type.kt, Shape.kt, accents/ (12 hand-curated ColorSchemes)
```

---

## Data models (`data/model/CrucibleResource.kt`)

All JSON models use `@Serializable` + `@SerialName("snake_case")`. The decoder sets
`ignoreUnknownKeys = true` + `isLenient = true` to tolerate API additions.

- **`CrucibleResource`** - sealed base: `uniqueId`, `name`, `description`, `keywords`.
- **`Sample` / `Dataset`** - both extend it; `name` is computed (`sampleName ?: uniqueId`) so a null
  API name never crashes.
- **`ResourceLink`** - `{unique_id, resource_type, name?, relationship}`, where `relationship` is
  `"parent" | "child" | "associated"` (matching the API's Literal type).
- **`ResourceSearchResult`** - the unified row type both search modes produce. Its `projectId` is
  **not** returned by `/resources/metadata/search`; it's populated client-side in name-search mode,
  where `searchSamples`/`searchDatasets` already return full objects carrying it. Metadata-mode
  results therefore have a null `projectId` and fall back to showing the mfid. Preserve that
  asymmetry rather than papering over it with per-result lookups.
- Also: `Instrument`, `Project`, `UserLead`, `AccountResponse`, `MetadataSearchResult`, and the
  request DTOs (`SampleCreateRequest`, `DatasetCreateRequest`, `ThumbnailCreateRequest`,
  `SampleUpdateRequest`, `DatasetUpdateRequest`).

---

## API (`data/api/`)

Ktor-based `CrucibleApiService`. Base URL and API key are user-configurable; auth header is
`Authorization: Bearer <api_key>` on every request.

Every response is wrapped - always branch on both arms:

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
}
```

### Endpoints

| Endpoint | Notes |
|---|---|
| `GET /resources/{uuid}` | Unified fetch - resolves type and returns the resource in one call |
| `GET /samples/{uuid}?include_links=true` | Full sample with relationships |
| `GET /datasets/{uuid}?include_links=true&include_metadata=true` | Dataset + scientific metadata inline |
| `GET /datasets?instrument_name=X&limit=N` | Datasets by instrument |
| `GET`/`POST`/`PATCH` `/resources/{id}/metadata` | See "Scientific metadata" below |
| `GET /projects` | Member projects only (unlike `/projects/search`) |
| `GET /projects/{id}`, `GET /projects/search` | Readable by any authenticated user - see "Access model" |
| `POST /projects` | Creates a project; see "Project creation" |
| `PATCH /projects/{id}` | Includes transferring leadership via `project_lead_username` |
| `GET /projects/{id}/users` | Member list |
| `DELETE /projects/{id}/users/{orcid}` | Admin, the lead, or the member themselves. The lead **cannot** remove themselves (409) - transfer leadership first |
| `GET /instruments`, `GET /instruments/{id}` | |
| `POST /deletion_requests` | Soft-delete request |
| `POST /access_groups/{group_name}/join` | Request to join (`group_name` is always a `project_id` today); 409 if already a member or already pending |
| `GET /join_requests` | Filters `group_name`/`status`/`requester_id` - see "Join requests" |
| `PATCH /join_requests/{request_id}` | Approve/reject; approval adds the member server-side |
| `GET /account/join_requests?status=` | The caller's own history across all projects |

**Pagination**: `fetchAllPages` for offset-based lists, `fetchAllPagesCursor` for keyset lists
(datasets, samples). Search endpoints return a flat list and need neither.

### Scientific metadata

`scientific_metadata` is **silently dropped** from `SampleUpdateRequest`, `DatasetUpdateRequest`,
`SampleCreateRequest`, and `DatasetCreateRequest`. It only moves via `POST`/`PATCH
/resources/{id}/metadata`, so create and edit flows always make two calls.

Both return `ApiResult<JsonObject>` (the resulting metadata), never `ApiResult<Unit>` - discarding
the payload once made a 409 or 500 from the metadata call completely invisible.

The routes are generic across sample/dataset/instrument and require **write** access, not just read.
`POST` creates or replaces (409 if non-empty metadata exists, unless `?overwrite=true`); `PATCH`
shallow-merges (new keys added, existing overwritten, nested dicts replaced wholesale - not
deep-merged) and never 409s. The `add-api-endpoint` skill has the PATCH-vs-POST decision rules.

### Access model

`GET /projects/search` and `GET /projects/{id}` are readable by **any** authenticated user, not just
members. Non-members get `lead` as `UserPublicRead` (no email) and a null `scientific_metadata`
regardless of `?include_metadata=`; members and admins get the full `UserRead` and the metadata. This
asymmetry is what makes discover-search and the non-member view in `ProjectDetailScreen` work - don't
"fix" a null `lead` by gating the endpoint.

### Project creation

`POST /projects` has **no server-side authorization check**: any authenticated user can create a
project naming any existing user as its lead. This is deliberate, for ad-hoc personal projects.

Exactly one of `project_lead_orcid`/`_email`/`_username` is required (this app only ever sends
`_username`, resolved through the same `SearchPickerField` user search as Manage Project's lead
field). `project_id` becomes the project's permanent handle and its access-group name; there is no
rename route. Errors: `400` no lead identifier, `404` lead username doesn't resolve, `409`
`project_id` taken. Unlike other resources, no `Resource`/`idtype` row is created, so
`creation_time`/`modification_time` stay null and a project can't carry scientific metadata.

### Join requests

`requestToJoinProject`, `reviewJoinRequest`, and `getMyJoinRequests` are one-shot calls with nothing
to share, so they go straight to `apiClient.service.*` from the owning ViewModel - deliberately no
repository wrapper. `getJoinRequests` is the exception: its *pending count per project* drives the
lead-facing dot on Home, the Projects list, and `ProjectDetailScreen`, so it goes through
`CrucibleRepository.fetchPendingJoinRequestCounts()`.

Authorization is what makes the bulk preload cheap. Passing `group_name` requires being that
project's lead or an admin (403 otherwise), but **omitting it auto-scopes a non-admin lead to every
project they lead in one call** (empty list, not 403, if they lead none). `DataSyncManager.syncAll()`
therefore issues one `getJoinRequests(status = "pending")` and buckets by `groupName` client-side,
writing `0` for projects with none so a resolved request clears its badge next sync.

`syncAll()` runs once per session (plus a resume after an interrupted refresh) - it forces the whole
preload and is far too heavy for a pull-to-refresh. `ProjectsListScreen`/`ProjectDetailScreen` call
`fetchPendingJoinRequestCounts()` directly from their own refresh actions instead.

---

## Caching layers

`CrucibleRepository` (`data/repository/CrucibleRepository.kt`) is the **single source of truth for
all in-memory caching**. Every cacheable read goes through it, backed by one `ObservableCache<K, V>`
per data type (in-memory, 10-min TTL, LRU eviction), each exposing `observeX()` (reactive `Flow`),
`fetchX(forceRefresh)` (cache-first), and `getCachedX()` (synchronous).

```
CrucibleRepository
  ├── resourceObservableCache     ObservableCache<uuid, CrucibleResource>
  ├── resourceTypeObservableCache ObservableCache<uuid, String>          - "sample"/"dataset", for
  │                                                                        screens holding only a UUID
  ├── thumbnailObservableCache    ObservableCache<uuid, List<Thumbnail>>
  ├── projectsObservableCache     ObservableCache<Unit, List<Project>>   - member projects list
  ├── projectObservableCache      ObservableCache<projectId, Project>    - per-project, incl. non-member
  │                                                                        projects via discover-search
  ├── projectMembersObservableCache  ObservableCache<projectId, List<User>>
  ├── instrumentsObservableCache  ObservableCache<Unit, List<Instrument>>
  ├── instrumentDatasetsObservableCache  ObservableCache<instrumentName, List<Dataset>>
  ├── projectSamplesObservableCache      ObservableCache<projectId, List<Sample>>
  ├── projectDatasetsObservableCache     ObservableCache<projectId, List<Dataset>>
  ├── pendingJoinRequestCountObservableCache  ObservableCache<projectId, Int>  - led projects only
  └── datasetFilesObservableCache ObservableCache<datasetUuid, List<AssociatedFile>>

PersistentProjectCache  (disk, 24h TTL)  - project summary lists only
```

`projectMembersObservableCache` is fetched alongside the project itself in `ProjectDetailScreen`'s
load effect and shared by the collapsing header's member count and `rememberOwnerNames`'s
owner-groupby resolution, so `GET /projects/{id}/users` runs once per project.

`PersistentProjectCache` needs a `PlatformContext`, so it stays outside `CrucibleRepository`.
`HomeViewModel` reads it on cold start and calls `repository.seedProjects()` to warm the in-memory
cache; `HomeScreen` supplies the context, since `getPlatformContext()` is `@Composable`-only and the
ViewModel can't call it.

**`fetchFileUrl(mfid)` is the one deliberate non-cache.** Signed download URLs are always fetched
fresh - it's only called on a share/download tap, never from a preload, so there's no repeated read a
cache would help, and reusing a stale-but-unexpired signed URL has no upside.

`invalidateAll()` clears every cache above in one call (logout, API key change, Cache settings'
"Clear All Cache" - which also clears `PersistentProjectCache` separately, since that tier isn't
owned here). `getCacheStats()` returns a snapshot for that same screen.

---

## ViewModels

Every list/detail/manage/create screen has a ViewModel in commonMain, constructor-injected via Koin:
`ResourceDetailViewModel`, `ProjectsListViewModel`, `ProjectDetailViewModel`, `ManageProjectViewModel`,
`InstrumentListViewModel`, `InstrumentDetailViewModel`, `ManageInstrumentViewModel`, `AccountViewModel`,
`CreateSampleViewModel`, `CreateDatasetViewModel`, `CreateProjectViewModel`, `EditResourceViewModel`,
`HomeViewModel`, `UserProfileViewModel`.

`UserProfileViewModel` (added when "Add to Project" landed on `UserProfileScreen`) holds the
viewed user (`UserProfileState`), the current user's own project list (`myProjects`, sourced from
`CrucibleRepository.observeProjects()` - already scoped server-side to member projects, so no new
fetch), and `addToProjectState` for the add-in-progress/result feedback the screen turns into a
toast. `checkProjectMembership()` - triggered when the "Add to Project" sheet opens, not on
screen load - fetches each of `myProjects`' member lists in parallel via
`CrucibleRepository.fetchProjectMembers()` (cache-backed, so free if already loaded elsewhere) and
matches the viewed user by ORCID/username into `memberProjectIds`, with `isCheckingMembership`
covering the gap so the sheet shows a pending state instead of flashing "Add" for projects that
turn out to already include them. `addToProject()` mirrors `ManageProjectViewModel.addMember()`'s
call shape (`addProjectMember`, invalidate that project's member cache on success) and additionally
folds the newly-added project into `memberProjectIds` on success.

Most expose a single `StateFlow<LoadState<T>>` (`ui/common/LoadState.kt`) rather than separate
loading/error/data/refreshing flags. Two exceptions:

**`HomeViewModel`** exposes three flows (`projects`, `fetchError`, `isPreloading`) plus a background
`preload()` with failure-tolerant batching (stops after 5 consecutive project-fetch failures). The
screen has three genuinely independent concerns - the list, a foreground error, and a background
prefetch that fails silently by design - so forcing them into one `LoadState` would lose information.

**`ResourceDetailViewModel`** drives the detail pager:

- `uiState: StateFlow<UiState>` - `Idle | Loading | Success(uuid, isRefreshing) | Error(message)`.
  `Success` carries **only the uuid**: the screen and every pager page read the resource and
  thumbnails from `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly, so
  there is no second copy of resource state to keep in sync.
- `isSyncing: StateFlow<Boolean>` - true while `DataSyncManager.syncAll()` runs (drives the home spinner).
- `fetchResource(uuid)` shows the cached version immediately, then always fetches fresh for detail.
- `refreshResource(uuid)` / `refreshThumbnails(uuid)` force-refresh through the repository; observers
  pick up the fresh value.
- `getCardState`/`setCardState` persist expand/collapse across pager pages (LRU-capped at
  `MAX_CARD_STATE_ENTRIES`).
- `startBackgroundSync()` - sync pauses during a user-initiated refresh and resumes after.
- `reset()` clears state on leaving the screen.

---

## Pull-to-refresh

All screens use M3's `PullToRefreshBox`. Screens with a `LoadState` ViewModel read the flag straight
off the state via `LoadState<T>.isRefreshingNow` rather than keeping a screen-local boolean.

`ResourceDetailScreen` is the one exception, with a `localRefreshState` flag: pulling on a *sibling*
page fetches that sibling inline through the repository without involving the ViewModel (whose
`isRefreshing` tracks only the navigated-to resource), so that case needs its own flag.

Either way, set the flag before the coroutine's work and clear it in a `finally` block, so the
spinner appears immediately and always clears - including on error.

Content does **not** move during the pull; the indicator overlays it, matching M3 and iOS
`UIRefreshControl` behavior.

---

## Navigation (`ui/navigation/`)

`Screen` is a sealed class of route strings; optional args use `?argName={argName}`, and special
characters in segments go through `encodeRouteSegment()`. 27 routes - see `Screen.kt` for the list,
which is the only place worth reading for it.

---

## ResourceDetailScreen pager

Siblings are all resources of the same type within the same project, drawn from the project cache
(`sameTypeSamples`/`sameTypeDatasets` params).

- Plain bounded pager: `pageCount = siblingList.size`, `initialPage = siblingIndex`, so it opens at
  the right position immediately. No virtual `Int.MAX_VALUE` count, no wrap-around. A `LaunchedEffect`
  scroll only covers the cold-start case where the sibling list wasn't resolved yet.
- **No manual preload or eviction windows.** Each page is `key(pageUuid)`'d and self-contained: it
  observes `observeResource(pageUuid)`/`.observeThumbnails(pageUuid)` and runs its own
  `LaunchedEffect(pageUuid) { repository.fetchResourceByUuid(pageUuid) }`. `HorizontalPager` decides
  which pages exist; `ObservableCache`'s TTL + LRU decides what's evicted. Earlier versions kept
  `loadedResources`/`enrichedUuids`/`failedEnrichmentUuids` maps and ±N distance math, which caused
  repeated stale- and flashing-content bugs - don't reintroduce them.
- A page renders its lightweight sibling-list stub immediately and swaps in the enriched resource in
  place, so there's no per-page spinner or flash. Only a page-local `enrichmentFailed` flag marks a
  failed enrichment.
- Swiping is a pure UI gesture - the ViewModel isn't updated, and `UiState.Success.uuid` stays the
  navigated-to resource. Pull-to-refresh on a sibling calls `fetchResourceByUuid(uuid, forceRefresh =
  true)` (not invalidate-then-fetch, so observers keep the existing value until the fresh one lands).

---

## Dependency injection (Koin)

- **`di/AppModule.kt`** - the shared module: `ApiClient`, `CrucibleRepository`, and `DataSyncManager`
  as `single`s, every ViewModel via `viewModelOf(::X)`.
- **`di/KoinInit.kt`** - `initKoin(platformModule)` starts Koin with `appModule` plus a
  platform-supplied module. `AppPreferences` is *not* in `appModule` because Android's implementation
  needs a `PlatformContext`; each platform's entry point provides it and calls `initKoin(...)` once,
  guarded by `KoinPlatformTools.defaultContext().getOrNull() == null`.
- **Entry points**: `MainActivity.onCreate()` (Android) and `App()` (`iosMain/App.kt`) both call
  `initKoin(...)` before rendering `NavGraph`.
- **In Compose**: `koinViewModel<T>()` for ViewModels, `koinInject<T>()` for other singletons.
- **`CrucibleRepository`** takes just `ApiClient` and is the single point of contact for
  fetch-with-cache logic. A ViewModel either goes through it or, for one-shot mutations with no shared
  caching, takes `ApiClient` directly - both fine. Reaching for a global instead of a constructor
  parameter is not.

**Leaf-composable exception (accepted, not a gap):** `InstrumentPickerField`, `FilterSheet`, and
`AssociatedFilesCard` call `koinInject<>()` from inside the composable rather than through an owning
ViewModel (`AssociatedFilesCard` injects `CrucibleRepository` for its cached file-list/download-URL
reads; the other two `ApiClient`). Each is reused from multiple unrelated parents with no single
owning ViewModel - `InstrumentPickerField` appears in both `CreateDatasetScreen` and
`EditResourceScreen` - so a per-use-site ViewModel or threaded callbacks would add wiring for no
benefit. `koinInject` still gives them a real, swappable dependency, which was the actual problem
being solved. Don't half-thread callbacks through call sites to "fix" this.

---

## iOS entry point

`iosMain/App.kt` → `iosMain/MainViewController.kt` → `iosApp/ContentView.swift` → `iOSApp.swift`.

`App.kt` mirrors `MainActivity` but uses `IosAppPreferences` (NSUserDefaults via
multiplatform-settings); `ConnectivityObserver` uses NWPathMonitor, which needs no context. Xcode
setup is in `dev/platform-parity.md`.

---

## Shared utilities (`data/util/`)

| File | Contents |
|---|---|
| `SearchExtensions.kt` | `matchesSearch()` for Sample, Dataset, Instrument, JsonObject, Project |
| `DateTimeUtils.kt` | `MONTH_NAMES`, `dateGroupKey(String?)` - ISO timestamp → "Mon YYYY" |
| `SortUtils.kt` | `SortField` enum, `SortState`, `List<T>.applySortState()` |
| `FormatUtils.kt` | File size / date formatting, `userDisplayName()` |
| `CryptoUtils.kt` | `PlatformCrypto.sha256Hex()` (expect/actual) for upload dedup |

`fetchProjectData(projectId)` (parallel sample+dataset fetch behind a per-project mutex) is a method
on `CrucibleRepository`, not a standalone utility. `DuplicateHolder.kt` lives in `ui/create/` rather
than here - it's an in-memory clipboard for the duplication flow, kept next to the screens it serves.

---

## Preferences (`data/preferences/AppPreferences.kt`)

A platform-agnostic interface (DataStore on Android, NSUserDefaults on iOS). Every value is a
`StateFlow`.

| Preference | Key | Notes |
|---|---|---|
| API key | `api_key` | |
| API base URL | `api_base_url` | Default `https://crucible.lbl.gov/api/v2/` |
| Graph Explorer URL | `graph_explorer_url` | Default `https://crucible.lbl.gov/explore/` |
| Theme mode | `theme_mode` | `system` / `light` / `dark` |
| Accent colour | `accent_color` | One of the 12 named accents |
| Accent contrast | `accent_contrast` | `standard` / `medium` / `high` |
| Dynamic colour | `use_dynamic_color` | Android 12+ only; forced false on iOS |
| Last visited resource | `last_visited_resource`, `last_visited_resource_name` | |
| Floating scan button | `floating_scan_button` | |
| Pinned projects | `pinned_projects` | |
| Synced projects | `synced_projects` | Preloaded in the background by `DataSyncManager.syncAll()` |
| Sync setup complete | `sync_setup_complete` | Set after the first-visit sync picker |
| Pinned / hidden instruments | `pinned_instruments`, `hidden_instruments` | |
| User ORCID | `user_orcid` | |
| User profile | `user_profile` | JSON-serialized `User`; `userProfile?.uniqueId` is the source of truth for ORCID |
| Resource history | `resource_history` | `HistoryItem`: `uuid`, `name`, `timestamp`, `resourceType?`, `projectId?` - `projectId` is recorded at view time, not derived from a cache lookup at render time |
| Sample / dataset / instrument group-by | `sample_group_by`, `dataset_group_by`, `instrument_group_by` | Defaults `TYPE` / `MEASUREMENT` / `MEASUREMENT` |
| Default project tab | `default_project_tab` | `SAMPLES` / `DATASETS` |
| People / Project result limit | `people_result_limit`, `project_result_limit` | Caps each category's results per search independently; default 5 |

---

## Testing

```bash
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:testAndroidHostTest
```

Runs in ~2s. Two gates gate on it: `.claude/hooks/pre-commit-check.sh` blocks any `git commit` made
through Claude Code's Bash tool whose tests fail, and `scripts/release.sh` runs them again in its
verify step.

**CI on tag only is deliberate, not a gap.** `.github/workflows/release.yml` fires on a `v*.*.*` tag
(or `workflow_dispatch`) and nothing else, because that workflow takes up to 20 minutes on GitHub -
far too slow to sit in front of every push or PR. Don't propose adding a push/PR trigger; the
trade-off has been made. The commit hook is the compensating control: it's the earliest automated
signal a broken test gets, which is why tests are gated there rather than left advisory. Commits made
by hand in another terminal bypass it, so run the suite yourself in that case.

**Layout**: `app/src/commonTest/kotlin/`, mirroring the production package path
(`data/cache/ObservableCacheTest.kt` tests `data/cache/ObservableCache.kt`). Platform-agnostic, so
one suite covers both targets.

**Dependencies** (`commonTest` block in `app/build.gradle.kts`): `kotlin("test")` and
`kotlinx-coroutines-test`. No mocking library, deliberately - see below.

### What is covered

| Suite | Covers |
|---|---|
| `ObservableCacheTest` | TTL expiry, LRU eviction at capacity, `invalidate`/`invalidateAll`, `ageMillis`, `observe` emission semantics |
| `CrucibleRepositoryTest` | Cache-miss paths: `getCachedX` returns null, `invalidateX` is a safe no-op, `observeX` emits null when uncached, `fetchSiblings` fallbacks |
| `FormatUtilsTest` | `formatDateTime` timezone conversion, missing-offset fallback, compact AM/PM, null and unparseable input |
| `SyncSuggestionsTest` | Which projects are suggested (led, pinned, the union without duplicates) and their sort order |

Nothing in `ui/` is tested. There is no instrumented, Compose-UI, or screenshot suite, and no
ViewModel tests.

### Patterns to follow

- **Inject time rather than sleeping.** `ObservableCacheTest`'s `cacheWithClock(ttl, maxSize, clock)`
  helper passes a fake `() -> Long`, so TTL tests are instant and deterministic. Never use a real
  delay to cross an expiry boundary.
- **`runTest` for anything `Flow`-shaped**, which is every `observe*` test.
- **Assert against recomputed values, not hardcoded output**, wherever the environment can vary.
  `FormatUtilsTest` derives the expected local hour the same way production code does, so it passes
  in any timezone. A hardcoded `"2:32 PM"` would pass only on the machine that wrote it.
- **No fakes or mocks.** Everything tested so far is either pure or exercised on a path that never
  touches the network, which is why `CrucibleRepositoryTest` covers only cache misses and fallbacks.
  Testing a hit path means introducing a fake `ApiClient`; that's a reasonable thing to add, just not
  something the suite currently does.

### When to add one

Add a test when you add or change **pure logic in `data/`** - cache and TTL behaviour, formatting,
sorting, grouping, search matching, or any pure function with branches. These are cheap to test, and
three of the four existing suites exist because the logic they cover broke once
(`FormatUtilsTest`'s first case documents a timezone bug it guards against).

Don't add one for a screen, a ViewModel, or an API call. There's no harness for the first two, and
the third would test Ktor rather than this app.

---

## Common gotchas

**Plain `remember` doesn't survive navigating away and back.** Navigation-Compose only composes the
top of the back stack, so pushing a destination fully disposes the composable underneath it; on
`popBackStack()` it recomposes from scratch and any plain `remember`ed value silently resets. Any
screen-level state that must survive a push-and-pop round trip needs `rememberSaveable` - scroll
position is the exception, since `rememberLazyListState()` already saves itself. Two consequences:

- `ProjectDetailScreen`'s per-group expand state uses `rememberSaveable` + `stateMapSaver()`
  (`ui/common/SaveableStateMap.kt`).
- **Prefer a full nav destination over a bottom sheet for anything whose content itself opens another
  destination.** `EditResourceScreen` is a real `Screen.EditResource` rather than a sheet for exactly
  this reason: as a sheet it had to route out to `MetadataEditorScreen`, which disposed the parent's
  sheet-visibility flags, so the sheet never reopened and the edit was silently discarded.

**`getPlatformContext()` is `@Composable`** - capture it at composable scope
(`val ctx = getPlatformContext()`) before passing into lambdas. It cannot be called inside `onClick`,
`remember {}`, or `LaunchedEffect {}`.

**expect/actual defaults** - default parameter values go on the `expect` side only. Repeating them on
the `actual` is a compile error.

**Phantom gaps in `LazyColumn`** - see `dev/style.md`'s Spacing & layout.

**iOS targets can't build on Linux.** `kotlin.native.ignoreDisabledTargets=true` in
`gradle.properties` suppresses the warnings; build for iOS on macOS only.

---

## Known gaps

- iOS: no deep-link/URL-scheme handling, no launch screen - see `dev/platform-parity.md`. Not
  blocking; iOS distribution isn't active yet.
