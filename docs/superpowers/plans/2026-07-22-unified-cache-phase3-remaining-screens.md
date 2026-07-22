# Unified Cache — Phase 3: Migrate remaining screens, delete CacheManager

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate every remaining direct `CacheManager` consumer — `HomeScreen`, `ProjectsListScreen`, `ProjectsListViewModel`, `ProjectDetailViewModel`, `ProjectDetailScreen`, `ManageProjectViewModel`, `InstrumentListViewModel`, `InstrumentDetailViewModel`, `InstrumentDetailScreen`, `ManageInstrumentViewModel`, `CreateSampleViewModel`/`CreateDatasetViewModel`/`EditResourceViewModel`, `EditResourceSheet`, `AssociatedFilesCard`, `LinkResourceSheet`, `AccountViewModel`, `CacheSettingsScreen` — onto `CrucibleRepository`'s reactive methods, then delete `CacheManager.kt` entirely. This is the final phase: after this lands, every cache read/write in the app goes through exactly one class (`CrucibleRepository`), backed by exactly one caching primitive (`ObservableCache`, from Phase 1).

**Architecture:** Two entity types have no repository methods yet — dataset-associated-files and per-file signed download URLs (currently `CacheManager.datasetFilesCache`/`fileUrlCache`, used only by `AssociatedFilesCard`). Task 1 adds those the same way Phase 1/2 added every other entity type: an `ObservableCache` field + `observeX`/`fetchX`/`invalidateX` methods on `CrucibleRepository`. Every subsequent task swaps one file's `koinInject<CacheManager>()` for `koinInject<CrucibleRepository>()` (already present in most files) and its `cacheManager.X()` calls for the equivalent `repository.X()` calls established in Phases 1–2. The final task deletes `CacheManager.kt`, removes it from `AppModule.kt`, and confirms via grep that zero references remain anywhere in the codebase.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, `kotlinx.coroutines.flow`.

## Global Constraints

- This plan assumes **Phase 1 and Phase 2 are complete and merged**. If `CrucibleRepository` does not already have `observeResource`, `observeProjects`, `observeInstruments`, `observeProjectSamples`, `observeProjectDatasets`, `observeInstrumentDatasets`/`fetchInstrumentDatasets`, `observeThumbnails`, or `fetchSiblings`, stop and verify Phases 1–2 landed correctly first.
- TTL/size caps for the two new caches added in Task 1 carry over unchanged from `CacheManager.kt`: `MAX_DATASET_FILES_CACHE_SIZE = 50`, `MAX_FILE_URL_CACHE_SIZE = 200`; file-URL TTL is **50 minutes** (`FILE_URL_TTL = 50 * 60 * 1000L`, not the standard 10-minute TTL — signed GCS URLs expire at 1 hour, so this TTL is deliberately shorter to always serve a URL with time left on it), dataset-files TTL is the standard 10 minutes.
- Every ViewModel constructor change in this plan (removing `cacheManager: CacheManager`) requires no `AppModule.kt` edit — Koin's `viewModelOf(::X)` resolves constructor parameters by type automatically (confirmed in Phase 2 Task 3 Step 3).
- No `Date.now()`/`Math.random()`.
- Every new suspend function that can throw follows `catch (e: CancellationException) { throw e }` before a general `catch`.
- Do not change `ResourceDetailScreen.kt`, `ResourceDetailViewModel.kt`, or `NavGraph.kt`'s `Screen.Detail` block — those were fully migrated in Phase 2 and are out of scope here.
- Do not change `FilterSheet.kt` or `InstrumentPickerField.kt` — verified in this plan's research to use only `koinInject<ApiClient>()` for live search calls, with no `CacheManager` dependency at all.

## File Structure

- **Modify**: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt` — add `observeDatasetFiles`/`fetchDatasetFiles`/`invalidateDatasetFiles` and `observeFileUrl`/`fetchFileUrl`/`invalidateFileUrl`.
- **Modify**: `HomeScreen.kt`, `ProjectsListScreen.kt`, `ProjectsListViewModel.kt`, `ProjectDetailViewModel.kt`, `ProjectDetailScreen.kt`, `ManageProjectViewModel.kt`, `InstrumentListViewModel.kt`, `InstrumentDetailViewModel.kt`, `InstrumentDetailScreen.kt`, `ManageInstrumentViewModel.kt`, `CreateEditViewModels.kt` (3 ViewModels), `EditResourceSheet.kt`, `AssociatedFilesCard.kt`, `LinkResourceSheet.kt`, `AccountViewModel.kt`, `CacheSettingsScreen.kt` — each swaps `CacheManager` usage for `CrucibleRepository`.
- **Delete**: `app/src/commonMain/kotlin/crucible/lens/data/cache/CacheManager.kt`.
- **Modify**: `app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt` — remove `single { CacheManager() }`.

---

### Task 1: Add dataset-files and file-URL caching to `CrucibleRepository`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` (Phase 1), `CrucibleApiService.getDatasetFiles(uuid)`/`getFileDownloadLink(mfid)` (existing, unchanged signatures — `CrucibleApiService.kt:362` and the `getFileDownloadLink` method used by `AssociatedFilesCard.kt` today).
- Produces:
  - `suspend fun fetchDatasetFiles(uuid: String, forceRefresh: Boolean = false): ApiResult<List<AssociatedFile>>`
  - `fun observeDatasetFiles(uuid: String): Flow<List<AssociatedFile>?>`
  - `fun invalidateDatasetFiles(uuid: String)`
  - `suspend fun fetchFileUrl(mfid: String, forceRefresh: Boolean = false): ApiResult<String>` (wraps `getFileDownloadLink`'s `ApiResult<FileDownloadLinkResponse>` down to just the `.url` string, matching what `AssociatedFilesCard` actually needs)
  - `fun observeFileUrl(mfid: String): Flow<String?>`
  - `fun invalidateFileUrl(mfid: String)`
  - (internal) `private val datasetFilesObservableCache = ObservableCache<String, List<AssociatedFile>>(ttlMillis = 10 * 60 * 1000L, maxSize = 50)`
  - (internal) `private val fileUrlObservableCache = ObservableCache<String, String>(ttlMillis = 50 * 60 * 1000L, maxSize = 200)`

- [ ] **Step 1: Confirm `getFileDownloadLink`'s exact return type**

Run: `grep -n "fun getFileDownloadLink" -A5 app/src/commonMain/kotlin/crucible/lens/data/api/CrucibleApiService.kt`
Read the output to confirm the response type's field name for the URL (expected to be `.url` based on `AssociatedFilesCard.kt`'s existing `r.data.url` usage) before writing Step 4 below. If the field name differs from `url`, adjust Step 4's `.url` reference accordingly.

- [ ] **Step 2: Write the failing tests**

Add to `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` (after the last test from Phase 2's Task 1):

```kotlin
    @Test
    fun observeDatasetFilesEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeDatasetFiles("dataset-uuid").first())
    }

    @Test
    fun invalidateDatasetFilesIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateDatasetFiles("dataset-uuid")
    }

    @Test
    fun observeFileUrlEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeFileUrl("some-mfid").first())
    }

    @Test
    fun invalidateFileUrlIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateFileUrl("some-mfid")
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — the four new methods do not exist yet.

- [ ] **Step 4: Implement the new fields and methods**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add the required import (alongside existing imports):

```kotlin
import crucible.lens.data.model.AssociatedFile
```

Add two new fields directly after `thumbnailObservableCache` (from Phase 2 Task 1):

```kotlin
    private val datasetFilesObservableCache = ObservableCache<String, List<AssociatedFile>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )
    // 50-min TTL — deliberately shorter than the standard 10-min TTL is NOT what this is;
    // it is LONGER, because signed GCS URLs are valid for 1 hour and this keeps served URLs
    // safely under that expiry with margin, matching CacheManager.FILE_URL_TTL today.
    private val fileUrlObservableCache = ObservableCache<String, String>(
        ttlMillis = 50 * 60 * 1000L,
        maxSize = 200
    )
```

Add the six new methods to the class body, after `invalidateInstrumentDatasets` (Phase 1 Task 6) / `fetchSiblings` (Phase 2 Task 1, whichever is later in the file):

```kotlin
    suspend fun fetchDatasetFiles(uuid: String, forceRefresh: Boolean = false): ApiResult<List<AssociatedFile>> {
        if (!forceRefresh) {
            datasetFilesObservableCache.get(uuid)?.let { return ApiResult.Success(it) }
        }
        return api.getDatasetFiles(uuid).also { result ->
            if (result is ApiResult.Success) datasetFilesObservableCache.put(uuid, result.data)
        }
    }

    fun observeDatasetFiles(uuid: String): Flow<List<AssociatedFile>?> = datasetFilesObservableCache.observe(uuid)

    fun invalidateDatasetFiles(uuid: String) = datasetFilesObservableCache.invalidate(uuid)

    suspend fun fetchFileUrl(mfid: String, forceRefresh: Boolean = false): ApiResult<String> {
        if (!forceRefresh) {
            fileUrlObservableCache.get(mfid)?.let { return ApiResult.Success(it) }
        }
        return when (val result = api.getFileDownloadLink(mfid)) {
            is ApiResult.Success -> {
                fileUrlObservableCache.put(mfid, result.data.url)
                ApiResult.Success(result.data.url)
            }
            is ApiResult.Error -> result
        }
    }

    fun observeFileUrl(mfid: String): Flow<String?> = fileUrlObservableCache.observe(mfid)

    fun invalidateFileUrl(mfid: String) = fileUrlObservableCache.invalidate(mfid)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` — this task only adds new methods.

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: add dataset-files and file-URL caching"
```

---

### Task 2: Migrate `HomeScreen.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `CrucibleRepository.fetchProjects`, `.fetchInstruments`, `.fetchProjectData` (all already exist with these exact signatures per Phase 1), plus the still-needed `ApiClient` for the direct `apiClient.service.getProjects()`/`getInstruments()` calls this screen makes today (these become `repository.fetchProjects(forceRefresh = false)`/`repository.fetchInstruments()` instead, per the note below).
- `cacheManager` is removed as a local `val` (line 83) — `apiClient` is also removed if nothing else in the file needs it after this change (verify in Step 3).

Today, `HomeScreen.kt` (lines 110-135) manually replicates repository-style cache-then-fetch logic inline: check `cacheManager.getProjects()`, else call `apiClient.service.getProjects()` and `cacheManager.cacheProjects(...)`. This is now exactly what `repository.fetchProjects(forceRefresh = false)` does — the whole block collapses to a single repository call.

- [ ] **Step 1: Replace the project-list fetch effect**

In `app/src/commonMain/kotlin/crucible/lens/ui/home/HomeScreen.kt`, replace the `LaunchedEffect(apiKey, retryTrigger) { ... }` block (currently lines 110-135):

```kotlin
    LaunchedEffect(apiKey, retryTrigger) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        when (val response = repository.fetchProjects()) {
            is crucible.lens.data.api.ApiResult.Success -> {
                allProjects = response.data
                fetchError = null
            }
            is crucible.lens.data.api.ApiResult.Error -> {
                fetchError = response.message
            }
        }
    }
```

Note: the old code's `try/catch` around the network call is no longer needed here — `CrucibleRepository.fetchProjects` (and every other `fetchX` method) already wraps its own network call in `safeCall { }` at the `CrucibleApiService` layer (see `CrucibleApiService.kt:684-692`), converting exceptions to `ApiResult.Error` before this screen ever sees them.

- [ ] **Step 2: Replace the instrument-list fetch effect**

Replace the `LaunchedEffect(apiKey) { ... }` block that fetches instruments (currently lines 137-146):

```kotlin
    LaunchedEffect(apiKey) {
        if (apiKey.isNullOrBlank()) return@LaunchedEffect
        repository.fetchInstruments()
    }
```

- [ ] **Step 3: Replace the preload effect's `PersistentProjectCache.save` call site and remove `cacheManager`/`apiClient` if unused**

The `LaunchedEffect(allProjects, pinnedProjects) { ... }` block (currently lines 148-183) already calls `repository.fetchProjectData(project.projectId)` — this does not need to change. Its `PersistentProjectCache.save(platformContext, allProjects)` call also does not change (that's Phase 1/2/3-unrelated disk persistence, out of scope).

After Steps 1–2, check whether `apiClient` (line 82) or `cacheManager` (line 83) are still referenced anywhere in the file:

```bash
grep -n "apiClient\.\|cacheManager\." app/src/commonMain/kotlin/crucible/lens/ui/home/HomeScreen.kt
```

Expected: zero matches for both after Steps 1–2. Remove both `val apiClient = koinInject<ApiClient>()` (line 82) and `val cacheManager = koinInject<CacheManager>()` (line 83), and remove the now-unused imports `import crucible.lens.data.api.ApiClient` (line 38) and `import crucible.lens.data.cache.CacheManager` (line 39).

- [ ] **Step 4: Update the pinned-instruments lookup**

The `pinnedInstrumentList` computation (currently lines 188-190) reads `cacheManager.getInstruments()`, which no longer exists as a local val after Step 3. Replace:

```kotlin
    val pinnedInstrumentList = remember(pinnedInstruments) {
        cacheManager.getInstruments()?.filter { it.uniqueId in pinnedInstruments } ?: emptyList()
    }
```

with a reactive read from the repository:

```kotlin
    val allInstruments by repository.observeInstruments().collectAsStateWithLifecycle(initial = null)
    val pinnedInstrumentList = remember(pinnedInstruments, allInstruments) {
        allInstruments?.filter { it.uniqueId in pinnedInstruments } ?: emptyList()
    }
```

Add the required import: `import androidx.lifecycle.compose.collectAsStateWithLifecycle`.

- [ ] **Step 5: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`, no new warnings (verify no unused-import warnings remain for `ApiClient`/`CacheManager` in this file).

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/home/HomeScreen.kt
git commit -m "HomeScreen: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 3: Migrate `ProjectsListViewModel.kt` and `ProjectsListScreen.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListViewModel.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListScreen.kt`

**Interfaces:**
- `ProjectsListViewModel` constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val repository: CrucibleRepository)`.
- `ProjectsListScreen`'s local `cacheManager` (line 68) is replaced by using the already-injected `repository` (line 67) for the search-matching reads.

- [ ] **Step 1: Rewrite `ProjectsListViewModel.load()`**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListViewModel.kt`:

```kotlin
package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Project
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectsListViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Project>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Project>>> = _loadState.asStateFlow()

    /** Per-project sample/dataset counts populated by background preloading. */
    private val _projectCounts = MutableStateFlow<Map<String, Pair<Int?, Int?>>>(emptyMap())
    val projectCounts: StateFlow<Map<String, Pair<Int?, Int?>>> = _projectCounts.asStateFlow()

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    val current = (_loadState.value as? LoadState.Success)?.data ?: emptyList()
                    _loadState.value = if (forceRefresh) LoadState.Success(current, isRefreshing = true)
                                       else LoadState.Loading
                    if (forceRefresh) _projectCounts.value = emptyMap()
                }

                when (val resp = repository.fetchProjects(forceRefresh)) {
                    is ApiResult.Success -> {
                        withContext(Dispatchers.Main) {
                            _loadState.value = LoadState.Success(resp.data)
                            _projectCounts.update { counts ->
                                counts + resp.data.associate { it.projectId to Pair<Int?, Int?>(null, null) }
                                    .filter { it.key !in counts }
                            }
                        }
                    }
                    is ApiResult.Error -> withContext(Dispatchers.Main) {
                        _loadState.value = LoadState.Error("Failed to load projects: ${resp.message}")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                withContext(Dispatchers.Main) { _loadState.value = LoadState.Error("Error: ${e.message}") }
            }
        }
    }

    /** Called by background preloading when counts arrive for a project. */
    fun updateCount(projectId: String, sampleCount: Int, datasetCount: Int) {
        _projectCounts.update { it + (projectId to Pair(sampleCount, datasetCount)) }
    }
}
```

Note: the old `!forceRefresh` cache-check branch (early-return using `cacheManager.getProjects()` before touching `_loadState`) is gone because `repository.fetchProjects(forceRefresh)` already does that exact cache-check internally (Phase 1's implementation: `if (!forceRefresh) { projectsObservableCache.get(Unit)?.let { return ApiResult.Success(it) } }`) — duplicating it here would be redundant. The visible behavior is identical: a cache hit returns immediately without a network call either way, the only difference is which layer performs the check. Also removed: `cacheManager.clearAll()` on `forceRefresh` (the old code's line 48) — this cleared the ENTIRE app cache (projects, resources, instruments, everything) just to force-refresh the projects list, which was overly broad; `repository.fetchProjects(forceRefresh = true)` alone already bypasses its own cache and refetches, which is the correct, narrower scope for "refresh the projects list."

- [ ] **Step 2: Update `ProjectsListScreen.kt`'s search-matching reads**

In `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListScreen.kt`, remove the `cacheManager` local val (line 68: `val cacheManager = koinInject<CacheManager>()`) and its import (`import crucible.lens.data.cache.CacheManager`, line 30).

Replace the two `cacheManager.getProjectSamples`/`cacheManager.getProjectDatasets` calls (currently lines 293 and 297):

```kotlin
                                    // Search in cached samples
                                    val matchesSamples = repository.getCachedProjectSamples(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true

                                    // Search in cached datasets (including metadata)
                                    val matchesDatasets = repository.getCachedProjectDatasets(project.projectId)
                                        ?.any { it.matchesSearch(searchQuery) } == true
```

`CrucibleRepository` does not yet have synchronous `getCachedProjectSamples`/`getCachedProjectDatasets` methods (Phase 1 only added `observeProjectSamples`/`observeProjectDatasets`, which are `Flow`-based, not usable inline in a `filter {}` lambda). Add these two synchronous accessors to `CrucibleRepository.kt` in this task (a small addition, since the underlying `ObservableCache.get()` method already exists from Phase 1 — this just exposes it under the project-scoped name):

```kotlin
    fun getCachedProjectSamples(projectId: String): List<Sample>? = projectSamplesObservableCache.get(projectId)

    fun getCachedProjectDatasets(projectId: String): List<Dataset>? = projectDatasetsObservableCache.get(projectId)
```

Add these two methods to `CrucibleRepository.kt` directly after `observeProjectDatasets` (from Phase 1 Task 5).

- [ ] **Step 3: Verify the files compile**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListViewModel.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectsListScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt
git commit -m "ProjectsListViewModel/Screen: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 4: Migrate `ProjectDetailViewModel.kt` and `ProjectDetailScreen.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailViewModel.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailScreen.kt`

**Interfaces:**
- `ProjectDetailViewModel` constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val repository: CrucibleRepository)`.
- `ProjectDetailScreen`'s three `cacheManager` local vals (lines 201, 681, 779) are each replaced.

- [ ] **Step 1: Rewrite `ProjectDetailViewModel.load()`**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailViewModel.kt`:

```kotlin
package crucible.lens.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Sample
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProjectContent(val samples: List<Sample>, val datasets: List<Dataset>)

class ProjectDetailViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<ProjectContent>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<ProjectContent>> = _loadState.asStateFlow()

    private var currentProjectId: String? = null

    fun load(projectId: String, isHidden: Boolean = false, forceRefresh: Boolean = false) {
        if (projectId == currentProjectId && !forceRefresh &&
            _loadState.value is LoadState.Success) return
        currentProjectId = projectId
        viewModelScope.launch {
            try {
                val current = (_loadState.value as? LoadState.Success)?.data
                _loadState.value = if (forceRefresh && current != null)
                    LoadState.Success(current, isRefreshing = true)
                else LoadState.Loading

                if (isHidden) {
                    val cachedSamples = repository.getCachedProjectSamples(projectId)
                    val cachedDatasets = repository.getCachedProjectDatasets(projectId)
                    val fromCache = cachedSamples != null && cachedDatasets != null
                    _loadState.value = LoadState.Success(
                        ProjectContent(cachedSamples ?: emptyList(), cachedDatasets ?: emptyList()),
                        fromCache = fromCache
                    )
                    return@launch
                }

                val (samples, datasets) = repository.fetchProjectData(projectId, forceRefresh)
                _loadState.value = LoadState.Success(ProjectContent(samples, datasets))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
```

Note: the old code's separate `cachedSamples`/`cachedDatasets` cache-check before deciding whether to call the network (lines 42-50 of the original) is now handled entirely inside `repository.fetchProjectData` (Phase 1's implementation already does cache-then-network with the per-project mutex). The `isHidden` branch is kept distinct because its semantics differ from the normal path — it's fine to return partial/stale data or nothing rather than blocking on a network fetch for a hidden project, which `fetchProjectData` does not support as a mode; this branch continues to use the two synchronous cache reads directly (added in Task 3 Step 2), which is the correct approach here.

- [ ] **Step 2: Update `ProjectDetailScreen.kt`'s three `cacheManager` usages**

In `app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailScreen.kt`:

Remove the import `import crucible.lens.data.cache.CacheManager` (line 50).

Replace the first usage, at line 201, inside the main `ProjectDetailScreen` composable (not the same scope as the unrelated `apiClient` at line 106, which lives inside the separate private `rememberOwnerNames` composable and is out of scope for this task):

```kotlin
    val repository = koinInject<CrucibleRepository>()
    val project = remember(projectId) {
        repository.getCachedProjects()?.find { it.projectId == projectId }
    }
```

`CrucibleRepository` does not yet have a synchronous `getCachedProjects()` accessor (only `observeProjects()`, `Flow`-based). Add it to `CrucibleRepository.kt` in this task, directly after `observeProjects` (Phase 1 Task 4):

```kotlin
    fun getCachedProjects(): List<Project>? = projectsObservableCache.get(Unit)
```

Remove the two `cacheManager.getProjectDataAgeMinutes(projectId)` usages (currently lines 745 and 846). `CrucibleRepository` does not yet expose an age accessor scoped to project data specifically — Phase 1 only added `resourceAgeMillis` for individual resources. Since these two call sites are cosmetic "Cached Nm ago" labels (same category as the one removed in Phase 2 Task 4 Step 6 for `ResourceDetailScreen`), apply the same resolution here rather than adding a new repository method for a cosmetic detail. Both occurrences have the identical structure — delete the entire enclosing `if (fromCache) { ... }` block at each location:

```kotlin
                if (fromCache) {
                    val ageMin = cacheManager.getProjectDataAgeMinutes(projectId) ?: 0
                    item(key = "cache_age") {
                        Text(
                            text = "Cached ${ageMin}m ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
```

Delete this whole block at both line 744-755 (inside the private `SamplesList` composable) and line 844-856 (inside the private `DatasetsList` composable) — confirmed via direct read of both locations that they are structurally identical `LazyColumn` items gated on the same `fromCache` boolean, so no other code depends on `ageMin` surviving.

After this removal, the `fromCache: Boolean = false` parameter (declared at line 674 in `SamplesList` and line 772 in `DatasetsList`) has no remaining usage within either function body. Remove the parameter from both function signatures, and remove the two `fromCache = (loadState as? LoadState.Success)?.fromCache ?: false` arguments passed at the call sites (lines 395 and 405) — otherwise this becomes a dead, unused parameter that Kotlin will warn about.

Remove the two now-orphaned `val cacheManager = koinInject<CacheManager>()` lines at 681 and 779 entirely (both were only used for the now-removed age lookups — verify with `grep -n "cacheManager\." app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailScreen.kt` returning zero results after this step).

- [ ] **Step 3: Verify the files compile**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailViewModel.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/projects/ProjectDetailScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt
git commit -m "ProjectDetailViewModel/Screen: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 5: Migrate `ManageProjectViewModel.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/projects/ManageProjectViewModel.kt`

**Interfaces:**
- Constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val apiClient: ApiClient, private val repository: CrucibleRepository)` — `apiClient` is kept because this ViewModel makes many direct `apiClient.service.X()` calls (`updateProject`, `searchUsers`, `addProjectMember`, `removeProjectMember`) that have no repository equivalent and are correctly out of scope (they're one-shot mutations, not cached reads).

- [ ] **Step 1: Update the constructor and the one `cacheManager` call site**

In `app/src/commonMain/kotlin/crucible/lens/ui/projects/ManageProjectViewModel.kt`, change the import:

```kotlin
import crucible.lens.data.repository.CrucibleRepository
```

(remove `import crucible.lens.data.cache.CacheManager`)

Change the constructor (currently lines 36-39):

```kotlin
class ManageProjectViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

Replace the single `cacheManager.clearProjectsCache()` call (currently line 140):

```kotlin
                    repository.invalidateProjects()
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/projects/ManageProjectViewModel.kt
git commit -m "ManageProjectViewModel: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 6: Migrate `InstrumentListViewModel.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentListViewModel.kt`

**Interfaces:**
- Constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val repository: CrucibleRepository)`.

- [ ] **Step 1: Rewrite the file**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentListViewModel.kt`:

```kotlin
package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Instrument
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstrumentListViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Instrument>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Instrument>>> = _loadState.asStateFlow()

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                val current = (_loadState.value as? LoadState.Success)?.data ?: emptyList()
                _loadState.value = LoadState.Success(current, isRefreshing = true)
            } else {
                _loadState.value = LoadState.Loading
            }
            try {
                when (val resp = repository.fetchInstruments(forceRefresh)) {
                    is ApiResult.Success -> _loadState.value = LoadState.Success(resp.data)
                    is ApiResult.Error -> _loadState.value = LoadState.Error("Failed to load instruments")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentListViewModel.kt
git commit -m "InstrumentListViewModel: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 7: Migrate `InstrumentDetailViewModel.kt` and `InstrumentDetailScreen.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailViewModel.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailScreen.kt`

**Interfaces:**
- `InstrumentDetailViewModel` constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val apiClient: ApiClient, private val repository: CrucibleRepository)` — `apiClient` is kept for the direct `getInstrument(instrumentId)` call (no repository equivalent needed for a single-instrument-by-id lookup; Phase 1 only added list-level `fetchInstruments`).
- `InstrumentDetailScreen`'s `cacheManager` local (line 78) is replaced.

- [ ] **Step 1: Rewrite `InstrumentDetailViewModel.load()`**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailViewModel.kt`:

```kotlin
package crucible.lens.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import crucible.lens.data.api.ApiClient
import crucible.lens.data.api.ApiResult
import crucible.lens.data.model.Dataset
import crucible.lens.data.model.Instrument
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.LoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InstrumentDetailViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _instrument = MutableStateFlow<Instrument?>(null)
    val instrument: StateFlow<Instrument?> = _instrument.asStateFlow()

    private val _datasetsState = MutableStateFlow<LoadState<List<Dataset>>>(LoadState.Loading)
    val datasetsState: StateFlow<LoadState<List<Dataset>>> = _datasetsState.asStateFlow()

    private var currentInstrumentId: String? = null

    fun load(instrumentId: String, forceRefresh: Boolean = false) {
        if (instrumentId == currentInstrumentId && !forceRefresh &&
            _datasetsState.value is LoadState.Success) return
        currentInstrumentId = instrumentId
        viewModelScope.launch {
            if (forceRefresh) {
                val current = (_datasetsState.value as? LoadState.Success)?.data ?: emptyList()
                _datasetsState.value = LoadState.Success(current, isRefreshing = true)
            } else {
                _datasetsState.value = LoadState.Loading
            }
            try {
                val resolvedInstrument = if (!forceRefresh) {
                    repository.getCachedInstruments()?.find { it.uniqueId == instrumentId }
                        ?: (apiClient.service.getInstrument(instrumentId) as? ApiResult.Success)?.data
                } else {
                    (apiClient.service.getInstrument(instrumentId) as? ApiResult.Success)?.data
                }
                if (resolvedInstrument == null) {
                    _datasetsState.value = LoadState.Error("Instrument not found")
                    return@launch
                }
                _instrument.value = resolvedInstrument
                val instrName = resolvedInstrument.instrumentName ?: resolvedInstrument.uniqueId
                when (val resp = repository.fetchInstrumentDatasets(instrName, forceRefresh)) {
                    is ApiResult.Success -> _datasetsState.value = LoadState.Success(resp.data)
                    is ApiResult.Error -> _datasetsState.value = LoadState.Error("Failed to load datasets")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _datasetsState.value = LoadState.Error("Connection error — check your network")
            }
        }
    }
}
```

`CrucibleRepository` does not yet have a synchronous `getCachedInstruments()` accessor. Add it to `CrucibleRepository.kt` in this task, directly after `observeInstruments` (Phase 1 Task 4):

```kotlin
    fun getCachedInstruments(): List<Instrument>? = instrumentsObservableCache.get(Unit)
```

- [ ] **Step 2: Update `InstrumentDetailScreen.kt`'s two `cacheManager` usages**

In `app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailScreen.kt`, remove the import `import crucible.lens.data.cache.CacheManager` (line 35) and replace `val cacheManager = koinInject<CacheManager>()` (line 78) with `val repository = koinInject<CrucibleRepository>()`. Add the import `import crucible.lens.data.repository.CrucibleRepository`.

Replace the `PROJECT` groupBy lookup (currently line 106):

```kotlin
                InstrumentDatasetGroupBy.PROJECT     -> d.projectId?.let { pid ->
                    repository.getCachedProjects()?.find { it.projectId == pid }?.title ?: pid
                } ?: "No project"
```

Replace the refresh menu item's cache-clear call (currently line 148):

```kotlin
                                RefreshMenuItem { overflowMenuExpanded = false; repository.invalidateInstruments(); viewModel.load(instrumentId, forceRefresh = true) }
```

- [ ] **Step 3: Verify the files compile**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailViewModel.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/instruments/InstrumentDetailScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt
git commit -m "InstrumentDetailViewModel/Screen: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 8: Migrate `ManageInstrumentViewModel.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/instruments/ManageInstrumentViewModel.kt`

**Interfaces:**
- Constructor changes from `(apiClient: ApiClient, cacheManager: CacheManager)` to `(private val apiClient: ApiClient, private val repository: CrucibleRepository)`.

- [ ] **Step 1: Update the constructor and the one `cacheManager` call site**

In `app/src/commonMain/kotlin/crucible/lens/ui/instruments/ManageInstrumentViewModel.kt`, change the import:

```kotlin
import crucible.lens.data.repository.CrucibleRepository
```

Change the constructor (currently lines 36-39):

```kotlin
class ManageInstrumentViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

Replace the single `cacheManager.clearInstrumentsCache()` call (currently line 106):

```kotlin
                    repository.invalidateInstruments()
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/instruments/ManageInstrumentViewModel.kt
git commit -m "ManageInstrumentViewModel: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 9: Migrate `CreateSampleViewModel`, `CreateDatasetViewModel`, `EditResourceViewModel` (`CreateEditViewModels.kt`)

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/create/CreateEditViewModels.kt`

**Interfaces:**
- All three ViewModel constructors change `cacheManager: CacheManager` to `repository: CrucibleRepository`.
- `cacheManager.cacheResource(uuid, resource)` → the repository doesn't have a public "just cache this" method that bypasses fetching (Phase 1's `resourceObservableCache` is private, only exposed via `fetchResourceByUuid`/`getCachedResource`/`invalidateResource`). Since these ViewModels already have the freshly-created/updated resource in hand (from the API response), the correct repository equivalent is a new `fun cacheResource(uuid: String, resource: CrucibleResource)` public method — add this to `CrucibleRepository` in this task.
- `cacheManager.clearProjectDetail(projectId)` → `repository.invalidateProjectData(projectId)` (exists from Phase 1 Task 5).

- [ ] **Step 1: Add `cacheResource` to `CrucibleRepository`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add a new public method directly after `invalidateResource` (Phase 1 Task 3):

```kotlin
    /** Directly seeds the resource cache with an already-known-fresh value (e.g. from a create/update API response), bypassing a network fetch. */
    fun cacheResource(uuid: String, resource: CrucibleResource) = resourceObservableCache.put(uuid, resource)
```

- [ ] **Step 2: Update the three ViewModel constructors and call sites**

In `app/src/commonMain/kotlin/crucible/lens/ui/create/CreateEditViewModels.kt`, change the import:

```kotlin
import crucible.lens.data.repository.CrucibleRepository
```

(remove `import crucible.lens.data.cache.CacheManager`)

For `CreateSampleViewModel` (currently lines 33-35), change:

```kotlin
class CreateSampleViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

and its two call sites (currently lines 49-50):

```kotlin
                        repository.cacheResource(sample.uniqueId, sample)
                        projectId?.let { repository.invalidateProjectData(it) }
```

For `CreateDatasetViewModel` (currently lines 71-73), change:

```kotlin
class CreateDatasetViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

and its two call sites (currently lines 91-92):

```kotlin
                cacheManager.cacheResource(newUuid, newDataset)
                request.projectId?.let { cacheManager.clearProjectDetail(it) }
```
becomes:
```kotlin
                repository.cacheResource(newUuid, newDataset)
                request.projectId?.let { repository.invalidateProjectData(it) }
```

For `EditResourceViewModel` (currently lines 165-167), change:

```kotlin
class EditResourceViewModel(
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

and its two call sites (currently lines 181 and 204):

```kotlin
                        repository.cacheResource(uuid, resp.data)
```
(applied identically at both locations).

- [ ] **Step 3: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/create/CreateEditViewModels.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt
git commit -m "CreateSampleViewModel/CreateDatasetViewModel/EditResourceViewModel: migrate off CacheManager"
```

---

### Task 10: Migrate `EditResourceSheet.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/detail/EditResourceSheet.kt`

**Interfaces:**
- Two `koinInject<CacheManager>()` call sites (lines 228, 286), each followed by `cacheManager.getProjects() ?: emptyList()` — replaced with `repository.getCachedProjects() ?: emptyList()` (the method added in Task 4 Step 2).

- [ ] **Step 1: Update both usages**

In `app/src/commonMain/kotlin/crucible/lens/ui/detail/EditResourceSheet.kt`, remove the import `import crucible.lens.data.cache.CacheManager` (line 16), add `import crucible.lens.data.repository.CrucibleRepository`.

Replace both occurrences (lines 228-229 and 286-287) of:

```kotlin
    val cacheManager = koinInject<CacheManager>()
    val projects = remember { cacheManager.getProjects() ?: emptyList() }
```

with:

```kotlin
    val repository = koinInject<CrucibleRepository>()
    val projects = remember { repository.getCachedProjects() ?: emptyList() }
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/detail/EditResourceSheet.kt
git commit -m "EditResourceSheet: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 11: Migrate `AssociatedFilesCard.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/detail/components/AssociatedFilesCard.kt`

**Interfaces:**
- Consumes: `CrucibleRepository.fetchDatasetFiles`, `.fetchFileUrl` (both from Task 1).
- The `fetch()` and `openFile()` local functions are rewritten to use the repository instead of `cacheManager`/`apiClient.service` directly.

- [ ] **Step 1: Rewrite the file**

Replace the entire contents of `app/src/commonMain/kotlin/crucible/lens/ui/detail/components/AssociatedFilesCard.kt`:

```kotlin
package crucible.lens.ui.detail.components
import crucible.lens.ui.common.AppIcon
import crucible.lens.ui.common.AppIconToken
import crucible.lens.ui.common.AppIcons

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crucible.lens.data.api.ApiResult
import crucible.lens.data.repository.CrucibleRepository
import crucible.lens.ui.common.ExpandChevron
import crucible.lens.ui.common.StandardSizeAnim
import crucible.lens.data.util.formatFileSize
import crucible.lens.platform.getPlatformContext
import crucible.lens.platform.openUrl
import crucible.lens.platform.shareText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal sealed class AssociatedFilesState {
    object Idle    : AssociatedFilesState()
    object Loading : AssociatedFilesState()
    object Empty   : AssociatedFilesState()
    data class Success(val files: List<crucible.lens.data.model.AssociatedFile>) : AssociatedFilesState()
    data class Err(val message: String) : AssociatedFilesState()
}

internal fun displayName(path: String): String = path.substringAfterLast('/').ifBlank { path }

internal fun fileIcon(name: String): AppIconToken {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "h5", "hdf5", "nc", "netcdf", "nxs" -> AppIcons.FileStorage
        "tiff", "tif", "png", "jpg", "jpeg", "bmp", "gif" -> AppIcons.FileImage
        "pdf" -> AppIcons.FilePdf
        "csv", "tsv", "txt", "dat", "log" -> AppIcons.Notes
        "json", "yaml", "yml", "xml", "toml" -> AppIcons.FileJson
        "zip", "tar", "gz", "bz2", "xz" -> AppIcons.FileArchive
        else -> AppIcons.FileGeneric
    }
}

@Composable
internal fun AssociatedFilesCard(
    datasetUuid: String,
    initialExpanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    var state by remember { mutableStateOf<AssociatedFilesState>(AssociatedFilesState.Idle) }
    val loadingFiles = remember { mutableStateMapOf<String, Boolean>() }
    val errorFiles = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val platformCtx = getPlatformContext()
    val repository = koinInject<CrucibleRepository>()

    fun fetch() {
        scope.launch {
            state = AssociatedFilesState.Loading
            loadingFiles.clear()
            when (val result = repository.fetchDatasetFiles(datasetUuid)) {
                is ApiResult.Success -> {
                    state = if (result.data.isEmpty()) AssociatedFilesState.Empty
                            else AssociatedFilesState.Success(result.data)
                }
                is ApiResult.Error -> {
                    state = if (result.code == 404) AssociatedFilesState.Empty
                            else AssociatedFilesState.Err(result.message)
                }
            }
        }
    }

    fun openFile(file: crucible.lens.data.model.AssociatedFile, share: Boolean) {
        scope.launch {
            loadingFiles[file.mfid] = true
            errorFiles.remove(file.mfid)
            try {
                val url = (repository.fetchFileUrl(file.mfid) as? ApiResult.Success)?.data
                if (url != null) {
                    val name = displayName(file.filename)
                    if (share) shareText(platformCtx, url, name) else openUrl(platformCtx, url)
                } else {
                    errorFiles[file.mfid] = true
                }
            } finally {
                loadingFiles.remove(file.mfid)
            }
        }
    }

    LaunchedEffect(datasetUuid) { fetch() }

    val filesState = state
    if (filesState !is AssociatedFilesState.Success) return

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Card {
            Column(modifier = Modifier.padding(16.dp).animateContentSize(StandardSizeAnim)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { val new = !expanded; expanded = new; onExpandedChange(new) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpandChevron(expanded = expanded, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    AppIcon(AppIcons.AttachFile, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Files (${filesState.files.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filesState.files.sortedBy { it.filename }.forEach { file ->
                            val name = displayName(file.filename)
                            val ingested = file.storagePath != null
                            val isLoadingFile = loadingFiles[file.mfid] == true
                            val hasError = errorFiles[file.mfid] == true
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AppIcon(fileIcon(name), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (file.size != null) {
                                        Text(formatFileSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isLoadingFile) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(1.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(32.dp))
                                } else if (hasError) {
                                    AppIcon(AppIcons.Unreachable, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Text("Unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 4.dp))
                                } else if (ingested) {
                                    IconButton(onClick = { openFile(file, false) }, modifier = Modifier.size(32.dp)) {
                                        AppIcon(AppIcons.Download, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { openFile(file, true) }, modifier = Modifier.size(32.dp)) {
                                        AppIcon(AppIcons.Share, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                                    AppIcon(AppIcons.Pending, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/detail/components/AssociatedFilesCard.kt
git commit -m "AssociatedFilesCard: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 12: Migrate `LinkResourceSheet.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/detail/LinkResourceSheet.kt`

**Interfaces:**
- The one `cacheManager.getProjects()` usage (line 120) is replaced with `repository.getCachedProjects()`.

- [ ] **Step 1: Update the usage**

In `app/src/commonMain/kotlin/crucible/lens/ui/detail/LinkResourceSheet.kt`, remove the import `import crucible.lens.data.cache.CacheManager` (line 28), add `import crucible.lens.data.repository.CrucibleRepository`.

Replace `val cacheManager = koinInject<CacheManager>()` (line 52) with `val repository = koinInject<CrucibleRepository>()`.

Replace the usage (currently line 120):

```kotlin
        cacheManager.getProjects()?.associate { it.projectId to (it.title ?: it.projectId) } ?: emptyMap<String, String>()
```

with:

```kotlin
        repository.getCachedProjects()?.associate { it.projectId to (it.title ?: it.projectId) } ?: emptyMap<String, String>()
```

- [ ] **Step 2: Verify the file compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/detail/LinkResourceSheet.kt
git commit -m "LinkResourceSheet: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 13: Migrate `AccountViewModel.kt` and `CacheSettingsScreen.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/settings/AccountViewModel.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/settings/CacheSettingsScreen.kt`

**Interfaces:**
- `AccountViewModel`'s two `cacheManager.clearAll()` calls (lines 192, 209) need a repository-wide "invalidate everything" method — add `fun invalidateAll()` to `CrucibleRepository` that calls every `ObservableCache.invalidateAll()` in one place.
- `CacheSettingsScreen`'s `CacheManager.CacheStats`/`getDetailedStats()`/`getProjectsAgeMinutes()` are UI-facing diagnostics with no direct repository equivalent — Task adds an equivalent `CrucibleRepository.CacheStats` data class and `getDetailedStats()`/`getProjectsAgeMinutes()` methods, reading from the repository's own `ObservableCache` fields.

- [ ] **Step 1: Add `invalidateAll()` to `CrucibleRepository`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add a new method at the end of the class body (after the last method added in prior tasks):

```kotlin
    /** Clears every cache this repository holds — used on sign-out and API key/base-URL change. */
    fun invalidateAll() {
        resourceObservableCache.invalidateAll()
        projectsObservableCache.invalidateAll()
        instrumentsObservableCache.invalidateAll()
        projectSamplesObservableCache.invalidateAll()
        projectDatasetsObservableCache.invalidateAll()
        instrumentDatasetsObservableCache.invalidateAll()
        thumbnailObservableCache.invalidateAll()
        datasetFilesObservableCache.invalidateAll()
        fileUrlObservableCache.invalidateAll()
    }
```

- [ ] **Step 2: Update `AccountViewModel.kt`**

In `app/src/commonMain/kotlin/crucible/lens/ui/settings/AccountViewModel.kt`, remove the import `import crucible.lens.data.cache.CacheManager`, add `import crucible.lens.data.repository.CrucibleRepository`.

Change the constructor (currently lines 56-60):

```kotlin
class AccountViewModel(
    private val prefs: AppPreferences,
    private val apiClient: ApiClient,
    private val repository: CrucibleRepository
) : ViewModel() {
```

Replace both `cacheManager.clearAll()` calls (currently lines 192 and 209) with `repository.invalidateAll()`.

- [ ] **Step 3: Add `CacheStats`, `getDetailedStats()`, `getProjectsAgeMinutes()` to `CrucibleRepository`**

`ObservableCache` (Phase 1) does not expose its internal map size or entry ages beyond the single-key `ageMillis(key)` method — `getDetailedStats()` needs aggregate counts across all cached entries per type. Rather than add a broad introspection API to `ObservableCache` itself (which would leak internal state for a single UI screen's benefit), add a package-internal `size()` method to `ObservableCache` (Phase 1's class) that `CrucibleRepository` can use:

In `app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt`, add one method to the `ObservableCache` class (after `ageMillis`):

```kotlin
    /** Current entry count, including any expired-but-not-yet-evicted entries. */
    fun size(): Int = state.value.size

    /** Snapshot of all current values (including expired ones) — used for aggregate stats only. */
    fun values(): List<V> = state.value.values.map { it.value }
```

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add the data class and two methods at the end of the class body (after `invalidateAll`, Task 13 Step 1):

```kotlin
    data class CacheStats(
        val resourceCount: Int,
        val thumbnailCount: Int,
        val projectCount: Int,
        val cachedSampleCount: Int,
        val cachedDatasetCount: Int,
        val estimatedSizeKB: Long
    )

    fun getDetailedStats(): CacheStats {
        val cachedSampleCount = projectSamplesObservableCache.values().sumOf { it.size }
        val cachedDatasetCount = projectDatasetsObservableCache.values().sumOf { it.size }
        val thumbnailSizeBytes = thumbnailObservableCache.values().sumOf { list ->
            list.sumOf { it.thumbnailB64.length.toLong() }
        }
        val estimatedSizeKB =
            resourceObservableCache.size() * 3L +
            thumbnailSizeBytes / 1024L +
            (cachedSampleCount + cachedDatasetCount).toLong()
        return CacheStats(
            resourceCount = resourceObservableCache.size(),
            thumbnailCount = thumbnailObservableCache.size(),
            projectCount = projectsObservableCache.get(Unit)?.size ?: 0,
            cachedSampleCount = cachedSampleCount,
            cachedDatasetCount = cachedDatasetCount,
            estimatedSizeKB = estimatedSizeKB
        )
    }

    fun getProjectsAgeMinutes(): Long? = projectsObservableCache.ageMillis(Unit)?.let { it / 60000 }
```

- [ ] **Step 4: Update `CacheSettingsScreen.kt`**

In `app/src/commonMain/kotlin/crucible/lens/ui/settings/CacheSettingsScreen.kt`, remove the import `import crucible.lens.data.cache.CacheManager`, add `import crucible.lens.data.repository.CrucibleRepository`.

Replace `val cacheManager = koinInject<CacheManager>()` (line 30) with `val repository = koinInject<CrucibleRepository>()`.

Replace the `var cacheStats by remember { mutableStateOf<CacheManager.CacheStats?>(null) }` declaration (line 32):

```kotlin
    var cacheStats by remember { mutableStateOf<CrucibleRepository.CacheStats?>(null) }
```

Replace the `LaunchedEffect(Unit)` body (lines 34-37):

```kotlin
    LaunchedEffect(Unit) {
        cacheAge = repository.getProjectsAgeMinutes()
        cacheStats = repository.getDetailedStats()
    }
```

Replace the "Clear All Cache" button's `onClick` (lines 115-118):

```kotlin
                onClick = {
                    repository.invalidateAll()
                    cacheAge = null
                    cacheStats = repository.getDetailedStats()
```

- [ ] **Step 5: Verify the files compile**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/settings/AccountViewModel.kt \
        app/src/commonMain/kotlin/crucible/lens/ui/settings/CacheSettingsScreen.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt
git commit -m "AccountViewModel/CacheSettingsScreen: migrate off CacheManager onto CrucibleRepository"
```

---

### Task 14: Update remaining `NavGraph.kt` cache-invalidation call sites and `AppModule.kt`, delete `CacheManager.kt`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt`
- Modify: `app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt`
- Delete: `app/src/commonMain/kotlin/crucible/lens/data/cache/CacheManager.kt`

**Interfaces:**
- `NavGraph.kt` has two `cacheManager.clearAll()` call sites for API key/base-URL change (`onApiBaseUrlSave`, `onApiKeySave` — verified earlier at lines 351 and 363) — both become `repository.invalidateAll()`.
- `AppModule.kt` removes `single { CacheManager() }` and the `CrucibleRepository(get(), get())` constructor call becomes `CrucibleRepository(get())` (dropping the now-unused `CacheManager` dependency) — **only if** `CrucibleRepository`'s constructor itself no longer needs `CacheManager` after this phase. Verify this in Step 1 before editing `AppModule.kt`.

- [ ] **Step 1: Confirm `CrucibleRepository` no longer needs `CacheManager` in its constructor**

Run: `grep -n "cacheManager\." app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`

Expected: this should show only the two `cacheManager.cacheResourceType(...)` calls inside `fetchProjectData` (from Phase 1 Task 5, deliberately left as `CacheManager`'s responsibility since `resourceTypeCache` is never read anywhere in the codebase — confirmed in Phase 1's plan). Since `CacheManager` is being deleted entirely in this task, `resourceTypeCache`'s write-only, never-read cache must now be removed too rather than migrated (migrating a cache that's never read would be pointless — the correct fix, confirmed by re-checking `grep -rn "getResourceType" app/src/commonMain/` still returns zero read-sites, is deletion). Remove the two `it.forEach { s -> cacheManager.cacheResourceType(s.uniqueId, "sample") } `/`it.forEach { ds -> cacheManager.cacheResourceType(ds.uniqueId, "dataset") }` lines from `fetchProjectData` entirely (they become plain `?.also { projectSamplesObservableCache.put(projectId, it) }` / `?.also { projectDatasetsObservableCache.put(projectId, it) }` without the `.forEach` side effect).

After this removal, `CrucibleRepository`'s constructor `(private val apiClient: ApiClient, private val cacheManager: CacheManager)` no longer uses `cacheManager` anywhere in the class body. Change the constructor to `(private val apiClient: ApiClient)` and remove the `import crucible.lens.data.cache.CacheManager` line.

- [ ] **Step 2: Update `AppModule.kt`**

In `app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt`, remove the import `import crucible.lens.data.cache.CacheManager` and the line `single { CacheManager() }`.

Change `single { CrucibleRepository(get(), get()) }` to `single { CrucibleRepository(get()) }`.

- [ ] **Step 3: Update `NavGraph.kt`'s two remaining `cacheManager` call sites**

In `app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt`, remove the import `import crucible.lens.data.cache.CacheManager` (currently line 57) and the `val cacheManager = koinInject<CacheManager>()` line (currently line 107).

Replace the two `cacheManager.clearAll()` call sites (`onApiBaseUrlSave` at line 351 and the API-key save at line 363):

```kotlin
                onApiBaseUrlSave = { url -> scope.launch { prefs.saveApiBaseUrl(url); apiClient.setBaseUrl(url); repository.invalidateAll(); PersistentProjectCache.clear(platformCtx) } },
```

and:

```kotlin
                    scope.launch { prefs.saveApiKey(key); apiClient.setApiKey(key); repository.invalidateAll() }
```

(both replacing `cacheManager.clearAll()` with `repository.invalidateAll()`). `NavGraph.kt` does not currently inject `CrucibleRepository` anywhere (verified: `grep -n "koinInject<CrucibleRepository>" NavGraph.kt` returns no results) — add `val repository = koinInject<CrucibleRepository>()` directly after the existing `val apiClient = koinInject<ApiClient>()` (line 106), and add the import `import crucible.lens.data.repository.CrucibleRepository`.

- [ ] **Step 4: Delete `CacheManager.kt` and confirm zero remaining references**

```bash
git rm app/src/commonMain/kotlin/crucible/lens/data/cache/CacheManager.kt
```

Run: `grep -rn "CacheManager" app/src/commonMain/ app/src/androidMain/ app/src/iosMain/`
Expected: zero matches anywhere in the codebase. If any remain, resolve them before proceeding (they indicate a missed call site from an earlier task).

- [ ] **Step 5: Verify Android compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`, no new warnings.

- [ ] **Step 6: Verify iOS compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64 2>&1 | grep -E "^w:|^e:|BUILD"`
Expected: `BUILD SUCCESSFUL`, only the 4 pre-existing unrelated warnings.

- [ ] **Step 7: Run the full unit test suite**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`. Note: several `CrucibleRepositoryTest` tests from Phases 1-2 construct `CrucibleRepository(ApiClient(), CacheManager())` — since `CrucibleRepository`'s constructor now takes only `ApiClient`, update every test call site in `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` from `CrucibleRepository(ApiClient(), CacheManager())` to `CrucibleRepository(ApiClient())`, and remove the now-unused `import crucible.lens.data.cache.CacheManager` from the test file.

- [ ] **Step 8: Build and manually verify the debug APK**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Install and manually verify:
1. Sign in with an API key — profile loads correctly.
2. Browse projects list — loads, search works (including the sample/dataset content-match search from Task 3).
3. Open a project detail — samples/datasets load.
4. Browse instruments list, open an instrument detail — datasets load, grouping by project works.
5. Create a new sample — saves correctly, appears in project list afterward without a manual refresh being required (verifies `invalidateProjectData` still works).
6. Edit an existing sample/dataset — saves, resource detail reflects the update.
7. Open a dataset with associated files — file list loads, tapping a file's download/share button works (verifies the new `fetchDatasetFiles`/`fetchFileUrl` path).
8. Go to Settings → Cache — stats display correctly, "Clear All Cache" works and stats reset.
9. Change the API base URL or API key in Settings — app doesn't crash, subsequent screens reload fresh data.
10. Sign out and sign back in — no stale data from the previous account appears anywhere.

- [ ] **Step 9: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/ui/navigation/NavGraph.kt \
        app/src/commonMain/kotlin/crucible/lens/di/AppModule.kt \
        app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt
git add -u app/src/commonMain/kotlin/crucible/lens/data/cache/CacheManager.kt
git add app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "Delete CacheManager: CrucibleRepository is now the single source of cached data

Every screen and ViewModel now reads/writes cached data exclusively through
CrucibleRepository, backed by ObservableCache. Removes the resource-type
cache entirely (write-only, never read anywhere in the codebase). This is
the final phase of the caching unification — see docs/superpowers/plans/
2026-07-22-unified-cache-phase{1,2,3}-*.md for the full history."
```

---

## Verification (full phase)

1. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest` — all unit tests pass.
2. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain` — `BUILD SUCCESSFUL`, zero new warnings.
3. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64` — `BUILD SUCCESSFUL`, only the 4 pre-existing unrelated warnings.
4. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug` — `BUILD SUCCESSFUL`; all 10 manual checks in Task 14 Step 8 pass.
5. `grep -rn "CacheManager" app/src/` returns zero results anywhere in the codebase.
6. `find app/src -iname "CacheManager.kt"` returns nothing.
7. `grep -c "class ObservableCache" app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt` returns `1` — confirming exactly one caching primitive exists in the entire app.
