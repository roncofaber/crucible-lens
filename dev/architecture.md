# Architecture Notes

Deep reference for structure, data flow, and caching. `AGENTS.md` is the fast-reference layer; UI conventions live in `dev/style.md`.

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
| Browser links | AndroidX Browser 1.10.0 on Android, UIKit on iOS |
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
│   ├── sync/         DataSyncManager - persistence, request coalescing, and project synchronization
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
    ├── scanner/      ScannerScreen, ScannerViewModel, QRScannerPlatform
    ├── search/       SearchScreen, SearchViewModel
    ├── settings/     Account, service-account administration, app settings, profiles, and sign-in
    └── theme/        Theme.kt, Type.kt, Shape.kt, accents/ (12 hand-curated ColorSchemes)
```

---

## Data models (`data/model/CrucibleResource.kt`)

All JSON models use `@Serializable` + `@SerialName("snake_case")`. The decoder sets `ignoreUnknownKeys = true` + `isLenient = true` to tolerate API additions.

- **`CrucibleResource`** - sealed base: `uniqueId`, `name`, `description`, `keywords`.
- **`Sample` / `Dataset`** - both extend it; `name` is computed (`sampleName ?: uniqueId`) so a null API name never crashes. Both responses include a nullable `project` identity reference, while datasets also include an `instrument` reference. Shared resolved accessors prefer each reference's current label, slug, and canonical MFID while falling back to legacy flat fields for unresolved records and old persisted cache entries. Project-filtered collection responses can also carry the optional contextual `projectRelation`, whose assigned or shared value describes the relationship to the project in the request rather than the resource's actual assignment.
- **`ResourceLink`** - `{unique_id, resource_type, name?, relationship}`, where `relationship` is
  `"parent" | "child" | "associated"` (matching the API's Literal type).
- **`ResourceSearchResult`** - the unified row type both search modes produce. Its `projectId` is not returned by `/resources/metadata/search`; it is populated client-side in name-search mode, where `searchSamples`/`searchDatasets` already return full objects carrying it. Dataset name-search results also carry a transient resolved project label. Metadata-mode results therefore have a null `projectId` and fall back to the MFID. Preserve that asymmetry rather than adding per-result lookups.
- **`Project`** - `uniqueId` is the stable V3 MFID and `projectId` is the editable human-readable slug. Canonical detail caches, navigation, pins, and stored selections use only the MFID. Slugs are resolved through the exact project collection filter before navigation.
- **`Instrument`** - `uniqueId` is the stable V3 MFID, `instrumentId` is the editable human-readable slug, and `instrumentName` is the display label. Canonical detail caches and navigation use only the MFID. Dataset responses retain `instrument_id` and `instrument_name` for compatibility while their `instrument` reference supplies the current display name and canonical navigation target. `ownerOrcid` is the canonical owner identity, while `owner` is the optional expanded public user profile.
- Also: `Instrument`, `UserLead`, `AccountResponse`, `MetadataSearchResult`, and the
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
| `GET /resources/{uuid}?include_datasets=false&include_links=true&include_metadata=true&include_owner=true` | Unified fetch that resolves type and returns the resource in one call. Sample responses use lightweight links without the deprecated embedded dataset collection |
| `GET /samples/{uuid}?include_datasets=false&include_links=true&include_owner=true` | Full sample with lightweight relationships and expanded owner, without duplicate embedded dataset records |
| `GET /datasets/{uuid}?include_links=true&include_metadata=true&include_owner=true` | Dataset, scientific metadata, expanded owner, and lightweight instrument and project references inline |
| `GET /datasets?instrument_mfid={mfid}&limit=100&cursor=...` | Canonical instrument dataset pages. Instrument Detail loads one page automatically and follows `next_cursor` only after an explicit Load more action |
| `GET /samples?project_mfid={mfid}&project_scope={assigned|shared}&limit=1000&cursor=...` | Canonical project sample collections. Assigned results drive synchronization; Project Detail merges live Shared results into the same list |
| `GET /datasets?project_mfid={mfid}&project_scope={assigned|shared}&limit=1000&cursor=...` | Canonical project dataset collections with the same merge and cache boundary as project samples |
| `GET /samples/search?q={query}&project_mfid={mfid}&project_scope={assigned|shared|all}&limit=20` | Bounded relevance search with optional project relationship scope; no cursor or offset pagination |
| `GET /datasets/search?q={query}&project_mfid={mfid}&project_scope={assigned|shared|all}&limit=20` | Dataset equivalent of project-scoped sample name search |
| `GET /samples/facets?field=...`, `GET /datasets/facets?field=...` | Cursor-paginated value buckets used as editable filter suggestions. Facets do not restrict users from entering a new value |
| `GET /samples`, `GET /datasets` with `visibility`, `affiliation=owner`, and `*_is_null` | Advanced search filters for visibility, the signed-in owner's resources, and missing scientific or assignment fields |
| `GET /samples`, `GET /datasets` with `anchor_mfid`, `sort=name`, and `direction` | Inclusive bounded sibling windows around an open resource when its project collection is not cached |
| `PATCH /samples/{mfid}`, `PATCH /datasets/{mfid}` | Descriptive fields only. Project and instrument assignments use dedicated operations |
| `PUT /datasets/{dataset_mfid}/instrument` | Assigns a registered instrument by canonical MFID and returns the current and previous instrument references |
| `POST /resources/{mfid}/project` | Previews a sample or dataset project move; repeat with `?confirm=true` to execute |
| `GET /resources/{mfid}/access` | Canonical ACL rows for a sample or dataset detail, including owner, direct user, service-account, project, instrument, public, system, and unknown principals |
| `PUT`/`DELETE` `/resources/{mfid}/access/{users|projects}/{principal}` | Adds, changes, or revokes an ordinary direct grant. User and service-account writes use `users` with the principal MFID; project writes use `projects` with the project slug |
| `PUT`/`DELETE` `/resources/{mfid}/access/public` | Enables or disables public viewer access through its dedicated route |
| `GET`/`POST`/`PATCH` `/resources/{id}/metadata` | See "Scientific metadata" below |
| `GET /projects` | Member projects only (unlike `/projects/search`) |
| `GET /users?username={username}` | Canonical exact username lookup. The client requests at most two records and rejects zero or multiple matches |
| `GET /users?{username|unique_id}=...&is_service_account=true` | Exact service-account lookup for instrument operator binding. The server-side type filter is authoritative even when a public-safe response omits the account-type field |
| `GET /users/search?q=...&is_service_account=true` | Service-account-only autocomplete for instrument operator selection |
| `GET`/`POST /service_accounts`, `GET`/`PATCH /service_accounts/{unique_id}`, `POST /service_accounts/{unique_id}/rotate_key` | Capability-gated platform administration. Create and rotate responses contain an API key exactly once and the app never persists it |
| `PATCH /datasets/{dsid}/thumbnails/{thumbnail_id}` | Replaces a thumbnail name, image, or both for users with dataset edit capability |
| `GET /projects/{project_mfid}` | Canonical singleton project lookup with caller capabilities. Readable by any authenticated user - see "Access model" |
| `GET /projects?project_id={slug}` | Exact project-slug resolution with caller capabilities. The client requests at most two records and rejects zero or multiple matches |
| `GET /projects/search` | Project discovery search |
| `POST /projects` | Creates a project; see "Project creation" |
| `PATCH /projects/{project_reference}` | Updates `project_id`, title, organization, or status. Leadership fields are rejected. The app exposes project ID changes to project admins and owners |
| `POST /resources/{project_mfid}/transfer_ownership` | Previews project leadership transfer; repeat with `?confirm=true` to execute |
| `GET /projects/{project_reference}/users` | Paginated member list with each member's project role |
| `POST /projects/{project_reference}/users/{user_id}` | Editors and above can add a member strictly below their own role: editors through contributor, admins through editor, and owners through admin |
| `PATCH /projects/{project_reference}/users/{user_id}` | Editors and above can change only members strictly below their own role; same-role and owner mutations are rejected |
| `DELETE /projects/{project_reference}/users/{user_id}` | Platform administrators, the project owner, or the member themselves. The owner **cannot** remove themselves (409) - transfer ownership first |
| `GET /instruments?status={active|maintenance|decommissioned}&include_owner=true` | Paginated status-filtered instrument list with expanded owners. The app always sends a status and defaults to active |
| `GET /instruments/search?q=...&status=active` | Fuzzy instrument search restricted to active instruments for dataset pickers |
| `GET /instruments/{instrument_mfid}?include_owner=true` | Canonical singleton instrument lookup with expanded owner and caller capabilities |
| `GET /instruments?instrument_id={slug}` | Exact instrument-slug resolution with caller capabilities. The client requests at most two records and rejects zero or multiple matches |
| `POST /instruments` | Registers a self-owned instrument from its validated ID, display name, location, and optional descriptive fields. Service accounts are rejected by the API |
| `PATCH /instruments/{instrument_mfid}` | Editor-gated descriptive update or validated `instrument_id` change. Ownership and status are excluded |
| `POST /resources/{instrument_mfid}/transfer_ownership` | Owner or platform-admin preview and confirmed instrument ownership transfer |
| `POST /instruments/{instrument_mfid}/status?status=` | Admin-gated lifecycle transition among `active`, `maintenance`, and `decommissioned` |
| `GET`/`POST`/`DELETE` `/instruments/{instrument_mfid}/service_accounts...` | Admin-gated instrument service-account bindings |
| `POST /deletion_requests` | Soft-delete request |
| `POST /access_groups/{group_name}/join` | Request to join (`group_name` is always a `project_id` today); 409 if already a member or already pending |
| `GET /join_requests` | Filters `group_name`/`status`/`requester_id` - see "Join requests" |
| `PATCH /join_requests/{request_id}` | Approve/reject; approval adds the member server-side |
| `GET /account/join_requests?status=` | The caller's own history across all projects |

**Pagination**: `fetchAllPages` is retained for offset-based lists. `fetchAllPagesCursor` follows `next_cursor` for samples, datasets, projects, and instruments, and those collection requests use `include_total=false` when no progress total is needed. Facet requests also follow their opaque cursor. Search endpoints return a flat list and need neither helper.

`EditResourceScreen` keeps ordinary descriptive saves separate from project and instrument assignment. Selecting another project first requests the server preview and then presents the resolved source and destination for confirmation. Successful moves update the resource detail cache and invalidate both affected project-list caches. A dataset instrument change uses the dedicated assignment route, updates the returned embedded reference, and invalidates both affected instrument dataset caches. Instrument selection remains read-only when `can_manage_access` is false.

Dataset models do not include the removed V3 `source_folder` field, and dataset search does not index it. Dataset details do not present an aggregate size because file locations, backends, and individual sizes belong to associated-file records.

### Dataset file uploads

`DatasetFileUploader` owns initiation, GCS transfer, completion, ingestion, and optional thumbnail creation for both dataset creation and adding files to an existing dataset. Every `ApiResult` is checked. Its checkpoint records completed stages so a retry does not repeat file registration, ingestion, or thumbnail creation. `DatasetFileAttachment` preserves selected image filenames through `FilesHolder` and dataset creation. `AddFilesScreen` removes successful items, retains failed items with their checkpoints, stays open after partial failure, and reports exact success and failure counts. The current picker intentionally accepts images only because selections are held in memory; general data-file attachment requires a streaming file abstraction.

### Scientific metadata

`scientific_metadata` is **silently dropped** from `SampleUpdateRequest`, `DatasetUpdateRequest`,
`SampleCreateRequest`, and `DatasetCreateRequest`. It only moves via `POST`/`PATCH
/resources/{id}/metadata`, so create and edit flows always make two calls.

Both return `ApiResult<JsonObject>` (the resulting metadata), never `ApiResult<Unit>` - discarding
the payload once made a 409 or 500 from the metadata call completely invisible.

The routes are generic across sample/dataset/instrument and require **write** access, not just read.
`POST` creates or replaces (409 if non-empty metadata exists, unless `?overwrite=true`); `PATCH`
shallow-merges (new keys added, existing overwritten, nested dicts replaced wholesale, not
deep-merged) and never 409s. The `add-api-endpoint` skill has the PATCH-vs-POST decision rules.

### Access model

Exact project, instrument, sample, dataset, and generic resource-detail responses carry nullable `ResourceCapabilities` guidance for `can_edit`, `can_manage_access`, `can_change_status`, `can_transfer`, and `max_grant_role`. `GET /account/profile` also carries `AccountCapabilities` for project, instrument, sample, dataset, service-account, and create-for-others actions. General resource lists and searches leave capabilities null because no per-item ACL calculation is performed. Navigation and management screens use populated capabilities instead of inferring permissions from owner identity or cached roles, never include capabilities in write DTOs, and retain the previous role or ownership behavior only when communicating with a server that omits the field. The API remains authoritative for every mutation and all error responses remain user-visible.

Sample and dataset detail overflow menus expose Edit, Add file, Link, and thumbnail mutations only when `can_edit` is true, and expose Manage Access only when `can_manage_access` is true. Duplicate follows the matching account-level create capability. `ManageResourceAccessViewModel` loads canonical ACL rows through the repository, limits viewer through admin choices to `max_grant_role`, and keeps unsupported principal types visible but read-only. Owner grants are never changed through ACL routes, public access uses its dedicated endpoint, project writes use the returned slug, and user and service-account writes use the principal MFID. Failed refreshes retain the previous grants, and destructive confirmations stay open until the server confirms success.

`GET /projects/search` and `GET /projects/{id}` are readable by **any** authenticated user, not just
members. Non-members get `lead` as `UserPublicRead` (no email) and a null `scientific_metadata`
regardless of `?include_metadata=`; members and admins get the full `UserRead` and the metadata. That
asymmetry is what makes discover-search and the non-member view in `ProjectDetailScreen` work - don't
"fix" a null `lead` by gating the endpoint.

### Project creation

`POST /projects` allows any authenticated user to create a project naming an existing user as its lead. Exactly one flexible `project_lead` or explicit `project_lead_orcid`/`_email`/`_username` field is required; this app sends `_username` after resolving the user through search. The response `unique_id` is the stable project MFID, while `project_id` is an editable slug. Errors include `400` for a missing or conflicting lead identifier, `404` when the lead cannot be resolved, and `409` when the slug is unavailable.

`ManageProjectViewModel` keeps descriptive project edits and ownership changes separate. Title and organization use the ordinary project `PATCH`. Leadership transfer searches for a canonical user identity, previews `transfer_ownership` without mutation, shows both server-resolved owners, and executes only after confirmation. Success invalidates the project overview, detail, and member caches and immediately removes lead-only controls from the former lead's local state.

### Join requests

`requestToJoinProject` and `reviewJoinRequest` are one-shot mutations, so they go straight to `apiClient.service.*` from the owning ViewModel. `getMyJoinRequests` goes through `CrucibleRepository` because Account and Project Detail share its account-scoped result. `getJoinRequests` also uses the repository because its pending count per project drives the lead-facing dot on Home, the Projects list, and `ProjectDetailScreen`.

Authorization is what makes the bulk preload cheap. Passing `group_name` requires being that project's lead or an admin (403 otherwise), but **omitting it auto-scopes a non-admin lead to every project they lead in one call** (empty list, not 403, if they lead none). `DataSyncManager.syncAll()` therefore issues one `getJoinRequests(status = "pending")` and buckets by `groupName` client-side, writing `0` for projects with none so a resolved request clears its badge next sync.

`syncAll()` runs once per session (plus a resume after an interrupted refresh); it forces the whole preload and is far too heavy for pull-to-refresh. `ProjectsListScreen`/`ProjectDetailScreen` call `fetchPendingJoinRequestCounts()` directly from their own refresh actions instead.

---

## Caching layers

`CrucibleRepository` (`data/repository/CrucibleRepository.kt`) is the **single source of truth for all in-memory caching**. Every cacheable read goes through it, backed by one `ObservableCache<K, V>` per data type with a 10-minute freshness TTL and least-recently-read-or-written capacity eviction. Freshness-aware `get()` calls decide whether a network fetch is needed, while `peek()` and reactive `observe()` calls retain the last value until replacement, explicit invalidation, or capacity eviction. Repository APIs expose this as `observeX()`, `fetchX(forceRefresh)`, and `getCachedX()`.

```
CrucibleRepository
  ├── resourceObservableCache     ObservableCache<uuid, CrucibleResource>
  ├── resourceTypeObservableCache ObservableCache<uuid, String>          - "sample"/"dataset", for
  │                                                                        screens holding only a UUID
  ├── thumbnailObservableCache    ObservableCache<uuid, List<Thumbnail>>
  ├── projectsObservableCache     ObservableCache<Unit, List<Project>>   - member projects list
  ├── projectObservableCache      ObservableCache<projectReference, Project> - MFID-canonical per-project detail
  │                                                                        projects via discover-search
  ├── projectMembersObservableCache  ObservableCache<projectId, List<User>>
  ├── instrumentsObservableCache  ObservableCache<InstrumentStatus, List<Instrument>>
  ├── instrumentObservableCache   ObservableCache<instrumentMfid, Instrument> - MFID-canonical per-instrument detail
  ├── instrumentDatasetsObservableCache  ObservableCache<instrumentMfid, InstrumentDatasetPage>
  ├── projectSamplesObservableCache      ObservableCache<projectSlug, List<Sample>>
  ├── projectDatasetsObservableCache     ObservableCache<projectSlug, List<Dataset>>
  ├── myJoinRequestsObservableCache      ObservableCache<Unit, List<JoinRequest>>  - active account
  ├── pendingJoinRequestCountObservableCache  ObservableCache<projectId, Int>  - led projects only
  └── datasetFilesObservableCache ObservableCache<datasetUuid, List<AssociatedFile>>

PersistentProjectCache  (disk)  - server- and account-owned project summaries and selected project replicas
```

`projectMembersObservableCache` is fetched alongside the project itself in `ProjectDetailScreen`'s load effect and shared by the collapsing header's member count and `rememberOwnerNames`'s owner-groupby resolution, so `GET /projects/{id}/users` runs once per project. `myJoinRequestsObservableCache` backs both Account history and the non-member Project Detail status check. Failed refreshes preserve its last successful value, and `invalidateAll()` clears it during account or credential changes.

Instrument collections are cached independently for active, maintenance, and decommissioned status. `InstrumentListViewModel` switches among those keys without mixing responses, while dataset instrument pickers always request active fuzzy-search results. Home resolves pinned instrument MFIDs individually when they are absent from the active collection, so maintenance and decommissioned pins remain reachable.

Instrument dataset pages are keyed by the stable instrument MFID. The first 100 datasets load automatically, and each explicit Load more action follows one server cursor and merges that page into the cached collection without duplicate MFIDs. A refresh replaces the collection with a new first page. Project resource lists retain their full-fetch behavior. Assigned project requests use the stable project MFID with `project_scope=assigned`, then store the result under the current slug for compatibility with existing list navigation and persistence. The contextual `projectRelation` is removed before caching because it describes one request rather than the resource itself. Project Detail fetches `project_scope=shared` alongside assigned content, merges both collections by resource MFID with assigned records taking precedence, and keeps shared results in ViewModel state rather than synchronized or repository caches. Shared rows show the resource's actual assigned project and use that project slug for Crucible Web links.

Resource ACL rows use a bounded ten-minute `ObservableCache` keyed by resource MFID. Successful grant, revoke, publish, and unpublish mutations update that cache from canonical server responses. Public-access mutations also update the authoritative resource-detail entry in place instead of invalidating it, so linked-resource and metadata sections remain visible while the detail screen observes the change. ACL cache entries are memory-only, account-scoped by the repository cache epoch, and cleared by `invalidateAll()`.

`DataSyncManager` owns project summary persistence, selected-project restoration, full synchronization, and future delta application. Home and navigation-level refreshes converge on its single-flight overview request, while `CrucibleRepository` coalesces direct project and instrument list requests from other screens. Project-specific synchronization is serialized per project. The manager depends internally on the narrow `ProjectCacheStore` contract; production adapts it to `PersistentProjectCache` with a `PlatformContext`, while host tests use an in-memory implementation. Lightweight project, sample, and dataset summaries stay separate from authoritative detail entries, so bulk or future incremental sync cannot overwrite nullable detail fields such as resource relationships, metadata, or owners. Detail observers prefer authoritative entries and fall back to live or persisted summaries when no detail is available.

Persistent project files are keyed by a hash of normalized API server URL and stable account identity, so work from one server or account cannot replace another owner's replica. Selected projects, synchronization state, and persisted project contents are keyed by stable project MFID. Each replica also records the current project slug for display, navigation, and migration, while API collection filters use the stable project MFID. Legacy files without current ownership, identity, and version metadata are rejected and refreshed. Selected project contents and project summaries are stored atomically. Android uses `AtomicFile` inside the backup-excluded `project_cache` directory; iOS uses atomic `NSString.writeToFile` operations in the system Caches directory. Stopping sync removes that project's persisted lists, bulk selection retains only selected MFIDs, and sign-out, credential replacement, API server changes, or Cache settings can clear the complete disk tier. Thumbnails, associated files, member lists, full resource details, shared project-scope results, and signed download URLs are not persisted.

Every repository network read captures the current cache epoch before starting and writes only if that epoch remains current. Credential, account, API server, and explicit cache transitions advance the epoch before clearing memory. A late response may still return to its canceled caller, but it cannot repopulate the active cache. Disk restoration and synchronization use the same epoch check, and per-owner filenames prevent an old write from replacing another account or server's file.

### Incremental project sync contract

The app currently performs full sample and dataset refreshes because the API modification-time filters and deletion feed are not complete. The local replica is ready for incremental sync without adding speculative routes: each `CachedProjectContent` records the stable project MFID, current slug, last successful synchronization time, optional opaque delta cursor, full-refresh requirement, and sample/dataset deletion tombstones. `ProjectContentDelta` applies upserts and deletions together, rejects the wrong MFID or slug and deltas older than the replica, and retains tombstones until the next authoritative full refresh. A slug change for the same MFID forces an authoritative refresh and invalidates transient entries under the former slug.

When the API contract is available, one logical delta response must provide a stable cursor or modification watermark plus deleted sample and dataset identifiers. The client should request changes strictly after its stored cursor, apply the complete response atomically through `DataSyncManager.applyProjectDelta()`, and persist the new cursor only with the updated replica. Missing cursors, incompatible schema versions, invalid project identity, pagination discontinuity, or server reset must call `markProjectForFullRefresh()` and use the existing full-fetch path. A failed request leaves the previous replica and cursor intact.

Account-derived preferences are stored as one serialized `AccountPreferencesData` record per confirmed account identity. Profile data, history, last-visited resources, project sync selections, and project/instrument pins are unavailable while signed out and switch atomically when `activateAccount()` confirms a profile. Theme, typography, grouping, and other device-level presentation settings remain global. Legacy preference values migrate only when a stored legacy profile identifies the same account.

**`fetchFileUrl(mfid)` is the one deliberate non-cache.** Signed download URLs are always fetched
fresh - it's only called on a share/download tap, never from a preload, so there's no repeated read a
cache would help.

`invalidateAll()` clears every cache above in one call (logout, API key change, Cache settings'
"Clear All Cache" - which also clears `PersistentProjectCache` separately, since that tier isn't
owned here). `getCacheStats()` returns a snapshot for that same screen.

---

## ViewModels

Every screen that loads or mutates remote data has a commonMain ViewModel registered through `viewModelOf` in `di/AppModule.kt`. Screen-scoped instances resolve through `koinViewModel()`, while shared repositories and preferences resolve through Koin singletons.

`ProjectDetailViewModel` keeps assigned and shared project collections in separate source states and exposes them as one merged project view. Assigned content follows the repository and synchronization path with offline fallback. Shared content loads concurrently as a non-persisted supplement, and its sample and dataset results are retained independently when only one endpoint fails. MFID deduplication prefers the assigned record, and a failure in either source retains the other source with a retryable warning.

`ManageProjectViewModel` owns project-management loading, member and join-request retries, search errors, project ID changes, ownership transfer, role-aware member mutations, and mutation progress. Project member responses carry `viewer`, `contributor`, `editor`, `admin`, or `owner`. Exact project capabilities control editing, renaming, access management, transfer, and the highest selectable non-owner grant role. Project capability ceilings are contributor for editors, editor for admins, and admin for owners, making all same-role mutations unavailable; the capability-less compatibility fallback enforces the same strict hierarchy. Ownership remains transfer-only. The member's returned role remains useful for labels, target-specific constraints, and compatibility fallback only. API failures must remain distinct from valid empty member, request, or search results, and destructive confirmations remain open until the server confirms success. Member mutations replace the local list with the full list returned by V3 and invalidate the shared member cache.

Project member and resource ACL lists sort by descending authority - owner, admin, editor, contributor, viewer - and alphabetically within each role. Project owner remains the API and authorization value but is labeled Lead in project UI. The shared `RoleBadge` maps roles to paired Material 3 semantic container and content colors, so badges retain contrast under static, dark, and dynamic color schemes without fixed hue assumptions. Add-member and resource-access forms use the shared `RoleDropdownField`. Project member roles become `CompactRoleDropdown` controls only after the user enters the explicit Edit roles mode; each selection writes immediately through the single-member API, retains the prior role until success, and shows row-scoped progress or failure without simulating a batch save.

`ManageInstrumentViewModel` owns capability-gated descriptive editing, validated instrument-ID changes, lifecycle transitions, service-account operator bindings, and previewed ownership transfer. Lifecycle controls use the dedicated status route, offer only `active`, `maintenance`, and `decommissioned`, and require explicit confirmation before decommissioning. A successful transition invalidates the active-instrument list and writes the returned instrument into the canonical detail cache. Service-account operator selection uses debounced `/users/search` autocomplete with `is_service_account=true`, labels bindings as operators rather than exposing their internal group standing, requires confirmation before removal, and replaces local state with each mutation's complete returned list. Instrument ownership is read-only during descriptive editing and changes only through the shared transfer workflow. Project and instrument management reuse `ResourceIdRenameDialog` and the ownership picker, progress, and confirmation components from `ui/common/ResourceManagementDialogs.kt`. Both preserve the editable human-facing slug separately from the display title or name and keep navigation keyed by MFID.

`ServiceAccountsViewModel` owns the capability-gated administrator list, retained pull-to-refresh state, cancellable detail selection, creation, platform-role update, and key-rotation states. The screen uses the shared role dropdown and empty-list presentation. One-time credentials exist only in ViewModel memory until the user dismisses the credential dialog. The screen never writes them to preferences, repositories, logs, or disk.

`CreateInstrumentViewModel` owns the Register Instrument form and submits only the V3 creation fields. Display name seeds a locally validated instrument ID until the user edits that ID manually. Name, ID, and location are required; descriptive fields remain optional. Ownership is omitted so the API assigns the signed-in human, and ownership transfer remains a separate Manage Instrument operation. The Instruments screen hides registration from service accounts. Successful creation caches the canonical MFID detail, invalidates status-filtered instrument collections, refreshes the retained Instruments screen, and opens Manage Instrument.

Project navigation accepts a slug only at external or resource-display boundaries. MFIDs use the canonical singleton route directly; slugs use `GET /projects?project_id=` and are converted to the returned MFID before entering Project Detail. The project detail cache is keyed only by MFID and never stores a second slug alias. Project web links continue to use the current slug because that is the human-facing URL.

Instrument navigation follows the same canonical boundary. MFIDs use the singleton route directly; slugs use `GET /instruments?instrument_id=` and are converted to the returned MFID before entering Instrument Detail. The instrument detail cache is keyed only by MFID. Dataset instrument links prefer the embedded instrument MFID and use the legacy flat instrument ID only when the embedded reference is absent.

The global QR scanner accepts raw sample or dataset identifiers and trusted `https://crucible.lbl.gov/explore/...` project, sample, and dataset links. `ScannerViewModel` parses only those known forms, forces an authenticated repository lookup, and navigates only with the canonical MFID returned by the API. Invalid, inaccessible, and missing targets remain on the scanner with an explicit retry action. The Link Resource scanner uses the same parser, accepts only sample or dataset targets, and passes the extracted identifier through its existing authenticated resource lookup before enabling any link operation.

Supported sample, dataset, and instrument reads request `include_owner=true`; project responses already include the resolved public lead without an owner flag. Sample and dataset creation submit `project_mfid` when the selection resolves to a project. Dataset creation similarly submits `instrument_mfid` for a registered instrument. Legacy slug and free-text fields remain compatibility fallbacks only when an old source record cannot resolve a canonical identity.

`LinkResourceViewModel` owns the Link Resource sheet's debounced name search, direct UUID resolution, and link mutation. Search failures remain distinct from valid empty results, partial search results retain a warning, and resolved targets remain available when link submission fails.

`SearchViewModel` owns global name, filter, facet-suggestion, and scientific-metadata searches. It uses `collectLatest` to cancel stale criteria, runs independent category endpoints concurrently, preserves successful categories during partial failures, and reads People and Project result limits directly from `AppPreferences`. Sample and dataset collection calls use their own typed query objects with shared typed visibility and affiliation options, while anchored sibling calls use a typed direction. Filter ownership uses the stable `owner_id`; owned-by-me uses `affiliation=owner`; visibility and missing-value switches map directly to the collection query contract; picker date-time values are normalized to explicit UTC instants before collection requests. Measurement, data format, session, and sample type facets load concurrently when the filter sheet opens, retain successful categories on partial failure, and populate editable suggestion fields. Global name search remains unscoped. Link Resource search uses the source resource's expanded project MFID with `project_scope=assigned`, falling back to its legacy project slug only when an old cached record lacks the expanded reference. This preserves the existing assigned-project result boundary across project slug changes.

`UserProfileViewModel` backs `UserProfileScreen`'s "Add to Project" flow. Username profile navigation resolves through the canonical exact `GET /users?username=` collection filter and rejects missing or non-unique results without calling a compatibility singleton route. The ViewModel holds the viewed user, the current user's cache-backed project list, `ProjectMembershipState`, and `AddToProjectState`. Membership checks run in parallel when the sheet opens, retain verified projects during partial failures, disable unknown projects, and retry only failed project IDs. Add-member failures remain visible and retryable, while success invalidates the shared member cache and updates the verified membership snapshot.

**`AccountViewModel` is reused as-is by `CompleteProfileScreen`** (`ui/settings/CompleteProfileScreen.kt`),
not duplicated into a second profile-editing ViewModel - `startEdit()`/`editState`/`saveProfile()`
are exactly `AccountScreen`'s own edit mode, entered automatically and rendered full-screen. Both
screens share the field UI (`ProfileEditFields`) and the username format regex (`USERNAME_PATTERN`,
in `AccountViewModel.kt`). `NavGraph.kt` gates every screen (deep links included) with a top-level
`LaunchedEffect(apiKey, userProfile)`: if the signed-in user's profile has no username or email, it
navigates to `Screen.CompleteProfile`, whether the sign-in was ORCID or a pasted API key (both funnel
into the same `userProfile` flow this effect watches). "Skip for now" sets
`ProfileCompletionGate.skippedThisLaunch` (a plain in-memory object, same convention as
`DuplicateHolder`/`MetadataHolder` - never persisted, so the prompt reappears every launch until a
username is saved).

Most expose a single `StateFlow<LoadState<T>>` (`ui/common/LoadState.kt`) rather than separate
loading/error/data/refreshing flags. Two exceptions:

`LoadState.Success` can carry a `refreshError` while retaining its existing data. Use it when a refresh failure should remain visible and retryable without replacing already loaded content with a full-screen error.

**`HomeViewModel`** exposes project summaries and foreground refresh errors. It restores summaries through `DataSyncManager` and delegates overview refreshes to the manager instead of starting its own project preload. `ResourceDetailViewModel.startBackgroundSync()` remains the session-level owner of selected-project synchronization and exposes `isSyncing` for Home's progress indicator.

**`ResourceDetailViewModel`** drives the detail pager:

- `uiState: StateFlow<UiState>` - `Idle | Loading | Success(uuid, isRefreshing) | Error(message)`.
  `Success` carries **only the uuid**: the screen and every pager page read the resource and
  thumbnails from `CrucibleRepository.observeResource(uuid)`/`.observeThumbnails(uuid)` directly, so
  there is no second copy of resource state to keep in sync.
- `deletionRequestSubmissionState: StateFlow<DeletionRequestSubmissionState>` owns deletion-request progress and errors so `DeletionRequestDialog` remains presentation-only.
- `associatedFileActionStates: StateFlow<Map<AssociatedFileActionKey, AssociatedFileActionState>>` resolves uncached signed URLs independently for each dataset, file, and Download or Share action. Compose launches the platform browser or share sheet only after a URL is ready, then clears that action state.
- `thumbnailMutationStates: StateFlow<Map<ThumbnailMutationKey, ThumbnailMutationState>>` owns dataset-scoped update and deletion progress, retryable errors, and cache refreshes while preventing overlapping writes to the same thumbnail.
- `isSyncing: StateFlow<Boolean>` - true while `DataSyncManager.syncAll()` runs (drives the home spinner).
- `fetchResource(uuid)` shows the cached version immediately, then always fetches fresh.
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
`isRefreshing` tracks only the navigated-to resource).

Either way, set the flag before the coroutine's work and clear it in a `finally` block, so the
spinner appears immediately and always clears, including on error. Content does **not** move during
the pull; the indicator overlays it, matching M3 and iOS `UIRefreshControl`.

---

## Navigation (`ui/navigation/`)

`Screen` is a sealed class of route strings; optional args use `?argName={argName}`, and special characters in segments go through `encodeRouteSegment()`. Treat `Screen.kt` as the route inventory rather than duplicating a count here.

---

## ResourceDetailScreen pager

Siblings are resources of the same type within the same project. A cached project collection remains authoritative. On a cache miss, name-sorted groups request bounded ascending and descending pages from the current resource through `anchor_mfid`, merge them by MFID, and keep date groups on the full collection path because the UI date group is based on the scientific timestamp rather than API creation time.

- Plain bounded pager: `pageCount = siblingList.size`, `initialPage = siblingIndex`, so it opens at
  the right position. No virtual `Int.MAX_VALUE` count, no wrap-around. A `LaunchedEffect` scroll only
  covers the cold-start case where the sibling list wasn't resolved yet.
- **No manual preload or eviction windows.** Each page is `key(pageUuid)`'d and self-contained: it observes `observeResource(pageUuid)`/`.observeThumbnails(pageUuid)` and runs its own `LaunchedEffect(pageUuid) { repository.fetchResourceByUuid(pageUuid) }`. `HorizontalPager` decides which pages exist; `ObservableCache` applies freshness and bounded recency eviction. Earlier versions kept `loadedResources`/`enrichedUuids`/`failedEnrichmentUuids` maps and ±N distance math, which caused repeated stale- and flashing-content bugs - don't reintroduce them.
- A page renders its lightweight sibling-list stub immediately and swaps in the enriched resource in
  place, so there's no per-page spinner or flash. A page-local `enrichmentFailed` flag marks a failure.
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
benefit, and `koinInject` still gives them a real, swappable dependency. Don't half-thread callbacks
through call sites to "fix" this.

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
| `FormatUtils.kt` | File size and date formatting plus centralized full, compact, and handle-based user identity labels |
| `CryptoUtils.kt` | `PlatformCrypto.sha256Hex()` (expect/actual) for upload dedup |

`fetchProjectData(projectMfid, projectSlug)` performs parallel assigned sample and dataset fetches behind a per-project MFID mutex. It is a method on `CrucibleRepository`, not a standalone utility. `DuplicateHolder.kt` lives in `ui/create/` rather than here - it is an in-memory clipboard for the duplication flow, kept next to the screens it serves.

---

## Preferences (`data/preferences/AppPreferences.kt`)

A platform-agnostic interface backed by DataStore on Android and NSUserDefaults on iOS. Every exposed value is a `StateFlow`.

| Scope | Values | Storage |
|---|---|---|
| Credential | API key | `SecureCredentialStore`: Android Keystore-backed AES-GCM or an iOS device-only Keychain item. The legacy `api_key` preference is removed only after verified migration. |
| Account selection | Active account ID | Global preference used to select a hashed `account_preferences_{sha256}` record. Stable identity preference is ORCID, username, then email. |
| Account-specific | User profile and ORCID, last visited resource, pinned and synced projects, sync setup, pinned and hidden instruments, resource history | One serialized `AccountPreferencesData` record per account. Switching or signing out replaces the active flows so data cannot leak between accounts. |
| App-wide | API and Graph Explorer URLs, theme mode, accent and contrast, dynamic color, floating scan button, group-by settings, default project tab, and search result limits | Platform preference store, shared across accounts. |

Pinned and synced projects are stored by stable project MFID. When an authoritative project list is available, recognized legacy slug selections are replaced with their MFIDs. Unresolved synced selections are removed because their contents cannot be refreshed, while unresolved pins remain available for a later authoritative refresh.

`HistoryItem` stores `uuid`, `name`, `timestamp`, optional `resourceType`, and optional `projectId`. The project ID is recorded when the resource is viewed rather than inferred from cache state during rendering.

---

## Testing

```bash
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:testAndroidHostTest
```

`scripts/verify-change.sh` is the tool-neutral local gate for Android compilation and host tests. Claude Code's `.claude/hooks/pre-commit-check.sh` invokes it before Bash-based commits, and `scripts/release.sh` runs the same checks in its verify step.

Build CI on tag only is deliberate. `.github/workflows/release.yml` fires on a `v*.*.*` tag or `workflow_dispatch` because it is too slow for every push or PR. The lightweight `.github/workflows/api-contract.yml` check runs daily or manually against the deployed OpenAPI document, using `scripts/check-api-contract.sh`; the same script accepts a local OpenAPI file for deterministic checks.

**Layout**: `app/src/commonTest/kotlin/`, mirroring the production package path
(`data/cache/ObservableCacheTest.kt` tests `data/cache/ObservableCache.kt`). Platform-agnostic, so
one suite covers both targets.

**Dependencies** (`commonTest` block in `app/build.gradle.kts`): `kotlin("test")`, `kotlinx-coroutines-test`, and Ktor's official `ktor-client-mock`. There is no general-purpose mocking library.

### What is covered

| Suite | Covers |
|---|---|
| `ObservableCacheTest` | Freshness expiry, stale-visible `peek` and `observe`, read/write recency eviction at capacity, atomic concurrent writes, invalidation, and `ageMillis` |
| `CacheEpochTest` | Rejection of writes captured before a cache-scope transition |
| `CrucibleRepositoryTest` | Cache-miss fallbacks, persisted and live summary precedence, stale restoration rejection, full-detail preservation across summary seeding and mutation responses, and `fetchSiblings` fallbacks |
| `CrucibleRepositoryNetworkTest` | Delayed responses across cache-scope changes, single-flight HTTP request coalescing, canonical MFID project and instrument routing, exact slug-filter routing, MFID-only detail caching, and ACL mutation consistency through Ktor `MockEngine` |
| `CrucibleApiServiceV3WriteTest` | V3-safe update payloads, instrument-reference response fields, and project-move and ownership-transfer preview and confirmation requests |
| `CrucibleApiServiceResourceAccessTest` | Sample and dataset capability decoding plus canonical resource ACL models, routes, HTTP methods, and authentication |
| `CrucibleApiServiceUserLookupTest` | Canonical username-filter routing plus zero, one, and multiple exact-result handling |
| `CrucibleApiServiceProjectLookupTest` | Exact project-slug zero and multiple result handling |
| `CrucibleApiServiceInstrumentLookupTest` | Exact instrument-slug zero and multiple result handling |
| `CrucibleApiServiceInstrumentDatasetsTest` | Canonical instrument-MFID filtering and bounded cursor-page requests |
| `CrucibleApiServiceInstrumentAccessTest` | Instrument-scoped service-account binding routes and service-account-only exact lookup |
| `CrucibleApiServiceInstrumentCreateTest` | V3 instrument registration route, required payload fields, self-owner omission, and response parsing |
| `CrucibleApiServiceOwnerExpansionTest` | Expanded-owner query coverage for typed sample, dataset, and instrument detail and list reads |
| `CrucibleApiServiceCurrentContractTest` | Stable owner filters, richer collection filters, anchored sibling requests, cursor-paginated facets, service-account administration, thumbnail updates, canonical create identifiers, dataset instrument assignment, and degraded readiness diagnostics |
| `CrucibleApiServiceProjectScopeTest` | Assigned and shared project-scope routing, cursor pagination, and query compatibility |
| `ProjectContentMergeTest` | Assigned and shared project resource deduplication, assigned-record precedence, shared-row marking, and partial-source failure retention |
| `ThumbnailMutationStateTest` | Dataset- and operation-scoped thumbnail mutation progress and errors |
| `DateTimeUtilsTest` | Offset and timezone-less filter timestamp normalization plus invalid-input preservation |
| `ResourceSlugTest` | Shared project and instrument ID grammar plus project-only reserved values |
| `FormatUtilsTest` | `formatDateTime` timezone conversion, missing-offset fallback, compact AM/PM, null and unparseable input |
| `ResourceLinkOperationTest` | Parent-child direction mapping, cross-type dataset-sample mapping, and self-link rejection |
| `ResourceAccessTest` | Writable ACL principal routing, read-only grant types, and maximum grant-role enforcement |
| `SearchCoverageTest` | Complete, partial, and total multi-endpoint search failure classification |
| `InstrumentDetailStateTest` | Instrument and dataset error classification plus retained-content refresh failures |
| `CreateInstrumentStateTest` | Generated instrument IDs, required-field validation, and expected V3 registration errors |
| `ProjectMembershipStateTest` | Complete, partial, failed, and retried project membership resolution plus add-member error classification |
| `AssociatedFileActionStateTest` | Independent Download and Share state, scoped clearing, and signed-link error classification |
| `SyncSuggestionsTest` | Which projects are suggested (led, pinned, the union without duplicates) and their sort order |
| `AccountPreferencesDataTest` | Account-record serialization, corruption fallback, storage-key isolation, and stable identity selection |
| `PersistentProjectCacheTest` | Account and server isolation, schema migration, serialization corruption, retention, ordered delta application, tombstones, and full-refresh fallback |
| `SingleFlightTest` | Concurrent request coalescing and recovery after a failed shared request |
| `DataSyncManagerNetworkTest` | Atomic sample/dataset publication, partial-failure replica retention, retry, delta rejection, and authoritative full-refresh fallback |
| `SecureCredentialManagerTest` | Credential migration, migration verification, cleanup, and failure handling |

Pure state transitions extracted from `ui/` are covered by host tests. There is no instrumented, Compose UI, screenshot, or ViewModel test suite.

### Patterns to follow

- **Inject time rather than sleeping.** `ObservableCacheTest`'s `cacheWithClock(ttl, maxSize, clock)` helper passes a fake `() -> Long`, so TTL tests are instant and deterministic. Never use a real delay to cross an expiry boundary.
- **`runTest` for anything `Flow`-shaped**, which is every `observe*` test.
- **Assert against recomputed values, not hardcoded output**, wherever the environment can vary.
  `FormatUtilsTest` derives the expected local hour the way production code does, so it passes in any
  timezone. A hardcoded `"2:32 PM"` would pass only on the machine that wrote it.
- **Use native test boundaries.** Repository HTTP tests construct `ApiClient` with Ktor `MockEngine`, preserving the production client configuration and serialization path. Synchronization tests use an in-memory `ProjectCacheStore` so they can exercise the common orchestration without Android or iOS filesystem APIs. Do not introduce a general mocking framework for these paths.

### When to add one

Add a test when you add or change **pure logic in `data/`** - cache and TTL behaviour, formatting,
sorting, grouping, search matching, or any pure function with branches. Cheap to test, and three of
the four existing suites exist because the logic they cover broke once (`FormatUtilsTest`'s first
case documents a timezone bug it guards against).

Do not add a test for a screen or ViewModel until a suitable harness exists. Add API-path tests when repository caching, request coordination, error classification, serialization, or synchronization behavior depends on the response; use `MockEngine` rather than a live service.

---

## Common gotchas

**Plain `remember` doesn't survive navigating away and back.** Navigation-Compose only composes the
top of the back stack, so pushing a destination fully disposes the composable underneath it; on
`popBackStack()` it recomposes from scratch and any plain `remember`ed value silently resets.
Screen-level state that must survive a push-and-pop round trip needs `rememberSaveable` - scroll
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

- iOS has no launch screen. Universal Link deployment still requires the Apple Team ID and the server-side AASA file described in `dev/platform-parity.md`.
