# Architecture Notes

## Stack

- Language: Kotlin, Jetpack Compose, Material 3
- Min SDK 26, Target SDK 35
- Build: Gradle KTS, KSP for Moshi codegen
- No DI framework (Hilt/Koin are not used — pass dependencies explicitly)

---

## Package layout

```
crucible.lens
├── data
│   ├── api/          Retrofit service + ApiClient singleton
│   ├── cache/        CacheManager (in-memory), PersistentProjectCache, PersistentThumbnailCache
│   ├── model/        Moshi data classes — Sample, Dataset, Project, ResourceLink, request DTOs
│   ├── network/      OkHttp / interceptor config
│   ├── preferences/  PreferencesManager (DataStore)
│   ├── repository/   CrucibleRepository — typed fetch + fallback logic
│   └── util/
└── ui
    ├── common/       Shared composables (ScrollToTopButton, QrCodeDialog, LazyColumnScrollbar, …)
    ├── create/       CreateSampleScreen, CreateDatasetScreen
    ├── detail/       ResourceDetailScreen
    ├── home/         HomeScreen
    ├── navigation/   NavGraph, Screen sealed class
    ├── projects/     ProjectsListScreen, ProjectDetailScreen
    ├── scanner/      QRCodeScannerView
    ├── search/       SearchScreen
    ├── settings/     ApiSettingsScreen, CacheSettingsScreen, OrcidLoginScreen
    └── viewmodel/    ScannerViewModel (single shared ViewModel for resource detail flow)
```

---

## Data models (`data/model/CrucibleResource.kt`)

All JSON models use `@JsonClass(generateAdapter = true)` (KSP codegen — no reflection).  
Field mapping: `@Json(name = "snake_case")`.

Key types:
- `Sample` / `Dataset` — both implement `CrucibleResource` (sealed interface)
- `ResourceLink` — flat relationship record: `{unique_id, resource_type, name, relationship}`  
  `relationship` is `"parent"`, `"child"`, or `"associated"`
- Request DTOs: `SampleCreateRequest`, `DatasetCreateRequest`, `ThumbnailCreateRequest`,  
  `SampleUpdateRequest`, `DatasetUpdateRequest`

---

## API (`data/api/CrucibleApiService.kt`)

Retrofit + Moshi. Base URL is user-configurable (stored in DataStore via `PreferencesManager`).

Key design choices:
- `GET /samples/{uuid}?include_links=true` — returns sample with all relationships in one call
- `GET /datasets/{uuid}?include_links=true` — same for datasets
- There is **no** separate graph/relationships endpoint used client-side any more
- Project lists use `GET /projects/{id}/samples` and `GET /projects/{id}/datasets?include_metadata=true`

---

## Caching layers

```
CacheManager (in-memory, 10-min TTL, 50 items/type)
  ├── resources  Map<uuid, CrucibleResource>
  ├── thumbnails Map<uuid, List<String>>        base64 PNG strings
  ├── projects   List<Project>
  ├── projectSamples  Map<projectId, List<Sample>>
  └── projectDatasets Map<projectId, List<Dataset>>

PersistentProjectCache  (disk, 24h TTL)   — project lists only
PersistentThumbnailCache (disk, no TTL)   — thumbnail base64 blobs per dataset uuid
```

Thumbnail loading order: `CacheManager → PersistentThumbnailCache → network`.  
Always write back through all layers on fetch.

---

## ViewModel (`ScannerViewModel`)

Single `AndroidViewModel` for the resource detail flow. Exposes:
- `uiState: StateFlow<UiState>` — `Idle | Loading | Success | Error`
- `fetchResource(uuid)` — cache → network, triggers preloading of linked resources
- `refreshResource(uuid)` — evict cache then refetch
- `getCardState` / `setCardState` — persists expand/collapse state of detail screen sections
- `ensureResourceCached(uuid)` — silent prefetch, returns cached or fetched resource

Project screens do **not** use `ScannerViewModel`. They manage their own `LaunchedEffect` + `scope.launch` coroutines locally.

---

## ResourceDetailScreen pager

- `Int.MAX_VALUE` virtual page count when `n > 1` siblings (enables wrap-around swipe)
- Real index: `pagerState.currentPage % n`
- Initial virtual page: aligned near `Int.MAX_VALUE / 2` to give room in both directions
- Preload ±10 pages (relationships/resources), ±1 page (thumbnails)
- Evict resources beyond ±20 pages, thumbnails beyond ±2
- Cleanup uses circular distance: `minOf(d, n - d)`

---

## Navigation (`NavGraph.kt`)

`Screen` sealed class with `route` strings. Named args use `{argName}` placeholders.  
Optional args: use query params `?argName={argName}` with `defaultValue`.

Screens: `Home`, `Detail`, `Projects`, `ProjectDetail`, `Search`,  
`ApiSettings`, `CacheSettings`, `OrcidLogin`, `CreateSample`, `CreateDataset`

---

## Common gotchas

**Phantom gaps in LazyColumn**: `Arrangement.spacedBy(N.dp)` adds N dp between *every* slot
including zero-height `AnimatedVisibility` items. Fix: use `Arrangement.Top` and add
`padding(bottom = N.dp)` inside each item's visible content.

**Experimental API errors**: compiler errors (not warnings) — always add `@OptIn`. See STYLE.md.

**Moshi adapters**: all models need `@JsonClass(generateAdapter = true)`. Missing this annotation
causes a runtime crash (no adapter found), not a compile error.

**FileProvider authority**: `${applicationId}.fileprovider` — used for camera capture temp files.
Already configured in `AndroidManifest.xml` + `res/xml/file_paths.xml`.
