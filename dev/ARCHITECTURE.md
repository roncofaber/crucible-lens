# Architecture Notes

Branch: `ios-development`  
Last updated: 2026-05-01

---

## Stack

| Concern | Library |
|---|---|
| Language | Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP) 1.7.3 |
| UI | Jetpack Compose / Material 3 — identical on Android and iOS |
| Networking | Ktor 3.0.3 (OkHttp engine on Android, Darwin engine on iOS) |
| Serialization | kotlinx.serialization 1.7.3 |
| Preferences | multiplatform-settings (NSUserDefaults on iOS, DataStore on Android) |
| Date/time | kotlinx-datetime 0.7.1 |
| QR scan + display | qr-kit 3.1.3 (Chaintech Network) |
| Image picking | moko-media 0.12.0 (iOS); ActivityResultContracts (Android) |
| WebView (ORCID) | compose-webview-multiplatform 2.0.3 |
| Navigation | org.jetbrains.androidx.navigation 2.9.2 |
| Min SDK | 26 (Android) |
| Target/Compile SDK | 36 (Android) |
| Kotlin | 2.1.0 |
| AGP | 9.2.0 |
| Gradle | 9.4.1 |
| No DI framework | dependencies passed explicitly |

---

## Source set layout

```
app/src/
├── commonMain/        Shared code — all UI screens, API, models, cache, navigation
├── androidMain/       Android actuals + Android-only screens (CreateDataset, Widget)
├── iosMain/           iOS actuals
└── main/java/         Android entry point (MainActivity) + PreferencesManager
```

### Package layout (commonMain)

```
crucible.lens
├── data
│   ├── api/          CrucibleApiService (Ktor), ApiClient singleton, ApiResult sealed class
│   ├── cache/        CacheManager (in-memory 10min TTL), PersistentProject/ThumbnailCache
│   ├── model/        CrucibleResource.kt — all data classes
│   ├── network/      ConnectivityObserver (expect/actual)
│   ├── preferences/  AppPreferences interface, PreferencesFactory (expect/actual)
│   └── util/         SearchExtensions, DateTimeUtils, SortUtils, ProjectFetcher, DuplicateHolder
└── ui
    ├── common/       QrCodeDialog, AnimatedPullToRefresh, LazyColumnScrollbar, BrowserUtils, …
    ├── create/       CreateSampleScreen (+ expect for CreateDatasetScreen)
    ├── detail/       ResourceDetailScreen
    ├── history/      HistoryScreen
    ├── home/         HomeScreen
    ├── instruments/  InstrumentListScreen, InstrumentDetailScreen
    ├── navigation/   NavGraph, Screen sealed class
    ├── projects/     ProjectsListScreen, ProjectDetailScreen
    ├── scanner/      QRScannerPlatform (QRCodeScannerView via qr-kit)
    ├── search/       SearchScreen
    ├── settings/     ApiSettingsScreen, AppearanceSettingsScreen, CacheSettingsScreen,
    │                 OrcidLoginScreen (compose-webview-multiplatform), SettingsScreen, AboutSettingsScreen
    ├── theme/        CrucibleScannerTheme, Typography
    └── viewmodel/    ScannerViewModel
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
- `GET /projects`, `GET /projects/{id}/users`
- `GET /instruments`, `GET /instruments/{id}`
- `GET /datasets?instrument_name=X&limit=N` — datasets by instrument
- `GET /idtype/{uuid}` — resolve resource type before fetching
- `POST /deletion_requests` — soft-delete request

---

## Caching layers

```
CacheManager (in-memory, 10-min TTL, LRU eviction)
  ├── resources        Map<uuid, CrucibleResource>          (max 50)
  ├── thumbnails       Map<uuid, List<String>>               (max 20, base64 PNG)
  ├── projects         List<Project>
  ├── instruments      List<Instrument>
  ├── projectSamples   Map<projectId, List<Sample>>          (max 30)
  ├── projectDatasets  Map<projectId, List<Dataset>>         (max 30)
  └── instrumentDatasets Map<instrumentName, List<Dataset>>  (max 15)

PersistentProjectCache  (disk, 24h TTL)   — project summary lists only
PersistentThumbnailCache (disk, 7 day TTL) — thumbnail base64 blobs per dataset uuid
```

Cache is cleared entirely on API key or base URL change.

---

## ViewModel (`ResourceDetailViewModel`)

Single `ViewModel` (commonMain, platform-agnostic) for the resource detail flow:
- `uiState: StateFlow<UiState>` — `Idle | Loading | Success(resource, thumbnails, isRefreshing) | Error`
- `isSyncing: StateFlow<Boolean>` — true while `DataSyncManager.syncAll()` is running (drives home screen spinner)
- `fetchResource(uuid)` — shows cached version immediately, always fetches fresh for full detail
- `refreshResource(uuid)` — evict cache, refetch; if UUID matches current resource → full refresh; if sibling → fetch+cache without changing `_uiState.resource`
- `getCardState` / `setCardState` — persists expand/collapse state across pager pages
- `startBackgroundSync()` / sync is paused during user-initiated refresh and resumed after
- `preloadRelatedResources(resource)` — background prefetch of linked resource UUIDs

Project/Instrument/List/Detail screens manage their own local coroutine state — they do not use `ResourceDetailViewModel`.

---

## Pull-to-refresh pattern

All screens use `PullToRefreshBox` (M3) with a **dedicated refresh flag** separate from the initial-load flag:

| Screen | PTR flag | Initial-load flag |
|---|---|---|
| ProjectsListScreen | `isUserRefreshing` | `isLoading` |
| InstrumentListScreen | `isUserRefreshing` | `isLoading` |
| ProjectDetailScreen | `isRefreshingNow` | `isLoading` |
| InstrumentDetailScreen | `isRefreshingNow` | `isLoading` |
| ResourceDetailScreen | `localRefreshState` (via `isRefreshing` from ViewModel) | ViewModel `UiState.Loading` |

The PTR flag is set to `true` synchronously before the coroutine launches (`if (forceRefresh) flag = true`) and cleared in the coroutine's `finally` block. This ensures the spinner appears immediately on pull and disappears cleanly when done.

Content does **not** move during pull-to-refresh — the M3 `PullToRefreshBox` indicator overlays the content. This matches the standard Material 3 and iOS `UIRefreshControl` behavior.

---

## Navigation (`NavGraph.kt`)

`Screen` sealed class with `route` strings. Optional args use query params `?argName={argName}`.  
Special characters in route segments encoded via `encodeRouteSegment()`.

All 18 routes: `Home`, `Scanner`, `Detail`, `History`, `Search`, `Projects`, `ProjectDetail`,
`Instruments`, `InstrumentDetail`, `Settings`, `SettingsApi`, `SettingsAppearance`,
`SettingsCache`, `SettingsAbout`, `OrcidLogin`, `CreateSample`, `CreateDataset`

---

## ResourceDetailScreen pager

Siblings are all samples (or datasets) of the same type within the same project, drawn from the project cache (`sameTypeSamples` / `sameTypeDatasets` params).

- `pageCount = siblingList.size`, `initialPage = siblingIndex` — pager opens at the correct position immediately, no post-composition scroll
- Lazy enrichment: fetches full resource data (with links) for ±10 pages around the current page; thumbnails for ±2 pages
- Eviction: resources beyond ±20 pages removed from local maps; thumbnails beyond ±3
- Primary resource is eagerly seeded into `loadedResources` / `enrichedUuids` on first composition so its page never shows a loading state
- Swiping is a pure UI gesture — the ViewModel is not updated; `resource` in `UiState.Success` always stays as the navigated-to resource
- Pull-to-refresh on a sibling fetches and caches that sibling without changing the primary `UiState.resource`; `siblingReloadTrigger` increments after completion to pick up fresh data
- Per-page loading/content visibility is driven by `enrichedUuids` and `failedEnrichmentUuids` (not by `mfid` comparisons)

---

## iOS entry point

`iosMain/App.kt` → `iosMain/MainViewController.kt` → `iosApp/ContentView.swift` → `iOSApp.swift`

`App.kt` mirrors `MainActivity` but using `IosAppPreferences` (NSUserDefaults via multiplatform-settings).
ConnectivityObserver uses NWPathMonitor on iOS (no context needed).

See `dev/IOS_SETUP.md` for Xcode project setup instructions.

---

## Shared utilities (`data/util/`)

| File | Contents |
|---|---|
| `SearchExtensions.kt` | `matchesSearch()` for Sample, Dataset, Instrument, JsonObject, Project |
| `DateTimeUtils.kt` | `MONTH_NAMES`, `dateGroupKey(String?)` — ISO timestamp → "Mon YYYY" |
| `SortUtils.kt` | `SortField` enum, `SortState`, `List<T>.applySortState()` |
| `ProjectFetcher.kt` | `fetchProjectData(projectId)` — parallel sample+dataset fetch with mutex |
| `DuplicateHolder.kt` | In-memory clipboard for sample/dataset duplication flow |

---

## Known architectural debt

**Repository pattern is inconsistently applied.** `CrucibleRepository` (`data/repository/CrucibleRepository.kt`)
wraps `ApiClient` + `CacheManager` with proper error mapping (`ResourceResult` sealed class), but only
`ResourceDetailViewModel` uses it. Every other ViewModel, and several leaf composables
(`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`, `DatasetDetailsCard`), call
`ApiClient.service.*` directly. This means there are currently two competing conventions for data
access in the codebase, and new code has no clear signal which one to follow.

Two ways to resolve this — not yet decided:
1. Extend `CrucibleRepository` to cover all data access and migrate every ViewModel/composable to go
   through it (better testability, single error-mapping point, matches the "MVVM + Repository"
   architecture this project claims to use).
2. Delete `CrucibleRepository` and standardize on direct `ApiClient` calls from ViewModels only —
   simpler, matches current majority practice, but still requires moving the API calls currently
   inside leaf composables (`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`,
   `DatasetDetailsCard`) into their owning ViewModels, since calling the network from a composable
   breaks the MVVM boundary regardless of which option is chosen.

**Unused KSP plugin.** `com.google.devtools.ksp` is applied in `app/build.gradle.kts` but no
`ksp(...)` dependency exists anywhere — likely a leftover from an abandoned codegen experiment
(Room, Moshi, etc.). Should be removed to shave a small amount of build time, once confirmed nothing
depends on it being present.

**No DI framework is a deliberate, documented choice** (see Stack table above) appropriate for this
project's size. The tradeoff: `ApiClient` and `CacheManager` are globally mutable singletons accessed
directly from ~20 files. This is consistent today, but will make future test-writing harder than it
would be with constructor-injected dependencies (e.g. via Koin, the common choice in KMP). Not worth
introducing now, but worth remembering if/when the project adds a test suite.

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
