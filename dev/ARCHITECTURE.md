# Architecture Notes

Branch: `main`
Last updated: 2026-07-21

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

### Package layout (commonMain)

```
crucible.lens
├── data
│   ├── api/          CrucibleApiService (Ktor), ApiClient, ApiResult sealed class
│   ├── cache/        CacheManager (in-memory 10min TTL), PersistentProjectCache
│   ├── model/        CrucibleResource.kt — all data classes
│   ├── network/      ConnectivityObserver (expect/actual)
│   ├── preferences/  AppPreferences interface, PreferencesFactory (expect/actual)
│   ├── repository/   CrucibleRepository — single point of contact for resource/project/instrument fetches
│   ├── sync/         DataSyncManager — background cache preload via CrucibleRepository
│   └── util/         SearchExtensions, DateTimeUtils, SortUtils, FormatUtils, CryptoUtils, DuplicateHolder
├── di/               AppModule (Koin module), KoinInit (initKoin())
└── ui
    ├── common/       QrCodeDialog, AppTopBar, AppIcons, LazyColumnScrollbar, …
    ├── create/       CreateSampleScreen, CreateDatasetScreen, CreateEditViewModels, AddFilesScreen
    ├── detail/       ResourceDetailScreen, ResourceDetailViewModel, EditResourceSheet, LinkResourceSheet
    ├── history/      HistoryScreen
    ├── home/         HomeScreen
    ├── instruments/  InstrumentListScreen/ViewModel, InstrumentDetailScreen/ViewModel, ManageInstrumentScreen/ViewModel
    ├── metadata/     MetadataEditorScreen, MetadataHolder
    ├── navigation/   NavGraph, Screen sealed class
    ├── projects/     ProjectsListScreen/ViewModel, ProjectDetailScreen/ViewModel, ManageProjectScreen/ViewModel
    ├── scanner/      QRScannerPlatform (QRCodeScannerView via easyqrscan)
    ├── search/       SearchScreen
    ├── settings/     SettingsScreen, ApiSettingsScreen, AppearanceSettingsScreen, CacheSettingsScreen,
    │                 AboutSettingsScreen, AccountScreen/ViewModel, UserProfileScreen, OrcidLoginScreen
    └── theme/        CrucibleScannerTheme, Typography
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

## ViewModels

Every list/detail/manage/create screen has its own `ViewModel` (commonMain, platform-agnostic),
constructor-injected via Koin (see "Dependency injection (Koin)" below): `ResourceDetailViewModel`,
`ProjectsListViewModel`, `ProjectDetailViewModel`, `ManageProjectViewModel`, `InstrumentListViewModel`,
`InstrumentDetailViewModel`, `ManageInstrumentViewModel`, `AccountViewModel`, `CreateSampleViewModel`,
`CreateDatasetViewModel`, `EditResourceViewModel`.

`ResourceDetailViewModel` is the most involved — it drives the resource detail pager:
- `uiState: StateFlow<UiState>` — `Idle | Loading | Success(resource, thumbnails, isRefreshing) | Error`
- `isSyncing: StateFlow<Boolean>` — true while `DataSyncManager.syncAll()` is running (drives home screen spinner)
- `fetchResource(uuid)` — shows cached version immediately, always fetches fresh for full detail
- `refreshResource(uuid)` — evict cache, refetch; if UUID matches current resource → full refresh; if sibling → fetch+cache without changing `_uiState.resource`
- `getCardState` / `setCardState` — persists expand/collapse state across pager pages
- `startBackgroundSync()` / sync is paused during user-initiated refresh and resumed after
- `preloadRelatedResources(resource)` — background prefetch of linked resource UUIDs

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

All 22 routes (see `Screen.kt` for the exact list): `Home`, `Scanner`, `Detail`, `History`, `Search`,
`Projects`, `ProjectDetail`, `ManageProject`, `Instruments`, `InstrumentDetail`, `ManageInstrument`,
`Settings`, `SettingsApi`, `SettingsAppearance`, `SettingsCache`, `SettingsAbout`, `SettingsAccount`,
`OrcidLogin`, `CreateSample`, `CreateDataset`, `AddFiles`, `MetadataEditor`, `UserProfile`

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
| `FormatUtils.kt` | File size / date formatting helpers |
| `CryptoUtils.kt` | `PlatformCrypto.sha256Hex()` (expect/actual) for upload dedup |
| `DuplicateHolder.kt` | In-memory clipboard for sample/dataset duplication flow |

`fetchProjectData(projectId)` (parallel sample+dataset fetch with a per-project mutex) used to live in
a standalone `ProjectFetcher.kt`; it is now a method on `CrucibleRepository` — see
"Dependency injection (Koin)" below.

---

## Dependency injection (Koin)

The app uses [Koin](https://insert-koin.io/) for dependency injection. `ApiClient` and `CacheManager`
were converted from Kotlin `object` singletons to plain classes and are registered as Koin `single`s
in `di/AppModule.kt`, giving them the same effective app-lifetime-singleton behavior they had before,
but as constructor-injectable dependencies rather than globally-reachable statics.

- **`di/AppModule.kt`** — the shared module: `ApiClient`, `CacheManager`, `CrucibleRepository`,
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
- **`CrucibleRepository`** (`data/repository/CrucibleRepository.kt`) is constructor-injected with
  `ApiClient` + `CacheManager` and is the single point of contact for resource/project/instrument
  fetch-with-cache logic. Every ViewModel that fetches Crucible data is expected to go through it or,
  for one-shot mutations (create/update/delete) that don't share caching logic with anything else, to
  take `ApiClient`/`CacheManager` directly via constructor injection — both are acceptable; reaching
  for the global object instead of the constructor parameter is not.

**Leaf-composable exception (accepted, not a gap):** `InstrumentPickerField`, `FilterSheet`, and
`AssociatedFilesCard` call `koinInject<ApiClient>()` (and `CacheManager` where needed) directly from
within the composable rather than through an owning ViewModel. This is intentional: each of these
components is reused from multiple, unrelated parent screens with no single owning ViewModel
(e.g. `InstrumentPickerField` appears in both `CreateDatasetScreen` and `EditResourceSheet`).
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
