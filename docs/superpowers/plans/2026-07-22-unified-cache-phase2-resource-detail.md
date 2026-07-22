# Unified Cache — Phase 2: ResourceDetailScreen uuid-based reactive rewrite

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `ResourceDetailScreen`, `ResourceDetailViewModel`, and the `Screen.Detail` route in `NavGraph` so the screen is driven by a `uuid: String` instead of a `resource: CrucibleResource` object, observing `CrucibleRepository`'s reactive `observeResource`/`observeThumbnails` flows for render data. This structurally eliminates the flash-bug class fixed ad hoc in commit `ce03f1c` (object-identity-keyed Compose state colliding with the cache-placeholder → network-result transition) by removing the full resource object as a parameter anywhere in this screen's tree — there is nothing left to accidentally key `remember`/`LaunchedEffect` on except stable uuid strings. `ResourceDetailCache.kt` (the hand-rolled `SnapshotStateMap`/`enrichedUuids` bookkeeping) is deleted; "enriched" becomes a derived read (`resource?.links != null`) instead of tracked state.

**Architecture:** `ResourceDetailViewModel.UiState.Success` shrinks to `data class Success(val uuid: String, val isRefreshing: Boolean = false)` — the ViewModel's job is purely "did fetching this uuid succeed/fail/is in flight," not holding resource data. `ResourceDetailScreen` takes `uuid: String` and observes `repository.observeResource(uuid)` for its own render data; each pager page independently observes `repository.observeResource(pageUuid)` and `repository.observeThumbnails(pageUuid)` for its own data, so pages update independently and automatically whenever the repository's cache changes — no manual per-page map-writing, no manual cross-page propagation. The windowed enrichment loop becomes "call `repository.fetchResourceByUuid(uuid)` for each page in range if not already enriched" with no bookkeeping beyond that call. Sibling-list loading moves out of the composable into `CrucibleRepository.fetchSiblings(...)`, using Phase 1's `observeProjectSamples`/`observeProjectDatasets` as the cache-hit path.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`collectAsStateWithLifecycle` via `androidx.lifecycle:lifecycle-runtime-compose`, already a `commonMain` dependency), Koin (`koinViewModel`, `koinInject`), `kotlinx.coroutines.flow` (`Flow`, `combine`).

## Global Constraints

- This plan assumes **Phase 1 is complete and merged** — `CrucibleRepository` already has `observeResource`, `getCachedResource`, `invalidateResource`, `resourceAgeMillis`, `observeProjectSamples`, `observeProjectDatasets`, `observeInstrumentDatasets`/`fetchInstrumentDatasets` (from `docs/superpowers/plans/2026-07-22-unified-cache-phase1-foundation.md`). If any of those methods do not exist when this plan's implementation starts, stop and re-verify Phase 1 landed correctly before proceeding.
- `CacheManager` is still used elsewhere in the app during this phase (`HomeScreen.kt`, `ProjectsListScreen.kt`, etc. — migrated in Phase 3). This plan only removes `CacheManager` usage from the specific files it touches: `ResourceDetailScreen.kt`, `ResourceDetailViewModel.kt`, and the `Screen.Detail` route block inside `NavGraph.kt`.
- No `Date.now()`/`Math.random()` — not needed in this phase (no new timestamp logic).
- Every new suspend function that can throw follows the codebase's established `catch (e: CancellationException) { throw e }` before a general `catch`.
- `AppIcons`, `AppScaffold`, `AppTopBar`, `AppAnimations` spring/duration constants are used exactly as they exist today — no new UI primitives introduced.
- Icon/animation/motion conventions from `CLAUDE.md` apply unchanged: `AppIcon(AppIcons.X)`, `ExpandChevron`, spring specs from `AppAnimations.kt`.
- Do not modify `BasicInfoCard.kt`, `SampleDetailsCard.kt`, `DatasetDetailsCard.kt`, `ThumbnailsSection.kt`, `EditResourceSheet.kt`, `LinkResourceSheet.kt`, `DeletionRequestDialog.kt`, `QrCodeDialog.kt`, or any card component in `ui/detail/components/` — they already take domain objects (`CrucibleResource`/`Sample`/`Dataset`) as parameters and are opened as one-shot leaf sheets/dialogs, not persistent pager state; they need no changes.
- Do not modify `HomeScreen.kt`, `ProjectsListScreen.kt`, `InstrumentListScreen.kt`, or any ViewModel other than `ResourceDetailViewModel` — that is Phase 3.

## File Structure

- **Delete**: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailCache.kt` — its `SnapshotStateMap`/`enrichedUuids`/`failedEnrichmentUuids`/`loadedThumbnails` bookkeeping is replaced by direct repository observation plus page-local `remember` state.
- **Rewrite**: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailViewModel.kt` — `UiState` drops `resource`/`thumbnails` fields; `fetchResource`/`refreshResource` become uuid-only cache invalidation + repository delegation; `resourceDetailCache`, `loadedResources`, `enrichedUuids`, `failedEnrichmentUuids`, `loadedThumbnails`, thumbnail helper methods, and `preloadRelatedResources` are removed (their jobs are now the repository's).
- **Add**: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt` — new `observeThumbnails(uuid)`/`fetchThumbnails` (already exists, kept)/`invalidateThumbnails(uuid)` backed by a new `thumbnailObservableCache`, and a new `fetchSiblings(resource, groupBy)` method absorbing `ResourceDetailScreen`'s `loadSiblings` logic.
- **Rewrite**: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailScreen.kt` — signature changes from `resource: CrucibleResource` to `uuid: String`; internal sibling/pager/enrichment logic rewritten around reactive per-page observation.
- **Modify**: `app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt` — the `Screen.Detail` composable block passes `mfid` to `ResourceDetailScreen` instead of `state.resource`; `UiState.Success`'s `contentKey`/history-saving logic adjusts to the new `UiState.Success(uuid, isRefreshing)` shape.

---

### Task 1: Add thumbnail and sibling-list methods to `CrucibleRepository`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` (Phase 1), existing `CrucibleApiService.getFilteredSamples`/`getFilteredDatasets` (unchanged signatures, `CrucibleApiService.kt:297-339`), existing `ensureContains`/`filterSiblings` logic (currently private top-level functions in `ResourceDetailScreen.kt:105-125`, moved into the repository in this task).
- Produces:
  - `fun observeThumbnails(uuid: String): Flow<List<Thumbnail>?>`
  - `fun invalidateThumbnails(uuid: String)`
  - `suspend fun fetchSiblings(resource: CrucibleResource, groupBy: String?): List<CrucibleResource>` — cache-then-network sibling list resolution, replacing `ResourceDetailScreen.loadSiblings()`'s logic. Returns the resolved, sorted, `ensureContains`-applied list (same contract `loadSiblings` had, but returned instead of mutated into local `var`s).
  - (internal) `private val thumbnailObservableCache = ObservableCache<String, List<Thumbnail>>(ttlMillis = 10 * 60 * 1000L, maxSize = 20)` (same TTL/size as `CacheManager.MAX_THUMBNAIL_CACHE_SIZE` today)
  - `fetchThumbnails(datasetUuid: String): List<Thumbnail>` (already exists, unchanged signature) is updated internally to also populate `thumbnailObservableCache` on success.

The existing `fetchThumbnails` (`CrucibleRepository.kt:81-92`) doesn't cache at all today — caching happens in the caller (`ResourceDetailViewModel.fetchAndCacheThumbnails`, `CacheManager.cacheThumbnails`). This task makes `fetchThumbnails` itself cache-aware (check `thumbnailObservableCache` first, populate on network fetch), matching the shape of every other `fetchX` method in the repository, so `ResourceDetailViewModel` and `ResourceDetailScreen` (Task 3/4) no longer need to manage thumbnail caching themselves.

`fetchSiblings` moves the two private top-level functions `ensureContains`/`filterSiblings`/`monthBounds`-based date-bounds logic from `ResourceDetailScreen.kt` into the repository as private helpers, since sibling resolution is now a data-layer concern, not a UI concern.

- [ ] **Step 1: Write the failing tests**

Add to `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` (after the last test from Phase 1's Task 6):

```kotlin
    @Test
    fun observeThumbnailsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeThumbnails("dataset-uuid").first())
    }

    @Test
    fun invalidateThumbnailsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateThumbnails("dataset-uuid")
    }

    @Test
    fun fetchSiblingsReturnsSingleItemListWhenResourceHasNoProjectId() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        val sample = Sample(uniqueId = "s1", sampleName = "Sample 1", projectId = null)
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(listOf(sample), result)
    }

    @Test
    fun fetchSiblingsUsesProjectCacheWhenAvailable() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        val sampleA = Sample(uniqueId = "s1", sampleName = "A", projectId = "p1", sampleType = "TypeX")
        val sampleB = Sample(uniqueId = "s2", sampleName = "B", projectId = "p1", sampleType = "TypeX")
        val sampleOtherType = Sample(uniqueId = "s3", sampleName = "C", projectId = "p1", sampleType = "TypeY")
        // Populate the project-samples cache the same way fetchProjectData would (via the
        // CacheManager constructor param is not used for this path in Phase 1 — projectSamples
        // are cached internally in CrucibleRepository, so populate via the public API: this
        // requires a cache hit path, which this test simulates by calling fetchProjectData
        // is not possible without network. Instead, this test is skipped in favor of verifying
        // the no-project-id and default-groupBy-filter behavior only, since project-cache
        // pre-population requires either a network mock (not available, see Phase 1 Task 3
        // preamble) or exposing a test-only seed method (out of scope for this plan).
        assertEquals(listOf(sampleA), repository.fetchSiblings(sampleA, groupBy = null).let {
            // Without a cached or fetchable project list, network call to getFilteredSamples
            // will fail in a JVM unit test (no real HTTP server) and fall through — this
            // exercises the "network unavailable" fallback path, not the cache-hit path.
            it
        })
    }
```

Note: the third test (`fetchSiblingsUsesProjectCacheWhenAvailable`) as drafted above cannot actually pre-populate the project cache without either a network mock or a test-only seam — this is a known limitation flagged explicitly in the test body rather than silently skipped. Replace it with a simpler, fully-deterministic version:

```kotlin
    @Test
    fun fetchSiblingsFallsBackToSingleItemWhenNetworkUnavailable() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        val sample = Sample(uniqueId = "s1", sampleName = "A", projectId = "p1", sampleType = "TypeX")
        // No project cache populated and no real network in this JVM unit test — getFilteredSamples
        // will throw or return an error; fetchSiblings must not propagate that as a crash, and must
        // still return a list containing at least the resource itself.
        val result = repository.fetchSiblings(sample, groupBy = null)
        assertEquals(true, result.any { it.uniqueId == "s1" })
    }
```

Use this second version instead of the "uses project cache" test above — delete the first (unusable) draft entirely from the test file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — `observeThumbnails`/`invalidateThumbnails`/`fetchSiblings` do not exist yet.

- [ ] **Step 3: Implement `observeThumbnails`/`invalidateThumbnails` and cache-aware `fetchThumbnails`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add a new field directly after `instrumentDatasetsObservableCache` (from Phase 1 Task 6):

```kotlin
    private val thumbnailObservableCache = ObservableCache<String, List<Thumbnail>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 20
    )
```

Replace the existing `fetchThumbnails` method:

```kotlin
    suspend fun fetchThumbnails(datasetUuid: String, forceRefresh: Boolean = false): List<Thumbnail> = withContext(Dispatchers.Default) {
        if (!forceRefresh) {
            thumbnailObservableCache.get(datasetUuid)?.let { return@withContext it }
        }
        try {
            when (val result = api.getThumbnails(datasetUuid)) {
                is ApiResult.Success -> result.data.also { thumbnailObservableCache.put(datasetUuid, it) }
                is ApiResult.Error -> emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observeThumbnails(uuid: String): Flow<List<Thumbnail>?> = thumbnailObservableCache.observe(uuid)

    fun invalidateThumbnails(uuid: String) = thumbnailObservableCache.invalidate(uuid)
```

Note: `fetchThumbnails` gains a `forceRefresh` parameter (defaulted `false` so existing behavior for any caller that doesn't pass it is unchanged) — Task 3 uses `forceRefresh = true` for `refreshThumbnails`.

- [ ] **Step 4: Implement `fetchSiblings`**

Add the required imports at the top of `CrucibleRepository.kt` (alongside the existing imports):

```kotlin
import crucible.lens.data.util.monthBounds
```

Add two private helper functions at the bottom of the file, outside the `CrucibleRepository` class body (moved verbatim from `ResourceDetailScreen.kt:105-125`, `ensureContains` generalized to a top-level private fun exactly as it is today):

```kotlin
private fun <T : CrucibleResource> List<T>.ensureContains(resource: T): List<T> =
    if (any { it.uniqueId == resource.uniqueId }) sortedBy { it.uniqueId }
    else (this + resource).sortedBy { it.uniqueId }

private fun List<Sample>.filterSiblings(groupBy: String?, resource: Sample) =
    filter { s -> when (groupBy) {
        "DATE"  -> crucible.lens.data.util.dateGroupKey(s.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "OWNER" -> s.ownerOrcid == resource.ownerOrcid
        else    -> s.sampleType == resource.sampleType
    } }.sortedBy { it.uniqueId }

private fun List<Dataset>.filterSiblings(groupBy: String?, resource: Dataset) =
    filter { d -> when (groupBy) {
        "INSTRUMENT" -> d.instrumentName == resource.instrumentName
        "DATE"       -> crucible.lens.data.util.dateGroupKey(d.timestamp) == crucible.lens.data.util.dateGroupKey(resource.timestamp)
        "FORMAT"     -> d.dataFormat == resource.dataFormat
        "SESSION"    -> d.sessionName == resource.sessionName
        "OWNER"      -> d.ownerOrcid == resource.ownerOrcid
        else         -> d.measurement == resource.measurement
    } }.sortedBy { it.uniqueId }
```

Add the `fetchSiblings` method to the `CrucibleRepository` class body, after `invalidateInstrumentDatasets` (from Phase 1 Task 6):

```kotlin
    /**
     * Resolves the sibling list for [resource] within its project, applying [groupBy]
     * filtering. Cache-first via the project sample/dataset list cache; falls back to a
     * filtered API call scoped to the group when no cached project list is available.
     * Always returns a list containing at least [resource] itself.
     */
    suspend fun fetchSiblings(resource: CrucibleResource, groupBy: String?): List<CrucibleResource> {
        return when (resource) {
            is Sample -> {
                val projectId = resource.projectId ?: return listOf(resource)
                val cached = projectSamplesObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource)
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredSamples(
                            projectId = projectId,
                            sampleType = if (groupBy == null || groupBy == "TYPE") resource.sampleType else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> resp.data.ensureContains(resource)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
            is Dataset -> {
                val projectId = resource.projectId ?: return listOf(resource)
                val cached = projectDatasetsObservableCache.get(projectId)
                if (cached != null) {
                    cached.filterSiblings(groupBy, resource).ensureContains(resource)
                } else {
                    val bounds = if (groupBy == "DATE") monthBounds(resource.timestamp) else null
                    when (val resp = withContext(Dispatchers.Default) {
                        api.getFilteredDatasets(
                            projectId = projectId,
                            measurement = if (groupBy == null || groupBy == "MEASUREMENT") resource.measurement else null,
                            instrumentName = if (groupBy == "INSTRUMENT") resource.instrumentName else null,
                            dataFormat = if (groupBy == "FORMAT") resource.dataFormat else null,
                            sessionName = if (groupBy == "SESSION") resource.sessionName else null,
                            ownerOrcid = if (groupBy == "OWNER") resource.ownerOrcid else null,
                            creationTimeGte = bounds?.first, creationTimeLte = bounds?.second
                        )
                    }) {
                        is ApiResult.Success -> resp.data.ensureContains(resource)
                        is ApiResult.Error -> listOf(resource)
                    }
                }
            }
        }
    }
```

Note: unlike `ResourceDetailScreen.loadSiblings()`'s original behavior (which set `siblingsResolved = true` even on error, but left the stale 1-item list in place), `fetchSiblings` on `ApiResult.Error` explicitly returns `listOf(resource)` — equivalent behavior (the screen will show just the current resource, `siblingsResolved` flips true), expressed as a return value instead of two side-effecting `var` writes.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`. `fetchSiblingsFallsBackToSingleItemWhenNetworkUnavailable` and `fetchSiblingsReturnsSingleItemListWhenResourceHasNoProjectId` must pass without a live network connection — confirm this by checking the test output does not hang (a JVM unit test with no mocked Ktor engine will attempt a real network call to `https://crucible.lbl.gov/...` inside `getFilteredSamples` and fail fast with a connection error, which the existing `try/catch` around `safeCall` in `CrucibleApiService.kt` converts to `ApiResult.Error` — this is expected and is what the test exercises).

- [ ] **Step 6: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` — `fetchThumbnails`'s new `forceRefresh` parameter has a default, so its one existing caller (`ResourceDetailViewModel.fetchAndCacheThumbnails`) still compiles unchanged at this point (it is rewritten in Task 3, not yet).

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: add observeThumbnails and fetchSiblings"
```

---

### Task 2: Delete `ResourceDetailCache.kt`

**Files:**
- Delete: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailCache.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing — this is a pure deletion. `ResourceDetailViewModel.kt` and `ResourceDetailScreen.kt` still reference `ResourceDetailCache` at this point in the plan (Tasks 3 and 4 remove those references), so this task **will leave the codebase non-compiling until Task 4 completes**. This is intentional and matches the plan's task ordering: Task 3 rewrites the ViewModel to stop producing a `ResourceDetailCache`, and Task 4 rewrites the screen to stop consuming one. Deleting the file first makes every remaining reference a compile error, which is a more reliable way to find every call site than grepping.

- [ ] **Step 1: Delete the file**

```bash
git rm app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailCache.kt
```

- [ ] **Step 2: Confirm the expected compile failures**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: compilation failure listing unresolved references to `ResourceDetailCache` in `ResourceDetailViewModel.kt` and `ResourceDetailScreen.kt`. This is expected — do not attempt to fix it in this task. Do not commit this task on its own; its deletion is committed together with Task 3's changes (see Task 3 Step 5) so the repository never has a broken intermediate commit.

---

### Task 3: Rewrite `ResourceDetailViewModel`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailViewModel.kt`

**Interfaces:**
- Consumes: `CrucibleRepository.fetchResourceByUuid`, `.observeResource`, `.getCachedResource`, `.invalidateResource`, `.fetchThumbnails`, `.invalidateThumbnails` (all from Phase 1 + Task 1), `DataSyncManager.syncAll()` (unchanged).
- Produces:
  ```kotlin
  sealed class UiState {
      object Idle : UiState()
      object Loading : UiState()
      data class Success(val uuid: String, val isRefreshing: Boolean = false) : UiState()
      data class Error(val message: String) : UiState()
  }

  class ResourceDetailViewModel(
      private val repository: CrucibleRepository,
      private val dataSyncManager: DataSyncManager
  ) : ViewModel() {
      val uiState: StateFlow<UiState>
      val isSyncing: StateFlow<Boolean>
      fun fetchResource(uuid: String)
      fun refreshResource(uuid: String)
      fun refreshThumbnails(uuid: String)
      fun startBackgroundSync()
      fun reset()
      fun getCardState(resourceId: String, key: String): Boolean
      fun setCardState(resourceId: String, key: String, value: Boolean)
  }
  ```
  Note the constructor drops `cacheManager: CacheManager` entirely — every cache operation this ViewModel needs is now on `CrucibleRepository`. `resourceDetailCache`, `loadedResources`, `enrichedUuids`, `failedEnrichmentUuids`, `loadedThumbnails`, `getThumbnails`, `fetchAndCacheThumbnails`, `evictThumbnails`, and `preloadRelatedResources` are all removed — their responsibilities are now either the repository's (caching, enrichment-on-demand) or moved into the screen (Task 4, page-local enrichment triggering).

- [ ] **Step 1: Rewrite the file**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailViewModel.kt`:

```kotlin
package crucible.lens.ui.detail

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.sync.DataSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val uuid: String, val isRefreshing: Boolean = false) : UiState()
    data class Error(val message: String) : UiState()
}

private const val MAX_CARD_STATE_ENTRIES = 50

class ResourceDetailViewModel(
    private val repository: CrucibleRepository,
    private val dataSyncManager: DataSyncManager
) : ViewModel() {

    // Tracks the active fetch/refresh so navigating to a new resource
    // cancels any in-flight request for the previous one.
    private var activeFetchJob: Job? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Persists expanded/collapsed state of detail screen cards across navigation. */
    private val resourceCardState = mutableStateMapOf<String, SnapshotStateMap<String, Boolean>>()
    private val resourceCardStateOrder = ArrayDeque<String>()

    fun getCardState(resourceId: String, key: String): Boolean =
        resourceCardState[resourceId]?.get(key) ?: false

    fun setCardState(resourceId: String, key: String, value: Boolean) {
        if (!resourceCardState.containsKey(resourceId)) {
            resourceCardStateOrder.addLast(resourceId)
            if (resourceCardStateOrder.size > MAX_CARD_STATE_ENTRIES) {
                resourceCardState.remove(resourceCardStateOrder.removeFirst())
            }
        }
        resourceCardState.getOrPut(resourceId) { mutableStateMapOf() }[key] = value
    }

    fun refreshThumbnails(uuid: String) {
        viewModelScope.launch {
            repository.invalidateThumbnails(uuid)
            repository.fetchThumbnails(uuid, forceRefresh = true)
        }
    }

    fun fetchResource(uuid: String) {
        activeFetchJob?.cancel()
        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()

            // Show cached version immediately for snappy navigation (Success emits as soon
            // as ANY cached data exists for this uuid — the screen observes the repository
            // directly for the actual resource content), but always fetch fresh data so the
            // detail view has links and full metadata.
            val hasCached = repository.getCachedResource(trimmedUuid) != null
            _uiState.value = if (hasCached) UiState.Success(trimmedUuid, isRefreshing = true) else UiState.Loading

            when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                is ResourceResult.Success -> {
                    _uiState.value = UiState.Success(trimmedUuid)
                }
                is ResourceResult.Error -> _uiState.value = UiState.Error(result.message)
                is ResourceResult.Loading -> {}
            }
        }
    }

    private var syncJob: Job? = null

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun startBackgroundSync() {
        _isSyncing.value = true
        syncJob = viewModelScope.launch {
            try { dataSyncManager.syncAll() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
            finally { _isSyncing.value = false }
        }
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }

    fun refreshResource(uuid: String) {
        activeFetchJob?.cancel()
        // Pause background sync so the user-initiated refresh gets uncontested network access.
        // Sync resumes after the refresh completes.
        val syncWasActive = syncJob?.isActive == true
        syncJob?.cancel()

        activeFetchJob = viewModelScope.launch {
            val trimmedUuid = uuid.trim()
            repository.invalidateResource(trimmedUuid)
            repository.invalidateThumbnails(trimmedUuid)

            val current = _uiState.value
            val isPrimary = current is UiState.Success && current.uuid == trimmedUuid
            _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = true) else it }
            try {
                when (val result = repository.fetchResourceByUuid(trimmedUuid)) {
                    is ResourceResult.Success -> {
                        // isPrimary distinguishes refreshing the currently-displayed resource
                        // from refreshing a sibling reached via the pager — either way the
                        // fresh data lands in the repository's cache and every page observing
                        // this uuid picks it up automatically; only the primary case updates
                        // this ViewModel's own uiState.
                        if (isPrimary) _uiState.value = UiState.Success(trimmedUuid)
                    }
                    is ResourceResult.Error -> if (isPrimary) _uiState.value = UiState.Error(result.message)
                    is ResourceResult.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Timeout or network failure — error state (if primary) was set above
            } finally {
                _uiState.update { if (it is UiState.Success) it.copy(isRefreshing = false) else it }
                if (syncWasActive) startBackgroundSync()
            }
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles in isolation**

This file will not compile on its own yet because `ResourceDetailScreen.kt` (Task 4, not yet rewritten) still references the old `UiState.Success(resource, thumbnails, isRefreshing)` shape and the deleted `resourceDetailCache`/`ResourceDetailCache`. Do not run a full compile check yet — proceed directly to Task 4, which is required before this file's changes are verifiable.

- [ ] **Step 3: Update the Koin module constructor call**

In `app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt`, `viewModelOf(::ResourceDetailViewModel)` (line 33) uses reflection-free constructor injection via Koin's `viewModelOf` — it automatically matches the new two-parameter constructor (`repository`, `dataSyncManager`) since Koin resolves constructor parameters by type, and both `CrucibleRepository` and `DataSyncManager` are already registered as singletons in the same module. **No change is needed to `AppModule.kt`** — this step is a verification note, not an edit: confirm by reading `app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt:27-44` that no explicit `ResourceDetailViewModel(get(), get(), get())` call exists (it uses `viewModelOf`, not `single { ResourceDetailViewModel(...) }`), which is why no edit is required here.

- [ ] **Step 4: Commit is deferred to Task 4**

Do not commit yet — this task's changes plus Task 2's deletion plus Task 4's screen rewrite land together as one commit once the codebase compiles again (see Task 4 Step 6).

---

### Task 4: Rewrite `ResourceDetailScreen`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailScreen.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `CrucibleRepository.observeResource`, `.getCachedResource`, `.fetchResourceByUuid`, `.observeThumbnails`, `.fetchThumbnails`, `.fetchSiblings` (Task 1), `koinInject<CrucibleRepository>()`, `koinInject<ApiClient>()` (kept — still used for `unlinkDatasetSample`/`unlinkDatasets`/`unlinkSamples`/`deleteThumbnail`, which are one-shot mutations with no cache-read concern, out of scope for this phase's caching redesign).
- Produces (new `ResourceDetailScreen` signature):
  ```kotlin
  @Composable
  fun ResourceDetailScreen(
      uuid: String,
      graphExplorerUrl: String,
      modifier: Modifier = Modifier,
      isRefreshing: Boolean = false,
      siblingGroupBy: String? = null,
      onBack: () -> Unit,
      onNavigateToResource: (String) -> Unit,
      onNavigateToProject: (String) -> Unit,
      onNavigateToInstrument: (String) -> Unit = {},
      onSearch: () -> Unit = {},
      onHome: () -> Unit,
      onRefresh: (uuid: String) -> Unit,
      onDuplicate: (CrucibleResource) -> Unit = {},
      recentHistory: List<crucible.lens.data.preferences.HistoryItem> = emptyList(),
      onSaveToHistory: (uuid: String, name: String, resourceType: String?) -> Unit = { _, _, _ -> },
      getCardState: (key: String) -> Boolean = { false },
      onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
      onNavigateToAddFiles: (datasetUuid: String) -> Unit = {},
      onNavigateToMetadataEditor: () -> Unit = {},
      onNavigateToUser: (String) -> Unit = {},
  )
  ```
  Removed parameters: `resource: CrucibleResource`, `thumbnails: List<Thumbnail>`, `cache: ResourceDetailCache`. `graphExplorerUrl` through `onNavigateToUser` are otherwise unchanged from today's signature.

This is the largest single file change in this phase. The rewrite preserves every existing behavior (pager, sibling grouping, thumbnails, pull-to-refresh, sheets/dialogs, overflow menu, QR dialog) but restructures the state model:

1. The screen observes its own `uuid`'s resource via `repository.observeResource(uuid).collectAsStateWithLifecycle(initial = repository.getCachedResource(uuid))` — if the resource is null (not yet fetched), the screen shows a loading state; `NavGraph`'s `LaunchedEffect(mfid) { viewModel.fetchResource(mfid) }` (unchanged) drives the actual fetch that populates the repository's cache, which this `collectAsStateWithLifecycle` picks up reactively.
2. Sibling list resolution moves to a `LaunchedEffect(resource?.let { it.projectId to activeSiblingGroupBy })`-keyed call to `repository.fetchSiblings(...)`, replacing the local `loadSiblings()` suspend function.
3. Each pager page (inside its existing `key(pageResource.uniqueId) { }` scope) independently observes `repository.observeResource(pageUuid)` and, for datasets, `repository.observeThumbnails(pageUuid)` — replacing `cache.loadedResources[pageResource.uniqueId]`/`cache.loadedThumbnails[pageResource.uniqueId]` reads.
4. "Is this page enriched" becomes `pageResourceState?.let { hasLinks(it) } == true` computed inline, replacing `cache.enrichedUuids.contains(...)`.
5. "Did enrichment fail" becomes page-local `var enrichmentFailed by remember { mutableStateOf(false) }`, set by that page's own `LaunchedEffect` when it calls `repository.fetchResourceByUuid(pageUuid)` and gets `ResourceResult.Error` — replacing `cache.failedEnrichmentUuids`.
6. The windowed cleanup blocks (evicting entries beyond ±20/±3) are deleted entirely — `ObservableCache`'s own size cap in the repository handles memory bounding now.

- [ ] **Step 1: Rewrite the top of the file — imports, module-level helpers, function signature**

Replace lines 1-170 of `app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailScreen.kt` (everything from the package declaration through the start of the function body, up to and including the `LaunchedEffect(resource.uniqueId) { if (resource is Dataset...` block) with:

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)
package crucible.lens.ui.detail
import androidx.compose.material3.ExperimentalMaterial3Api
import crucible.lens.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIcons
import crucible.lens.ui.common.AppTopBar
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import crucible.lens.ui.common.EffectsFastSpring
import crucible.lens.ui.common.EffectsDefaultSpring
import crucible.lens.ui.common.SpatialDefaultSizeSpring
import crucible.lens.ui.common.SpatialFastSizeSpring
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.time.Clock
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.data.repository.ResourceResult
import crucible.lens.data.model.CrucibleResource
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.ResourceLink
import crucible.lens.data.model.Sample
import crucible.lens.data.model.Thumbnail
import crucible.lens.data.model.ThumbnailCreateRequest
import crucible.lens.ui.metadata.MetadataHolder
import crucible.lens.ui.common.AppScaffold
import crucible.lens.ui.common.parseAsJsonObject
import crucible.lens.ui.common.OpenInWebMenuItem
import crucible.lens.ui.common.ShareMenuItem
import crucible.lens.ui.common.ErrorCard
import crucible.lens.ui.common.fadeEndEdge
import crucible.lens.ui.common.LoadingContent
import crucible.lens.ui.common.QrCodeDialog
import crucible.lens.ui.common.QrCodeDialogWithNavigation
import crucible.lens.ui.common.ScrollToTopButton
import crucible.lens.ui.detail.components.*
import org.koin.compose.koinInject

private data class UnlinkRequest(val name: String, val otherUuid: String, val action: suspend () -> Unit)

private fun hasLinks(resource: CrucibleResource): Boolean = when (resource) {
    is Sample -> resource.links != null
    is Dataset -> resource.links != null
}

private fun siblingGroupLabel(groupBy: String?, resource: CrucibleResource): String = when (groupBy) {
    "MEASUREMENT" -> "Measurement"
    "INSTRUMENT"  -> "Instrument"
    "DATE"        -> "Date"
    "FORMAT"      -> "Format"
    "SESSION"     -> "Session"
    "OWNER"       -> "Owner"
    "TYPE"        -> "Type"
    null -> when (resource) { is Sample -> "Type"; else -> "Measurement" }
    else -> groupBy.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResourceDetailScreen(
    uuid: String,
    graphExplorerUrl: String,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    siblingGroupBy: String? = null,
    onBack: () -> Unit,
    onNavigateToResource: (String) -> Unit,
    onNavigateToProject: (String) -> Unit,
    onNavigateToInstrument: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onHome: () -> Unit,
    onRefresh: (uuid: String) -> Unit,
    onDuplicate: (CrucibleResource) -> Unit = {},
    recentHistory: List<crucible.lens.data.preferences.HistoryItem> = emptyList(),
    onSaveToHistory: (uuid: String, name: String, resourceType: String?) -> Unit = { _, _, _ -> },
    getCardState: (key: String) -> Boolean = { false },
    onCardStateChange: (key: String, value: Boolean) -> Unit = { _, _ -> },
    onNavigateToAddFiles: (datasetUuid: String) -> Unit = {},
    onNavigateToMetadataEditor: () -> Unit = {},
    onNavigateToUser: (String) -> Unit = {},
) {
    val apiClient = koinInject<ApiClient>()
    val repository = koinInject<CrucibleRepository>()
    var showQrDialog by remember { mutableStateOf(false) }
    var showSiblingGroupDialog by remember { mutableStateOf(false) }
    // Local groupBy that can be changed while browsing; starts from the nav argument.
    var activeSiblingGroupBy by remember { mutableStateOf(siblingGroupBy) }

    // The primary resource this screen was navigated to. Observed reactively from the
    // repository — NavGraph's LaunchedEffect(mfid) { viewModel.fetchResource(mfid) } drives
    // the actual fetch; this just renders whatever the repository currently has cached for
    // this uuid, updating automatically as fresher data arrives (cache placeholder -> full
    // network result), with NO object-identity-keyed state anywhere reacting to that change.
    val resource by repository.observeResource(uuid)
        .collectAsStateWithLifecycle(initial = repository.getCachedResource(uuid))
```

- [ ] **Step 2: Rewrite the sibling-loading and pager-setup section**

Replace the block from the old `LaunchedEffect(resource.uniqueId) { if (resource is Dataset...` (originally lines 181-185) through the old `LaunchedEffect(activeSiblingGroupBy) { ... }` block (originally lines 305-312) with:

```kotlin

    // Sibling navigation: same type within the same project. Resolved via the repository
    // (cache-then-network), re-resolved when the resource's project changes or the user
    // changes groupBy. Keyed on (projectId, groupBy) — stable strings, never the resource
    // object — so this never resets due to the resource being enriched in place.
    var siblingList by remember { mutableStateOf<List<CrucibleResource>>(emptyList()) }
    var siblingsResolved by remember { mutableStateOf(false) }

    val currentProjectId = resource.let { r ->
        when (r) {
            is Sample -> r.projectId
            is Dataset -> r.projectId
            null -> null
        }
    }

    LaunchedEffect(uuid, currentProjectId, activeSiblingGroupBy) {
        val r = resource
        if (r == null) return@LaunchedEffect
        siblingsResolved = false
        siblingList = listOf(r)
        try {
            siblingList = repository.fetchSiblings(r, activeSiblingGroupBy)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            siblingList = listOf(r)
        } finally {
            siblingsResolved = true
        }
    }

    val siblingIndex = remember(siblingList, uuid) {
        siblingList.indexOfFirst { it.uniqueId == uuid }
    }

    // HorizontalPager state. initialPage uses siblingIndex directly so the pager
    // opens at the right position without a post-composition scroll (cold-start
    // fallback below handles the rare case where siblingList isn't ready yet).
    val pagerState = rememberPagerState(
        initialPage = siblingIndex.coerceAtLeast(0),
        pageCount = { siblingList.size.coerceAtLeast(1) }
    )

    // Scroll to the resource's position once siblings are resolved and pageCount is updated.
    // Runs after recomposition so pageCount reflects the full sibling list size.
    LaunchedEffect(siblingIndex, siblingsResolved) {
        if (siblingsResolved && siblingIndex > 0 && pagerState.currentPage != siblingIndex) {
            pagerState.scrollToPage(siblingIndex)
        }
    }

    // Current resource shown in the pager — drives TopAppBar title and overflow menu
    val currentPageUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId ?: uuid
    val currentDisplayResource: CrucibleResource? by repository.observeResource(currentPageUuid)
        .collectAsStateWithLifecycle(initial = repository.getCachedResource(currentPageUuid))
```

Note: `currentProjectId` reads `resource` (a `by`-delegated `State<CrucibleResource?>`) once into the local parameter `r` via `.let { r -> ... }`, then smart-casts `r` inside the `when` branches — capturing into a local `val`/lambda parameter first avoids relying on smart-cast through the delegated property read itself, which Kotlin does not always guarantee across `when` branch bodies.

- [ ] **Step 3: Rewrite screen-level sheet state and history-tracking effects**

Replace the block from the old `// Screen-level sheet/dialog state` comment (originally lines 343-350) through the old `LaunchedEffect(MetadataHolder.isDirty)` block (originally lines 362-369) with:

```kotlin

    // Screen-level sheet/dialog state — operate on currentDisplayResource
    var showEditSheet by remember { mutableStateOf(false) }
    var editSheetPendingMetadata by remember { mutableStateOf<kotlinx.serialization.json.JsonObject?>(null) }
    var editSheetWaitingForMetadata by remember { mutableStateOf(false) }
    var showLinkSheet by remember { mutableStateOf(false) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var pendingUnlink by remember { mutableStateOf<UnlinkRequest?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    // Track history and last-viewed sibling continuously as user scrolls through pages
    LaunchedEffect(pagerState.currentPage, pagerState.targetPage) {
        val targetPage = if (pagerState.isScrollInProgress) pagerState.targetPage else pagerState.currentPage
        val targetResource = siblingList.getOrNull(targetPage)
        if (targetResource != null) {
            val rtype = if (targetResource is Sample) "sample" else "dataset"
            onSaveToHistory(targetResource.uniqueId, targetResource.name, rtype)
        }
    }

    // Re-open edit sheet with updated metadata after returning from MetadataEditorScreen.
    LaunchedEffect(MetadataHolder.isDirty) {
        if (MetadataHolder.isDirty && editSheetWaitingForMetadata) {
            editSheetPendingMetadata = MetadataHolder.take()
            editSheetWaitingForMetadata = false
            showEditSheet = true
        }
    }
```

- [ ] **Step 4: Delete the two windowed-loading `LaunchedEffect` blocks entirely**

Delete the entire block from the old `// Incremented when a sibling PTR completes` comment (originally line 371) through the end of the second windowed-loading effect (originally line 523, the closing brace of the thumbnail-loading `LaunchedEffect`). This removes:
- `siblingReloadTrigger` (no longer needed — pages now observe the repository directly, so a fresh fetch automatically propagates to every observer without a manual trigger counter)
- The relationship-enrichment `LaunchedEffect(pagerState.currentPage, siblingList, siblingReloadTrigger)` block (originally lines 376-464)
- The thumbnail-loading `LaunchedEffect(pagerState.currentPage, siblingList, siblingReloadTrigger)` block (originally lines 468-523)

This logic is replaced by per-page `LaunchedEffect`s inside the `HorizontalPager` content lambda itself (Step 6), since each page now independently manages its own enrichment/thumbnail fetch triggered by its own visibility in the pager's composition — Compose's `HorizontalPager` already only composes pages near the current one (governed by `beyondViewportPageCount`, default near-zero), so per-page effects naturally approximate the old ±10/±2 windowing without needing to compute page-index windows by hand.

- [ ] **Step 5: Rewrite the `triggerRefresh` function and its supporting state**

Replace the block from the old `LaunchedEffect(resource.uniqueId) { onSaveToHistory(...) }` (originally lines 525-527) through the end of the old `triggerRefresh` function and its trailing `LaunchedEffect(isRefreshing)` (originally lines 528-574) with:

```kotlin

    val scope = rememberCoroutineScope()
    val platformContext = getPlatformContext()
    val isDarkTheme = isSystemInDarkTheme()
    val primaryColorValue = MaterialTheme.colorScheme.primary.value.toLong()

    // True only when a sibling (not the primary resource) was pull-to-refreshed.
    var localRefreshState by remember { mutableStateOf(false) }

    fun triggerRefresh() {
        val currentUuid = siblingList.getOrNull(pagerState.currentPage)?.uniqueId ?: uuid
        if (currentUuid != uuid) {
            // Sibling refresh: fetch inline without ViewModel involvement. The fresh result
            // lands in the repository's cache; that page's own observeResource collection
            // picks it up automatically — no manual map write, no reload-trigger counter.
            scope.launch {
                localRefreshState = true
                try {
                    repository.fetchResourceByUuid(currentUuid)
                } finally {
                    localRefreshState = false
                }
            }
        } else {
            onRefresh(currentUuid)
        }
    }

    // Primary resource refresh only — sibling PTR is handled inline above.
    LaunchedEffect(isRefreshing) {
        localRefreshState = isRefreshing
    }
```

Note: `repository.fetchResourceByUuid` on a sibling that already has links cached will short-circuit to the cache-hit path (per `CrucibleRepository.kt`'s existing `hasLinks` check) and not actually re-fetch from network — for a pull-to-refresh gesture the user expects a real refresh even if cached data has links. This matches a pre-existing limitation already present in the old code path's `apiClient.service.getSample`/`getDataset` calls only when it's a genuinely different code path — however, the OLD code deliberately bypassed the cache-then-network check by calling `apiClient.service.getSample`/`getDataset` directly (see original lines 549-553), which this rewrite's use of `fetchResourceByUuid` does NOT replicate. To preserve pull-to-refresh's "always hit network" semantic, invalidate the resource cache immediately before fetching:

```kotlin
        if (currentUuid != uuid) {
            scope.launch {
                localRefreshState = true
                try {
                    repository.invalidateResource(currentUuid)
                    repository.fetchResourceByUuid(currentUuid)
                } finally {
                    localRefreshState = false
                }
            }
        } else {
```

Use this corrected version (with the `invalidateResource` call) instead of the first draft above.

- [ ] **Step 6: Rewrite the pager content — per-page reactive observation**

Replace the entire `AppScaffold { ... PullToRefreshBox { ... HorizontalPager { ... } ... } }` block. This is the largest single replacement — locate the existing structure from `AppScaffold(` (originally line 576) through its closing `}` (originally line 1023) and replace it in full:

```kotlin

    AppScaffold(
        topBar = {
            AppTopBar(
                title = if (resource is Sample) "Sample" else "Dataset",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onSearch) {
                        AppIcon(AppIcons.Search)
                    }
                    IconButton(onClick = onHome) {
                        AppIcon(AppIcons.Home)
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            AppIcon(AppIcons.MoreVert)
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            val displayForMenu = currentDisplayResource
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { AppIcon(AppIcons.Edit) },
                                onClick = { overflowMenuExpanded = false; showEditSheet = true },
                                enabled = displayForMenu != null
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { AppIcon(AppIcons.CopyResource) },
                                onClick = { displayForMenu?.let { overflowMenuExpanded = false; onDuplicate(it) } },
                                enabled = displayForMenu != null
                            )
                            if (displayForMenu is Dataset) {
                                DropdownMenuItem(
                                    text = { Text("Add file") },
                                    leadingIcon = { AppIcon(AppIcons.AttachFile) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        onNavigateToAddFiles(displayForMenu.uniqueId)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Link") },
                                leadingIcon = { AppIcon(AppIcons.LinkResource) },
                                onClick = { overflowMenuExpanded = false; showLinkSheet = true },
                                enabled = displayForMenu != null
                            )
                            val deletionRequest = when (displayForMenu) {
                                is Sample -> displayForMenu.deletionRequest
                                is Dataset -> displayForMenu.deletionRequest
                                null -> null
                            }
                            val deletionStatus = (deletionRequest?.get("status") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            DropdownMenuItem(
                                text = { Text(if (deletionStatus != null) "Deletion ${deletionStatus.replaceFirstChar { it.uppercase() }}" else "Request deletion") },
                                leadingIcon = { AppIcon(AppIcons.RequestDeletion) },
                                enabled = displayForMenu != null && deletionStatus == null,
                                onClick = { overflowMenuExpanded = false; showDeletionDialog = true }
                            )
                            val projectId = when (displayForMenu) {
                                is Sample -> displayForMenu.projectId
                                is Dataset -> displayForMenu.projectId
                                null -> null
                            }
                            if (displayForMenu != null && projectId != null && graphExplorerUrl.isNotBlank()) {
                                val webUrl = when (displayForMenu) {
                                    is Sample  -> "$graphExplorerUrl/$projectId/samples/${displayForMenu.uniqueId}"
                                    is Dataset -> "$graphExplorerUrl/$projectId/datasets/${displayForMenu.uniqueId}"
                                }
                                OpenInWebMenuItem { overflowMenuExpanded = false; openUrl(platformContext, webUrl) }
                                ShareMenuItem {
                                    overflowMenuExpanded = false
                                    shareResource(
                                        context = platformContext,
                                        resource = displayForMenu,
                                        shareText = webUrl,
                                        subject = displayForMenu.name,
                                        darkTheme = isDarkTheme,
                                        bannerColorValue = primaryColorValue
                                    )
                                }
                            }
                            // Sibling grouping — only when resource belongs to a project
                            if (displayForMenu != null && projectId != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                val groupLabel = siblingGroupLabel(activeSiblingGroupBy, displayForMenu)
                                DropdownMenuItem(
                                    text = { Text("Siblings: $groupLabel") },
                                    leadingIcon = { AppIcon(AppIcons.SwapResource) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        showSiblingGroupDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = localRefreshState,
            onRefresh = { triggerRefresh() },
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (siblingList.isNotEmpty() && siblingIndex >= 0) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { pageIndex ->
                    if (pageIndex >= siblingList.size) return@HorizontalPager
                    val pageResource = siblingList[pageIndex]
                    val pageUuid = pageResource.uniqueId

                    key(pageUuid) {
                        // Each page independently observes its own resource and (for datasets)
                        // thumbnails from the repository. Any fetch anywhere in the app that
                        // updates this uuid's cache entry is picked up automatically here.
                        val displayResource by repository.observeResource(pageUuid)
                            .collectAsStateWithLifecycle(initial = repository.getCachedResource(pageUuid) ?: pageResource)
                        val isEnriched = displayResource?.let { hasLinks(it) } == true
                        var enrichmentFailed by remember { mutableStateOf(false) }

                        LaunchedEffect(pageUuid, isEnriched) {
                            if (!isEnriched) {
                                when (repository.fetchResourceByUuid(pageUuid)) {
                                    is ResourceResult.Error -> enrichmentFailed = true
                                    is ResourceResult.Success -> enrichmentFailed = false
                                    is ResourceResult.Loading -> {}
                                }
                            }
                        }

                        // Always observed, regardless of resource type — harmless for samples
                        // since nothing ever populates a thumbnail cache entry for a sample uuid,
                        // so this simply stays null. Avoids an if/else branch with two different
                        // State<List<Thumbnail>?> producers (collectAsStateWithLifecycle vs a
                        // plain remembered MutableState), which is unnecessary complexity for a
                        // case that's just as correct handled uniformly.
                        val displayThumbnails by repository.observeThumbnails(pageUuid)
                            .collectAsStateWithLifecycle(initial = null)
                        LaunchedEffect(pageUuid, isEnriched) {
                            if (isEnriched && pageResource is Dataset && displayThumbnails == null) {
                                repository.fetchThumbnails(pageUuid)
                            }
                        }
                        val resolvedThumbnails = displayThumbnails ?: emptyList()

                        // Each page needs its own independent scroll state
                        val scrollState = rememberScrollState()
                        val showScrollToTop by remember { derivedStateOf { scrollState.value > 300 } }
                        val pageGetCardState: (String) -> Boolean = { k -> getCardState("$pageUuid/$k") }
                        val pageSetCardState: (String, Boolean) -> Unit = { k, value -> onCardStateChange("$pageUuid/$k", value) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                visible = !isEnriched && !isRefreshing,
                                enter = fadeIn(animationSpec = EffectsDefaultSpring),
                                exit = fadeOut(animationSpec = EffectsDefaultSpring)
                            ) {
                                LoadingContent(title = "Loading Resource")
                            }

                            AnimatedVisibility(
                                visible = isEnriched,
                                enter = fadeIn(animationSpec = EffectsFastSpring),
                                exit = fadeOut(animationSpec = EffectsFastSpring)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(16.dp)
                                ) {
                                    val hasPrev = pageIndex > 0
                                    val hasNext = pageIndex < siblingList.size - 1

                                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                        BasicInfoCard(
                                            resource = displayResource ?: pageResource,
                                            onPrev = if (hasPrev) {
                                                { scope.launch { pagerState.animateScrollToPage(pageIndex - 1) } }
                                            } else null,
                                            onNext = if (hasNext) {
                                                { scope.launch { pagerState.animateScrollToPage(pageIndex + 1) } }
                                            } else null,
                                            currentIndex = pageIndex,
                                            totalCount = siblingList.size,
                                            siblingsResolved = siblingsResolved
                                        )
                                    }

                                    val resolved = displayResource ?: pageResource

                                    AnimatedVisibility(
                                        visible = enrichmentFailed,
                                        enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                        exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                    ) {
                                        ErrorCard(
                                            title = "Could not load full data",
                                            message = "Links and metadata may be incomplete.",
                                            modifier = Modifier.padding(bottom = 16.dp),
                                            onRetry = { enrichmentFailed = false }
                                        )
                                    }

                                    val displayDeletionRequest = when (resolved) {
                                        is Sample -> resolved.deletionRequest
                                        is Dataset -> resolved.deletionRequest
                                    }
                                    if (displayDeletionRequest != null) {
                                        val delStatus = (displayDeletionRequest["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "pending"
                                        val delReason = (displayDeletionRequest["reason"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.ifBlank { null }
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppIcon(AppIcons.RequestDeletion, tint = MaterialTheme.colorScheme.onErrorContainer)
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        "Deletion ${delStatus.replaceFirstChar { it.uppercase() }}",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    if (delReason != null) {
                                                        Text(delReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    when (resolved) {
                                        is Sample -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                            SampleDetailsCard(
                                                sample = resolved,
                                                onProjectClick = onNavigateToProject,
                                                onUserClick = onNavigateToUser,
                                                onShowQr = { showQrDialog = true },
                                                initialAdvanced = pageGetCardState("advanced"),
                                                onAdvancedChange = { pageSetCardState("advanced", it) }
                                            )
                                        }
                                        is Dataset -> Box(modifier = Modifier.padding(bottom = 16.dp)) {
                                            DatasetDetailsCard(
                                                dataset = resolved,
                                                onProjectClick = onNavigateToProject,
                                                onUserClick = onNavigateToUser,
                                                onInstrumentClick = onNavigateToInstrument,
                                                onShowQr = { showQrDialog = true },
                                                initialAdvanced = pageGetCardState("advanced"),
                                                onAdvancedChange = { pageSetCardState("advanced", it) }
                                            )
                                        }
                                    }

                                    when (resolved) {
                                        is Dataset -> {
                                            AnimatedVisibility(
                                                visible = resolvedThumbnails.isNotEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                                                    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                                                        ThumbnailsSection(
                                                            uuid = pageUuid,
                                                            thumbnails = resolvedThumbnails,
                                                            onDelete = { thumbnailId ->
                                                                scope.launch {
                                                                    val resp = apiClient.service.deleteThumbnail(pageUuid, thumbnailId)
                                                                    if (resp is ApiResult.Success) {
                                                                        repository.invalidateThumbnails(pageUuid)
                                                                        repository.fetchThumbnails(pageUuid, forceRefresh = true)
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "associated" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    LinkedSamplesCard(
                                                        samples = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasetSample(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("linked_samples"),
                                                        onExpandChange = { pageSetCardState("linked_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "parent" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ParentDatasetsCard(
                                                        parents = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasets(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("parent_datasets"),
                                                        onExpandChange = { pageSetCardState("parent_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "child" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ChildDatasetsCard(
                                                        children = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasets(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("child_datasets"),
                                                        onExpandChange = { pageSetCardState("child_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = !resolved.scientificMetadata.isNullOrEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ScientificMetadataCard(
                                                        metadata = resolved.scientificMetadata ?: emptyMap(),
                                                        initialExpanded = pageGetCardState("sci_meta_expanded"),
                                                        initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                                        onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                                        onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                                    )
                                                }
                                            }
                                            AssociatedFilesCard(
                                                datasetUuid = pageUuid,
                                                initialExpanded = pageGetCardState("download_links"),
                                                onExpandedChange = { pageSetCardState("download_links", it) }
                                            )
                                        }
                                        is Sample -> {
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "parent" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ParentSamplesCard(
                                                        parents = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "parent" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkSamples(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("parent_samples"),
                                                        onExpandChange = { pageSetCardState("parent_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "sample" && it.relationship == "child" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ChildSamplesCard(
                                                        children = resolved.links.orEmpty().filter { it.resourceType == "sample" && it.relationship == "child" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkSamples(resolved.uniqueId, u) } },
                                                        initialExpanded = pageGetCardState("child_samples"),
                                                        onExpandChange = { pageSetCardState("child_samples", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = resolved.links?.any { it.resourceType == "dataset" && it.relationship == "associated" } == true,
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    LinkedDatasetsCard(
                                                        datasets = resolved.links.orEmpty().filter { it.resourceType == "dataset" && it.relationship == "associated" }.sortedBy { it.uniqueId },
                                                        onNavigateToResource = onNavigateToResource,
                                                        onUnlink = { u, name -> pendingUnlink = UnlinkRequest(name, u) { apiClient.service.unlinkDatasetSample(u, resolved.uniqueId) } },
                                                        initialExpanded = pageGetCardState("linked_datasets"),
                                                        onExpandChange = { pageSetCardState("linked_datasets", it) }
                                                    )
                                                }
                                            }
                                            AnimatedVisibility(
                                                visible = !resolved.scientificMetadata.isNullOrEmpty(),
                                                enter = fadeIn(animationSpec = EffectsFastSpring) + expandVertically(animationSpec = SpatialDefaultSizeSpring),
                                                exit = fadeOut(animationSpec = EffectsFastSpring) + shrinkVertically(animationSpec = SpatialFastSizeSpring)
                                            ) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                                    ScientificMetadataCard(
                                                        metadata = resolved.scientificMetadata ?: emptyMap(),
                                                        initialExpanded = pageGetCardState("sci_meta_expanded"),
                                                        initialExpandAll = pageGetCardState("sci_meta_expand_all"),
                                                        onExpandedChange = { pageSetCardState("sci_meta_expanded", it) },
                                                        onExpandAllChange = { pageSetCardState("sci_meta_expand_all", it) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val ageMin = null as Long?
                                    if (ageMin != null) {
                                        Text(
                                            text = "Cached ${ageMin}m ago",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            ScrollToTopButton(
                                visible = showScrollToTop,
                                onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    } // end key()
                } // end HorizontalPager
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoadingContent(title = "Loading Resource")
                }
            }
        } // end PullToRefreshBox
    } // end AppScaffold
```

Note on `val ageMin = null as Long?`: the old "Cached Nm ago" footer read `cacheManager.getResourceAgeMinutes(resource.uniqueId)`. `CrucibleRepository.resourceAgeMillis(uuid)` (Phase 1) is the reactive-cache equivalent, but it returns milliseconds and is a plain function call, not observed — calling it directly here would read a stale, non-recomposing value. Rather than wire up a third `collectAsStateWithLifecycle` just for a cosmetic debug footer, this rewrite removes the "Cached Nm ago" text entirely (replace `val ageMin = null as Long?` and its surrounding `if` block with nothing — delete both). This is a minor, intentional behavior removal: flag it to the user as a callout in this task's final report, since it changes visible (if minor, debug-flavored) UI text.

- [ ] **Step 7: Rewrite the closing dialogs/sheets section**

Replace the final block of the file — from the old `// Screen-level sheets and dialogs — operate on currentDisplayResource` comment (originally line 1025) through the end of the function (originally line 1162) — with:

```kotlin

    // Screen-level sheets and dialogs — operate on currentDisplayResource
    if (showSiblingGroupDialog) {
        val forGroupOptions = currentDisplayResource
        if (forGroupOptions != null) {
            val options: List<Pair<String, String>> = when (forGroupOptions) {
                is Sample  -> listOf("TYPE" to "Type", "DATE" to "Date", "OWNER" to "Owner")
                is Dataset -> listOf(
                    "MEASUREMENT" to "Measurement",
                    "INSTRUMENT"  to "Instrument",
                    "DATE"        to "Date",
                    "FORMAT"      to "Format",
                    "SESSION"     to "Session",
                    "OWNER"       to "Owner"
                )
            }
            val effectiveActive = activeSiblingGroupBy ?: when (forGroupOptions) {
                is Sample -> "TYPE"; is Dataset -> "MEASUREMENT"
            }
            AlertDialog(
                onDismissRequest = { showSiblingGroupDialog = false },
                icon = { AppIcon(AppIcons.SwapResource) },
                title = { Text("Sibling grouping") },
                text = {
                    Column {
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeSiblingGroupBy = value
                                        showSiblingGroupDialog = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = effectiveActive == value, onClick = null)
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSiblingGroupDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
    val editSheetResource = currentDisplayResource
    if (showEditSheet && editSheetResource != null) {
        EditResourceSheet(
            resource = editSheetResource,
            onDismiss = { showEditSheet = false; editSheetPendingMetadata = null },
            onSaved = { showEditSheet = false; editSheetPendingMetadata = null; onRefresh(editSheetResource.uniqueId) },
            overrideMetadata = editSheetPendingMetadata,
            onOpenMetadataEditor = { currentJson ->
                val current = runCatching {
                    currentJson.trim().ifBlank { null }
                        ?.parseAsJsonObject()
                }.getOrNull() ?: kotlinx.serialization.json.JsonObject(emptyMap())
                MetadataHolder.put(current)
                showEditSheet = false
                editSheetWaitingForMetadata = true
                onNavigateToMetadataEditor()
            }
        )
    }
    val linkSheetResource = currentDisplayResource
    if (showLinkSheet && linkSheetResource != null) {
        LinkResourceSheet(
            resource = linkSheetResource,
            recentHistory = recentHistory,
            onDismiss = { showLinkSheet = false },
            onLinked = { showLinkSheet = false; onRefresh(linkSheetResource.uniqueId) }
        )
    }
    val deletionResource = currentDisplayResource
    if (showDeletionDialog && deletionResource != null) {
        DeletionRequestDialog(
            resource = deletionResource,
            onDismiss = { showDeletionDialog = false },
            onSubmitted = { showDeletionDialog = false; onRefresh(deletionResource.uniqueId) }
        )
    }
    pendingUnlink?.let { req ->
        var isUnlinking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isUnlinking) pendingUnlink = null },
            icon = { AppIcon(AppIcons.UnlinkResource) },
            title = { Text("Unlink resource") },
            text = { Text("Remove link to \"${req.name}\"? The resources themselves will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isUnlinking = true
                            try {
                                req.action()
                                repository.invalidateResource(req.otherUuid)
                                pendingUnlink = null
                                currentDisplayResource?.let { onRefresh(it.uniqueId) }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                pendingUnlink = null
                            }
                        }
                    },
                    enabled = !isUnlinking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isUnlinking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    else Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnlink = null }, enabled = !isUnlinking) { Text("Cancel") }
            }
        )
    }

    // QR Code Dialog with horizontal navigation
    if (showQrDialog) {
        if (siblingList.isNotEmpty() && siblingIndex >= 0) {
            QrCodeDialogWithNavigation(
                resources = siblingList,
                initialIndex = pagerState.currentPage % siblingList.size,
                onDismiss = { showQrDialog = false },
                onPageChange = { pageIndex ->
                    scope.launch { pagerState.animateScrollToPage(pageIndex) }
                }
            )
        } else {
            val qrResource = currentDisplayResource
            if (qrResource != null) {
                QrCodeDialog(
                    mfid = qrResource.uniqueId,
                    name = qrResource.name,
                    onDismiss = { showQrDialog = false }
                )
            }
        }
    }
}
```

- [ ] **Step 8: Update `NavGraph.kt`'s `Screen.Detail` composable**

In `app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt`, the `contentKey` lambda (originally lines 441-450) reads `state.resource.uniqueId` — update it to use `state.uuid` matching the new `UiState.Success(uuid, isRefreshing)` shape:

```kotlin
                    contentKey = { state ->
                        when (state) {
                            is UiState.Idle    -> "idle"
                            is UiState.Loading -> "loading"
                            // Treat a stale success (wrong resource still loaded) as loading
                            // so the old resource never flashes during navigation
                            is UiState.Success -> if (state.uuid == mfid) "success" else "loading"
                            is UiState.Error   -> "error"
                        }
                    },
```

Replace the `is UiState.Success -> if (state.resource.uniqueId != mfid) { ... } else { ... }` block (originally lines 482-562) with:

```kotlin
                is UiState.Success -> if (state.uuid != mfid) {
                    // Stale state from a previous navigation — show blank background
                    // rather than flashing the wrong resource. LaunchedEffect will
                    // call reset() + fetchResource(mfid) momentarily.
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    ResourceDetailScreen(
                        uuid = mfid,
                        isRefreshing = state.isRefreshing,
                        graphExplorerUrl = graphExplorerUrl,
                        siblingGroupBy = siblingGroupBy,
                        onSaveToHistory = { uuid, name, resourceType ->
                            scope.launch { prefs.saveLastVisitedResource(uuid, name) }
                            scope.launch { prefs.addToHistory(uuid, name, resourceType) }
                        },
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToResource = { newMfid ->
                            navController.navigate(Screen.Detail.createRoute(newMfid))
                        },
                        onNavigateToProject = { projectId ->
                            navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                        },
                        onNavigateToInstrument = { instrumentId ->
                            navController.navigate(Screen.InstrumentDetail.createRoute(instrumentId))
                        },
                        onSearch = navigateSearch,
                        onHome = navigateHome,
                        onRefresh = { uuid ->
                            viewModel.refreshResource(uuid)
                        },
                        getCardState = { key -> viewModel.getCardState(mfid, key) },
                        onCardStateChange = { key, value -> viewModel.setCardState(mfid, key, value) },
                        onNavigateToAddFiles = { datasetUuid ->
                            navController.navigate(Screen.AddFiles.createRoute(datasetUuid))
                        },
                        onNavigateToMetadataEditor = {
                            navController.navigate(Screen.MetadataEditor.route)
                        },
                        onNavigateToUser = { identifier ->
                            navController.navigate(Screen.UserProfile.createRoute(identifier))
                        },
                        recentHistory = resourceHistory,
                        onDuplicate = { resource ->
                            when (resource) {
                                is Sample -> {
                                    DuplicateHolder.putSample(DuplicateHolder.SamplePrefill(
                                        name = resource.name,
                                        type = resource.sampleType,
                                        description = resource.description,
                                        timestamp = resource.timestamp,
                                        projectId = resource.projectId
                                    ))
                                    navController.navigate(Screen.CreateSample.createRoute())
                                }
                                is Dataset -> {
                                    DuplicateHolder.putDataset(DuplicateHolder.DatasetPrefill(
                                        name = resource.name,
                                        measurement = resource.measurement,
                                        instrumentName = resource.instrumentName,
                                        dataFormat = resource.dataFormat,
                                        sessionName = resource.sessionName,
                                        timestamp = resource.timestamp,
                                        projectId = resource.projectId
                                    ))
                                    navController.navigate(Screen.CreateDataset.createRoute())
                                }
                            }
                        }
                    )
                } // end else (correct resource)
```

Note: the old code's `LaunchedEffect(state.resource) { scope.launch { prefs.saveLastVisitedResource(...) } ... }` (originally lines 489-493) — which saved history using the just-fetched resource's `name` — is now redundant: `ResourceDetailScreen`'s own `onSaveToHistory` callback (invoked from its `LaunchedEffect(pagerState.currentPage, pagerState.targetPage)`, Task 4 Step 3) already saves history for the currently-displayed sibling on every page change, including the initial page. Delete the old `LaunchedEffect(state.resource) { ... }` block entirely rather than porting it — do not include it in the replacement above (it is already absent from the replacement block shown, confirm no `LaunchedEffect(state.resource)` remains in the file after this edit).

- [ ] **Step 9: Verify the full codebase compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL` with no new warnings. If compile errors reference `MONTH_NAMES`, `formatFileSize`, or `formatDecimal` imports that are now unused in `ResourceDetailScreen.kt` (these were used only within code paths — `loadSiblings`, thumbnail size formatting — verify by checking whether any remaining code in the file references them; if none do, remove the corresponding `import crucible.lens.data.util.X` lines to avoid unused-import warnings).

- [ ] **Step 10: Verify iOS still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`, only the 4 pre-existing unrelated warnings (`PersistentProjectCache.kt`, `CryptoUtils.kt`).

- [ ] **Step 11: Build and manually verify the debug APK**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Install the APK and manually verify:
1. Open a project, tap into a sample/dataset — detail screen loads correctly, all cards render.
2. Swipe through 5+ siblings rapidly — no loading-state flash on already-visited siblings, no visible flicker.
3. While viewing a resource, background-sync a project that resource belongs to (or simply wait — `startBackgroundSync` runs automatically) — confirm the currently-displayed resource does not flash back to a loading state if a background fetch happens to touch the same uuid.
4. Pull-to-refresh on the primary resource — spinner appears and clears correctly, data updates.
5. Pull-to-refresh on a sibling (swipe to a sibling, then PTR) — only that sibling refreshes, primary resource state is untouched.
6. Edit a resource via the overflow menu — sheet opens with current data, save works, screen reflects the update.
7. Link/unlink a resource — sheet opens, action completes, both sides' links update.
8. Open the QR dialog with the sibling pager, swipe within it — navigation and swiping work.
9. Trigger the "Could not load full data" error card artificially (e.g. toggle airplane mode mid-sibling-scroll) — confirm it appears/retries correctly.
10. Delete a thumbnail — confirm it disappears and the section either updates or collapses correctly.

This is a manual step — there is no automated UI test infrastructure in this project (per `CLAUDE.md`'s "no automated tests" state, and Phase 1/2 only add unit tests for the data layer, not Compose UI tests). Do not mark this task complete until all 10 checks pass on a real build.

- [ ] **Step 12: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailViewModel.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt
git add -u app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailCache.kt
git commit -m "Rewrite ResourceDetailScreen/ViewModel to observe the repository by uuid

Structurally eliminates the flash-bug class fixed ad hoc in ce03f1c: the
screen no longer receives a full resource object anywhere in its tree, so
no Compose state can be accidentally keyed on object identity. Enriched-ness
is now derived from resource.links != null instead of tracked in a separate
set; ResourceDetailCache's hand-rolled bookkeeping is deleted in favor of
each pager page independently observing CrucibleRepository's reactive cache."
```

---

## What this plan intentionally does NOT do (deferred to Phase 3)

- **Does not touch `HomeScreen.kt`, `ProjectsListScreen.kt`, `ProjectsListViewModel.kt`, `ProjectDetailViewModel.kt`, `InstrumentListScreen.kt`, `InstrumentListViewModel.kt`, `InstrumentDetailScreen.kt`, `InstrumentDetailViewModel.kt`, `ManageProjectViewModel.kt`, `ManageInstrumentViewModel.kt`, `CreateEditViewModels.kt`, `FilterSheet.kt`, `InstrumentPickerField.kt`, `AssociatedFilesCard.kt`, `LinkResourceSheet.kt`, or `EditResourceSheet.kt`.** All still use `CacheManager`/`ApiClient` directly. Phase 3 migrates these onto the repository's Phase-1/Phase-2 methods and finally deletes `CacheManager`'s now-dead resource/project/instrument/thumbnail methods.
- **Does not remove `CacheManager.kt` itself** — it's still the sole cache for every screen this phase didn't touch.
- **Does not add a "cached Nm ago" indicator back** — the old debug footer text is removed (Task 4 Step 6) since wiring up a fourth reactive collection for a cosmetic detail wasn't worth the complexity; if this is wanted back, it can be added as a small follow-up using `repository.resourceAgeMillis(uuid)` polled on a timer, or accepted as gone.

## Verification (full phase)

1. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest` — all unit tests pass (Phase 1's 26 plus Task 1's new tests).
2. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain` — `BUILD SUCCESSFUL`, zero new warnings.
3. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64` — `BUILD SUCCESSFUL`, only the 4 pre-existing unrelated warnings.
4. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug` — `BUILD SUCCESSFUL`; all 10 manual checks in Task 4 Step 11 pass.
5. `grep -rn "ResourceDetailCache" app/src/commonMain/` returns zero results anywhere in the codebase.
6. `grep -n "resource: CrucibleResource" app/src/commonMain/kotlin/crucible/lens/ui/detail/ResourceDetailScreen.kt` returns zero results in the function's own top-level parameter list (it may still appear in nested lambda parameters like `onDuplicate: (CrucibleResource) -> Unit`, which is expected and correct).
