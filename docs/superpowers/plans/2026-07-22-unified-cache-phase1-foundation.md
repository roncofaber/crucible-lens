# Unified Cache — Phase 1: Foundation (ObservableCache + Reactive Repository)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `CacheManager`'s per-type `LinkedHashMap` caches with one generic, reactive, TTL-aware store (`ObservableCache<K, V>`), and give `CrucibleRepository` a full set of `observe*`/`fetch*` methods over every entity type (resources, projects, instruments, project sample/dataset lists, instrument dataset lists) so it becomes the single source of truth for cached data. This phase does **not** touch any screen or ViewModel — it only builds and unit-tests the foundation. Phases 2 and 3 (separate plans) migrate `ResourceDetailScreen`/`ViewModel`/`NavGraph` and the remaining screens onto this foundation.

**Architecture:** `ObservableCache<K, V>` wraps a `MutableStateFlow<Map<K, CachedEntry<V>>>` with the same TTL-expiry and LRU-eviction semantics `CacheManager` already has, but as one reusable class instead of one hand-written pair of methods per entity type. `CrucibleRepository` holds one `ObservableCache` instance per entity type and exposes `observeX(key): Flow<V?>` (reactive, TTL-aware, cache-then-network) alongside the existing one-shot `fetchX()` methods. `CacheManager` is deleted once all its callers are migrated in Phases 2–3 — in this phase it is left untouched and unused code is not yet removed, since `HomeScreen`/`ProjectsListScreen`/etc. still depend on it directly.

**Tech Stack:** Kotlin Multiplatform, `kotlinx.coroutines` (`MutableStateFlow`, `Mutex`), `kotlin.time.Clock` (already used in `CacheManager.kt`), `kotlin.test` (JVM execution via Android's `withHostTestBuilder`, verified working in this repo — see Task 1).

## Global Constraints

- TTL for resource/project/instrument-list entries: **10 minutes** (`CACHE_TTL = 10 * 60 * 1000L`, matches `CacheManager.kt:18` today).
- Per-type cache size caps carry over unchanged from `CacheManager.kt:20-25`: resources 50, thumbnails 20 (not touched in this phase — thumbnails stay in `CacheManager` until Phase 3), project details 30, instrument datasets 15.
- `CachedEntry<V>(val value: V, val timestamp: Long)` — same shape as `CacheManager.kt`'s existing `CachedItem<T>` (`CacheManager.kt:11-14`), defined fresh in the new `ObservableCache.kt` file under a distinct name so it does not collide with or require importing/modifying `CacheManager.kt`, which stays untouched in this phase.
- All new code lives in `app/src/commonMain/kotlin/crucible/lens/data/cache/` and `app/src/commonMain/kotlin/crucible/lens/data/repository/` — no `androidMain`/`iosMain` changes in this phase.
- No `Date.now()`/`Math.random()` — timestamps come from `kotlin.time.Clock.System.now().toEpochMilliseconds()`, exactly as `CacheManager.kt` does today.
- Every new suspend function that can throw follows the codebase's established `catch (e: CancellationException) { throw e }` before a general `catch` — see `CrucibleRepository.kt:68-72` for the existing pattern to match.
- Do not delete `CacheManager.kt`, `HomeScreen.kt`, `ProjectsListScreen.kt`, or any other current `CacheManager` consumer in this phase — they are out of scope and migrate in Phases 2/3.
- Do not change `ResourceDetailScreen.kt`, `ResourceDetailViewModel.kt`, or `NavGraph.kt` in this phase — that is Phase 2.

---

### Task 1: Enable and verify JVM-executable commonTest infrastructure

**Files:**
- Modify: `app/build.gradle.kts:36-49`

**Interfaces:**
- Produces: a working `:composeApp:testAndroidHostTest` Gradle task that executes `commonTest` sources on the JVM. All later tasks' tests run via this task.

This codebase has a `commonTest` source set wired to `kotlin("test")` (`app/build.gradle.kts:172-176`) but no task actually executes it — `iosX64Test`/`iosSimulatorArm64Test` are `SKIPPED` (Kotlin/Native test binaries cannot run on Linux, consistent with `dev/ARCHITECTURE.md`'s note that iOS compiles but cannot run on Linux), and the `com.android.kotlin.multiplatform.library` plugin does not wire up an Android unit-test task by default. This step adds the one Gradle DSL call needed to fix that — this was manually verified in this session: adding `withHostTestBuilder {}.configure {}` inside the `android { }` block causes a new `testAndroidHostTest` task to appear, and a throwaway `commonTest` test both passed and failed correctly through it (confirmed via `TEST-*.xml` output showing `tests="1" failures="0"`, then `failures="1"` after intentionally breaking the assertion).

- [ ] **Step 1: Add the host test builder to the android block**

In `app/build.gradle.kts`, inside the existing `android { }` block (currently lines 37-49), add one line after the `androidResources { enable = true }` block:

```kotlin
kotlin {
    android {
        namespace = "crucible.lens"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        androidResources {
            enable = true
        }

        withHostTestBuilder {}.configure {}
    }
```

- [ ] **Step 2: Write a throwaway smoke test to verify the task executes**

Create `app/src/commonTest/kotlin/crucible/lens/sandbox/SmokeTest.kt`:

```kotlin
package crucible.lens.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals

class SmokeTest {
    @Test
    fun sanityCheck() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 3: Run the test task and verify it passes**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, and `app/build/test-results/testAndroidHostTest/TEST-crucible.lens.sandbox.SmokeTest.xml` exists with `tests="1" failures="0"`.

- [ ] **Step 4: Delete the throwaway smoke test**

```bash
rm -rf app/src/commonTest/kotlin/crucible/lens/sandbox
```

Leave the `commonTest` directory structure in place (Task 2 adds real tests under `app/src/commonTest/kotlin/crucible/lens/data/`).

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "Enable JVM-executable unit tests via withHostTestBuilder"
```

---

### Task 2: `ObservableCache<K, V>` — generic reactive TTL store

**Files:**
- Create: `app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/cache/ObservableCacheTest.kt`

**Interfaces:**
- Produces:
  - `data class CachedEntry<V>(val value: V, val timestamp: Long)`
  - `class ObservableCache<K, V>(private val ttlMillis: Long, private val maxSize: Int, private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() })`
    - `fun observe(key: K): Flow<V?>` — reactive, updates whenever `put`/`invalidate`/`clear` is called for that key, treats expired entries as absent (does not proactively evict on a timer — expiry is checked lazily on read, matching `CacheManager`'s existing behavior)
    - `fun get(key: K): V?` — one-shot synchronous read, same expiry semantics as `observe`
    - `fun put(key: K, value: V)` — evicts the single oldest entry if already at `maxSize` before inserting, same eviction shape as `CacheManager.cacheResource`/`evictOldestIfOver` (both check `size >= limit` before insert, which correctly holds the cache at exactly `maxSize` steady-state — verified by trace, not a bug to fix)
    - `fun invalidate(key: K)` — removes one entry
    - `fun invalidateAll()` — clears everything
    - `fun ageMillis(key: K): Long?` — returns `null` if absent/expired, else `now() - timestamp`
- Consumes: nothing (leaf class, no dependencies on other new code).

- [ ] **Step 1: Write the failing tests**

Create `app/src/commonTest/kotlin/crucible/lens/data/cache/ObservableCacheTest.kt`:

```kotlin
package crucible.lens.data.cache

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObservableCacheTest {

    private fun cacheWithClock(ttlMillis: Long, maxSize: Int, clock: () -> Long) =
        ObservableCache<String, String>(ttlMillis = ttlMillis, maxSize = maxSize, now = clock)

    @Test
    fun getReturnsNullForMissingKey() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.get("missing"))
    }

    @Test
    fun putThenGetReturnsValue() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        assertEquals("value-a", cache.get("a"))
    }

    @Test
    fun getReturnsNullAfterTtlExpires() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.get("a"))
    }

    @Test
    fun getReturnsValueJustBeforeTtlExpires() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 999L
        assertEquals("value-a", cache.get("a"))
    }

    @Test
    fun putEvictsOldestWhenAtCapacity() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 100_000, maxSize = 2) { time }
        cache.put("a", "value-a"); time = 10L
        cache.put("b", "value-b"); time = 20L
        // Cache is now at capacity (2). Inserting a third must evict the oldest ("a").
        cache.put("c", "value-c")
        assertNull(cache.get("a"))
        assertEquals("value-b", cache.get("b"))
        assertEquals("value-c", cache.get("c"))
    }

    @Test
    fun invalidateRemovesEntry() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        cache.invalidate("a")
        assertNull(cache.get("a"))
    }

    @Test
    fun invalidateAllClearsEverything() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        cache.put("b", "value-b")
        cache.invalidateAll()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun ageMillisReturnsNullForMissingKey() {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.ageMillis("missing"))
    }

    @Test
    fun ageMillisReturnsElapsedTimeSincePut() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 100_000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 500L
        assertEquals(500L, cache.ageMillis("a"))
    }

    @Test
    fun ageMillisReturnsNullAfterExpiry() {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.ageMillis("a"))
    }

    @Test
    fun observeEmitsCurrentValueImmediately() = runTest {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        cache.put("a", "value-a")
        assertEquals("value-a", cache.observe("a").first())
    }

    @Test
    fun observeEmitsNullForMissingKey() = runTest {
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { 0L }
        assertNull(cache.observe("missing").first())
    }

    @Test
    fun observeEmitsNullForExpiredEntry() = runTest {
        var time = 0L
        val cache = cacheWithClock(ttlMillis = 1000, maxSize = 10) { time }
        cache.put("a", "value-a")
        time = 1001L
        assertNull(cache.observe("a").first())
    }
}
```

- [ ] **Step 2: Add the `kotlinx-coroutines-test` dependency needed by the test above**

`runTest` (used for the two `observe*` tests) requires `kotlinx-coroutines-test`, which is not currently a dependency anywhere in this project (verified via `grep -rn "kotlinx-coroutines-test" gradle/ app/build.gradle.kts` returning no results).

In `gradle/libs.versions.toml`, find the existing `kotlinx-coroutines-core` line (currently at line 49) and add a sibling line directly after it:

```toml
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

In `app/build.gradle.kts`, find the `commonTest` block (currently lines 172-176) and add the new dependency:

```kotlin
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
```

- [ ] **Step 3: Run the tests to verify they fail with "unresolved reference: ObservableCache"**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — `ObservableCache` does not exist yet.

- [ ] **Step 4: Implement `ObservableCache`**

Create `app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt`:

```kotlin
package crucible.lens.data.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

data class CachedEntry<V>(val value: V, val timestamp: Long)

/**
 * Generic in-memory TTL cache backed by a [MutableStateFlow], so callers can either
 * read synchronously ([get]) or observe changes reactively ([observe]). Expiry is
 * checked lazily on read/observe — there is no background eviction timer.
 */
class ObservableCache<K, V>(
    private val ttlMillis: Long,
    private val maxSize: Int,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private val state = MutableStateFlow<Map<K, CachedEntry<V>>>(emptyMap())

    private fun CachedEntry<V>.isExpired(): Boolean = now() - timestamp > ttlMillis

    fun get(key: K): V? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        return entry.value
    }

    fun observe(key: K): Flow<V?> = state.map { map ->
        val entry = map[key] ?: return@map null
        if (entry.isExpired()) null else entry.value
    }

    fun put(key: K, value: V) {
        state.value = state.value.let { current ->
            val withoutEvicted = if (current.size >= maxSize && key !in current) {
                val oldestKey = current.entries.minByOrNull { it.value.timestamp }?.key
                if (oldestKey != null) current - oldestKey else current
            } else current
            withoutEvicted + (key to CachedEntry(value, now()))
        }
    }

    fun invalidate(key: K) {
        state.value = state.value - key
    }

    fun invalidateAll() {
        state.value = emptyMap()
    }

    fun ageMillis(key: K): Long? {
        val entry = state.value[key] ?: return null
        if (entry.isExpired()) return null
        return now() - entry.timestamp
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all 13 tests in `ObservableCacheTest` pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/cache/ObservableCache.kt \
        app/src/commonTest/kotlin/crucible/lens/data/cache/ObservableCacheTest.kt \
        gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Add ObservableCache: generic reactive TTL store"
```

---

### Task 3: Expand `CrucibleRepository` with reactive resource caching

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` from Task 2 (`observe`, `get`, `put`, `invalidate`, `invalidateAll`, `ageMillis`).
- Produces (added to `CrucibleRepository`, alongside its existing methods which are all kept unchanged in this task):
  - `fun observeResource(uuid: String): Flow<CrucibleResource?>`
  - `fun getCachedResource(uuid: String): CrucibleResource?`
  - `fun invalidateResource(uuid: String)`
  - `fun resourceAgeMillis(uuid: String): Long?`
  - (internal) a `private val resourceObservableCache = ObservableCache<String, CrucibleResource>(ttlMillis = 10 * 60 * 1000L, maxSize = 50)` field
- This task does **not** yet change `fetchResourceByUuid` to write into `resourceObservableCache` instead of `CacheManager` — that migration (and the accompanying `CacheManager` deprecation) happens per-entity-type across Tasks 3-6, with `fetchResourceByUuid` updated in this task specifically since resources are the type this task covers.

Since `CrucibleRepository` currently reads/writes resources via the injected `CacheManager` (`cacheManager.getResource`/`cacheManager.cacheResource` at `CrucibleRepository.kt:54-63`), this task replaces those two call sites with the new `resourceObservableCache`, and adds the new `observeResource`/`getCachedResource`/`invalidateResource`/`resourceAgeMillis` methods. `CacheManager` is still injected into `CrucibleRepository`'s constructor after this task (other methods — `fetchProjects`, `fetchInstruments`, `fetchProjectData` — still use it; those migrate in Tasks 4-6).

A `FakeApiClient`-style test double is needed since `ApiClient`/`CrucibleApiService` make real Ktor HTTP calls with no existing mock infrastructure (verified via `grep -rn "ktor-client-mock\|MockEngine"` returning no results anywhere in this project). Rather than instantiate `CrucibleApiService` with a mocked Ktor engine (a heavier lift with no established local pattern), this task tests `resourceObservableCache`'s integration behavior directly through a minimal fake that mimics `fetchResourceByUuid`'s cache-then-network contract, deferring full `CrucibleRepository` integration tests (which need an HTTP-mockable `ApiClient`) to Phase 2, where `ResourceDetailViewModel`'s tests will need the same infrastructure and can introduce it once.

- [ ] **Step 1: Write the failing tests for the new observe/invalidate methods**

Create `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`:

```kotlin
package crucible.lens.data.repository

import crucible.lens.data.api.ApiClient
import crucible.lens.data.cache.CacheManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class CrucibleRepositoryTest {

    @Test
    fun getCachedResourceReturnsNullWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.getCachedResource("unknown-uuid"))
    }

    @Test
    fun invalidateResourceIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        // Populating the cache first would require a real network call through
        // fetchResourceByUuid — no ApiClient mock exists in this project yet (see
        // Task 3 preamble). This test only confirms invalidate() doesn't throw on an
        // absent key; invalidate's actual removal behavior is covered directly by
        // ObservableCacheTest.invalidateRemovesEntry, which this method delegates to.
        repository.invalidateResource("unknown-uuid")
        assertNull(repository.getCachedResource("unknown-uuid"))
    }

    @Test
    fun resourceAgeMillisReturnsNullWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.resourceAgeMillis("unknown-uuid"))
    }

    @Test
    fun observeResourceEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeResource("unknown-uuid").first())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — `getCachedResource`/`invalidateResource`/`resourceAgeMillis`/`observeResource` do not exist yet on `CrucibleRepository`.

- [ ] **Step 3: Implement the new methods and migrate `fetchResourceByUuid`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add the import and the new field, then replace `fetchResourceByUuid` and `hasLinks`'s call sites, and add the four new methods.

Add to the imports at the top of the file (after the existing `import crucible.lens.data.cache.CacheManager` on line 5):

```kotlin
import crucible.lens.data.cache.ObservableCache
import kotlinx.coroutines.flow.Flow
```

Add the new field inside the class body, directly after the existing `private val projectFetchMutexes = mutableMapOf<String, Mutex>()` (currently line 46):

```kotlin
    private val resourceObservableCache = ObservableCache<String, CrucibleResource>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 50
    )
```

Replace the existing `fetchResourceByUuid` method body (currently lines 52-73) — the method signature and `ResourceResult` return type are unchanged, only the two `cacheManager.getResource`/`cacheManager.cacheResource` calls change to `resourceObservableCache.get`/`resourceObservableCache.put`:

```kotlin
    suspend fun fetchResourceByUuid(uuid: String): ResourceResult = withContext(Dispatchers.Default) {
        try {
            val cached = resourceObservableCache.get(uuid)
            // Check if we have a fully-loaded cached version (with links)
            if (cached != null && hasLinks(cached)) {
                return@withContext ResourceResult.Success(cached)
            }

            when (val result = api.getResource(uuid)) {
                is ApiResult.Success -> {
                    val resource = result.data
                    resourceObservableCache.put(uuid, resource)
                    ResourceResult.Success(resource)
                }
                is ApiResult.Error -> httpError(result.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResourceResult.Error("Network error: ${e.message ?: "check your connection"}")
        }
    }
```

Add the four new public methods directly after `fetchResourceByUuid` (before the existing `hasLinks` private function):

```kotlin
    /** Reactive read — emits the current cached resource (or null) and re-emits on any change. */
    fun observeResource(uuid: String): Flow<CrucibleResource?> = resourceObservableCache.observe(uuid)

    /** One-shot synchronous read — null if absent or expired. */
    fun getCachedResource(uuid: String): CrucibleResource? = resourceObservableCache.get(uuid)

    /** Evicts a single resource, forcing the next fetch to hit the network. */
    fun invalidateResource(uuid: String) = resourceObservableCache.invalidate(uuid)

    /** Age of the cached entry in milliseconds, or null if absent/expired. */
    fun resourceAgeMillis(uuid: String): Long? = resourceObservableCache.ageMillis(uuid)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all 4 new tests in `CrucibleRepositoryTest` pass, and all 13 `ObservableCacheTest` tests still pass.

- [ ] **Step 5: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` with no new warnings — `fetchResourceByUuid`'s external signature (`suspend fun fetchResourceByUuid(uuid: String): ResourceResult`) is unchanged, so `ResourceDetailViewModel.kt` (its only caller) needs no changes.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: migrate resource cache to ObservableCache, add reactive reads"
```

---

### Task 4: Migrate project and instrument list caching to `ObservableCache`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` from Task 2.
- Produces (added to `CrucibleRepository`):
  - `fun observeProjects(): Flow<List<Project>?>`
  - `fun invalidateProjects()`
  - `fun observeInstruments(): Flow<List<Instrument>?>`
  - `fun invalidateInstruments()`
- `fetchProjects(forceRefresh: Boolean = false): ApiResult<List<Project>>` and `fetchInstruments(forceRefresh: Boolean = false): ApiResult<List<Instrument>>` keep their exact existing signatures (unchanged callers in `HomeScreen.kt`, `ProjectsListViewModel.kt`, `ManageProjectViewModel.kt`, `InstrumentListViewModel.kt`, `DataSyncManager.kt` — none of those are touched in this phase) but internally read/write via two new `ObservableCache` instances instead of the injected `CacheManager`.

Projects and instruments are each a single list (not keyed per-item), so each gets an `ObservableCache<Unit, List<T>>` — using `Unit` as the key is the standard way to model a cache that holds exactly one entry while reusing the same `ObservableCache` machinery (TTL, `observe`, `invalidate`) rather than writing a bespoke single-value cache type.

- [ ] **Step 1: Write the failing tests**

Add to `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` (inside the existing `CrucibleRepositoryTest` class, after the last test from Task 3):

```kotlin
    @Test
    fun observeProjectsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjects().first())
    }

    @Test
    fun observeInstrumentsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeInstruments().first())
    }

    @Test
    fun invalidateProjectsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateProjects()
    }

    @Test
    fun invalidateInstrumentsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateInstruments()
    }
```

No new imports are needed for this step — `observeProjects()`/`observeInstruments()` return types are inferred by the compiler and never named explicitly in the test body, so `Project`/`Instrument` do not need to be imported into the test file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — `observeProjects`/`invalidateProjects`/`observeInstruments`/`invalidateInstruments` do not exist yet.

- [ ] **Step 3: Implement the new fields and methods, migrate `fetchProjects`/`fetchInstruments`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add two new fields directly after `resourceObservableCache` (added in Task 3):

```kotlin
    private val projectsObservableCache = ObservableCache<Unit, List<Project>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 1
    )
    private val instrumentsObservableCache = ObservableCache<Unit, List<Instrument>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 1
    )
```

Replace the existing `fetchProjects` method (currently lines 95-102):

```kotlin
    /** Cache-first project list fetch. Caches on success. */
    suspend fun fetchProjects(forceRefresh: Boolean = false): ApiResult<List<Project>> {
        if (!forceRefresh) {
            projectsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return api.getProjects().also { result ->
            if (result is ApiResult.Success) projectsObservableCache.put(Unit, result.data)
        }
    }

    fun observeProjects(): Flow<List<Project>?> = projectsObservableCache.observe(Unit)

    fun invalidateProjects() = projectsObservableCache.invalidate(Unit)
```

Replace the existing `fetchInstruments` method (currently lines 104-112):

```kotlin
    /** Cache-first instrument list fetch. Caches on success. */
    suspend fun fetchInstruments(forceRefresh: Boolean = false): ApiResult<List<Instrument>> {
        if (!forceRefresh) {
            instrumentsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
        }
        return api.getInstruments().also { result ->
            if (result is ApiResult.Success) instrumentsObservableCache.put(Unit, result.data)
        }
    }

    fun observeInstruments(): Flow<List<Instrument>?> = instrumentsObservableCache.observe(Unit)

    fun invalidateInstruments() = instrumentsObservableCache.invalidate(Unit)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (17 from Tasks 2-3 plus 4 new ones).

- [ ] **Step 5: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` — `fetchProjects`/`fetchInstruments` signatures are unchanged, so `HomeScreen.kt`, `ProjectsListViewModel.kt`, `ManageProjectViewModel.kt`, `InstrumentListViewModel.kt`, and `DataSyncManager.kt` need no changes yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: migrate projects/instruments cache to ObservableCache"
```

---

### Task 5: Migrate per-project sample/dataset list caching to `ObservableCache`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` from Task 2.
- Produces (added to `CrucibleRepository`):
  - `fun observeProjectSamples(projectId: String): Flow<List<Sample>?>`
  - `fun observeProjectDatasets(projectId: String): Flow<List<Dataset>?>`
  - `fun invalidateProjectData(projectId: String)` — invalidates both the sample and dataset entry for that project in one call (mirrors `CacheManager.clearProjectDetail`'s existing two-cache-at-once semantics, which `CreateEditViewModels.kt` relies on today)
- `fetchProjectData(projectId: String, forceRefresh: Boolean = false, onCountsAvailable: (suspend (Int, Int) -> Unit)? = null): Pair<List<Sample>, List<Dataset>>` keeps its exact existing signature (unchanged callers in `HomeScreen.kt`, `ProjectsListScreen.kt`, `ProjectDetailViewModel.kt` — not touched in this phase) but internally reads/writes via two new `ObservableCache` instances instead of `cacheManager.getProjectSamples`/`getProjectDatasets`/`cacheProjectSamples`/`cacheProjectDatasets`. The existing per-project `Mutex` behavior (`projectFetchMutexes`) is unchanged — this task only swaps the underlying storage.

- [ ] **Step 1: Write the failing tests**

Add to `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` (after the last test from Task 4):

```kotlin
    @Test
    fun observeProjectSamplesEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjectSamples("project-1").first())
    }

    @Test
    fun observeProjectDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeProjectDatasets("project-1").first())
    }

    @Test
    fun invalidateProjectDataIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateProjectData("project-1")
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — the three new methods do not exist yet.

- [ ] **Step 3: Implement the new fields and methods, migrate `fetchProjectData`**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add two new fields directly after `instrumentsObservableCache` (added in Task 4):

```kotlin
    private val projectSamplesObservableCache = ObservableCache<String, List<Sample>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
    private val projectDatasetsObservableCache = ObservableCache<String, List<Dataset>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 30
    )
```

Replace the existing `fetchProjectData` method body (currently lines 123-188) — the signature and the `Mutex`/`coroutineScope`/`onCountsAvailable` coordination logic are unchanged, only the four `cacheManager.getProjectSamples`/`getProjectDatasets`/`cacheProjectSamples`/`cacheProjectDatasets` calls change:

```kotlin
    suspend fun fetchProjectData(
        projectId: String,
        forceRefresh: Boolean = false,
        onCountsAvailable: (suspend (Int, Int) -> Unit)? = null
    ): Pair<List<Sample>, List<Dataset>> {
        val mutex = projectFetchMutexes.getOrPut(projectId) { Mutex() }
        return mutex.withLock {
            val cachedSamples = if (!forceRefresh) projectSamplesObservableCache.get(projectId) else null
            val cachedDatasets = if (!forceRefresh) projectDatasetsObservableCache.get(projectId) else null
            if (cachedSamples != null && cachedDatasets != null) {
                onCountsAvailable?.invoke(cachedSamples.size, cachedDatasets.size)
                return@withLock cachedSamples to cachedDatasets
            }
            coroutineScope {
                // Coordinate fire-once callback: fires when both totals are known
                var sampleTotal: Int? = null
                var datasetTotal: Int? = null
                val coordMutex = if (onCountsAvailable != null) Mutex() else null
                var countFired = false

                val sOnTotal: (suspend (Int) -> Unit)? = if (onCountsAvailable != null) { total ->
                    coordMutex!!.withLock {
                        sampleTotal = total
                        val d = datasetTotal
                        if (d != null && !countFired) { countFired = true; onCountsAvailable(total, d) }
                    }
                } else null

                val dOnTotal: (suspend (Int) -> Unit)? = if (onCountsAvailable != null) { total ->
                    coordMutex!!.withLock {
                        datasetTotal = total
                        val s = sampleTotal
                        if (s != null && !countFired) { countFired = true; onCountsAvailable(s, total) }
                    }
                } else null

                val s = async {
                    if (cachedSamples != null) {
                        sOnTotal?.invoke(cachedSamples.size)
                        cachedSamples
                    } else {
                        val result = api.getSamplesByProject(projectId, onTotalKnown = sOnTotal)
                        (result as? ApiResult.Success)?.data
                            ?.also {
                                projectSamplesObservableCache.put(projectId, it)
                                it.forEach { s -> cacheManager.cacheResourceType(s.uniqueId, "sample") }
                            } ?: emptyList()
                    }
                }
                val d = async {
                    if (cachedDatasets != null) {
                        dOnTotal?.invoke(cachedDatasets.size)
                        cachedDatasets
                    } else {
                        val result = api.getDatasetsByProject(projectId, onTotalKnown = dOnTotal)
                        (result as? ApiResult.Success)?.data
                            ?.also {
                                projectDatasetsObservableCache.put(projectId, it)
                                it.forEach { ds -> cacheManager.cacheResourceType(ds.uniqueId, "dataset") }
                            } ?: emptyList()
                    }
                }
                s.await() to d.await()
            }
        }
    }

    fun observeProjectSamples(projectId: String): Flow<List<Sample>?> =
        projectSamplesObservableCache.observe(projectId)

    fun observeProjectDatasets(projectId: String): Flow<List<Dataset>?> =
        projectDatasetsObservableCache.observe(projectId)

    fun invalidateProjectData(projectId: String) {
        projectSamplesObservableCache.invalidate(projectId)
        projectDatasetsObservableCache.invalidate(projectId)
    }
```

Note: `cacheManager.cacheResourceType(...)` calls are intentionally left unchanged in this task — resource-type caching (`CacheManager.resourceTypeCache`) is a separate, still-`CacheManager`-owned concern not covered by this phase's scope (it is not read anywhere in the current codebase per `grep -rn "getResourceType" app/src/commonMain` — only written — so it is left alone rather than migrated speculatively).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (21 from Tasks 2-4 plus 3 new ones).

- [ ] **Step 5: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` — `fetchProjectData`'s signature is unchanged, so `HomeScreen.kt`, `ProjectsListScreen.kt`, `ProjectDetailViewModel.kt`, and `DataSyncManager.kt` need no changes yet.

- [ ] **Step 6: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: migrate project sample/dataset list cache to ObservableCache"
```

---

### Task 6: Migrate instrument-dataset list caching to `ObservableCache`

**Files:**
- Modify: `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`
- Test: `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ObservableCache<K, V>` from Task 2.
- Produces (added to `CrucibleRepository`):
  - `suspend fun fetchInstrumentDatasets(instrumentName: String, forceRefresh: Boolean = false): ApiResult<List<Dataset>>` — **new** method; today this cache-then-fetch logic lives inline in `InstrumentDetailViewModel.load()` (`InstrumentDetailViewModel.kt:54-67`), calling `apiClient.service.getDatasetsByInstrument` and `cacheManager.getInstrumentDatasets`/`cacheInstrumentDatasets` directly. This task adds the repository method with the same cache-then-fetch shape as `fetchProjects`/`fetchInstruments` above; `InstrumentDetailViewModel` itself is **not** modified in this phase (that happens in Phase 3) — this task only adds the method and proves it via unit test.
  - `fun observeInstrumentDatasets(instrumentName: String): Flow<List<Dataset>?>`
  - `fun invalidateInstrumentDatasets(instrumentName: String)`

- [ ] **Step 1: Write the failing tests**

Add to `app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt` (after the last test from Task 5):

```kotlin
    @Test
    fun observeInstrumentDatasetsEmitsNullWhenNotCached() = runTest {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        assertNull(repository.observeInstrumentDatasets("Microscope A").first())
    }

    @Test
    fun invalidateInstrumentDatasetsIsSafeNoOpWhenNotCached() {
        val repository = CrucibleRepository(ApiClient(), CacheManager())
        repository.invalidateInstrumentDatasets("Microscope A")
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: compilation failure — `observeInstrumentDatasets`/`invalidateInstrumentDatasets` do not exist yet.

- [ ] **Step 3: Implement the new field and methods**

In `app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt`, add one new field directly after `projectDatasetsObservableCache` (added in Task 5):

```kotlin
    private val instrumentDatasetsObservableCache = ObservableCache<String, List<Dataset>>(
        ttlMillis = 10 * 60 * 1000L,
        maxSize = 15
    )
```

Add the new method after `invalidateProjectData` (added in Task 5):

```kotlin
    /** Cache-first dataset-by-instrument fetch. Caches on success. */
    suspend fun fetchInstrumentDatasets(
        instrumentName: String,
        forceRefresh: Boolean = false
    ): ApiResult<List<Dataset>> {
        if (!forceRefresh) {
            instrumentDatasetsObservableCache.get(instrumentName)?.let { return ApiResult.Success(it) }
        }
        return api.getDatasetsByInstrument(instrumentName).also { result ->
            if (result is ApiResult.Success) instrumentDatasetsObservableCache.put(instrumentName, result.data)
        }
    }

    fun observeInstrumentDatasets(instrumentName: String): Flow<List<Dataset>?> =
        instrumentDatasetsObservableCache.observe(instrumentName)

    fun invalidateInstrumentDatasets(instrumentName: String) =
        instrumentDatasetsObservableCache.invalidate(instrumentName)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (24 from Tasks 2-5 plus 2 new ones).

- [ ] **Step 5: Verify the wider codebase still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain`
Expected: `BUILD SUCCESSFUL` — this task only adds new methods, no existing signatures changed, so no other file needs modification.

- [ ] **Step 6: Verify iOS still compiles**

Run: `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64`
Expected: `BUILD SUCCESSFUL` (the 4 pre-existing warnings in `PersistentProjectCache.kt`/`CryptoUtils.kt` are unrelated and expected — see `dev/PLATFORM_PARITY.md`).

- [ ] **Step 7: Commit**

```bash
git add app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt \
        app/src/commonTest/kotlin/crucible/lens/data/repository/CrucibleRepositoryTest.kt
git commit -m "CrucibleRepository: add fetchInstrumentDatasets with ObservableCache backing"
```

---

## What this plan intentionally does NOT do (deferred to Phase 2 / Phase 3)

- **Does not touch `CacheManager.kt`.** It remains fully intact and is still injected into `CrucibleRepository`'s constructor (used by `cacheManager.cacheResourceType`, and still directly injected into `HomeScreen.kt`, `ProjectsListScreen.kt`, `InstrumentDetailScreen.kt`, `ProjectDetailScreen.kt`, `ResourceDetailScreen.kt`, `AssociatedFilesCard.kt`, `LinkResourceSheet.kt`, `EditResourceSheet.kt`, and several ViewModels). Its resource/project/instrument/project-detail caching responsibilities are now *duplicated* (unused) inside `CrucibleRepository`'s new `ObservableCache` fields until Phase 3 removes the old code paths — this is intentional, since Phase 1 must not break any currently-working screen. `CacheManager`'s thumbnail cache, dataset-files cache, and file-URL cache are entirely untouched (out of scope for this phase).
- **Does not change `ResourceDetailScreen.kt`, `ResourceDetailViewModel.kt`, or `NavGraph.kt`.** The uuid-based reactive screen redesign (the part that structurally eliminates the flash-bug class) is Phase 2.
- **Does not change `HomeScreen.kt`, `ProjectsListScreen.kt`, `ProjectsListViewModel.kt`, `ProjectDetailViewModel.kt`, `InstrumentListScreen.kt`, `InstrumentListViewModel.kt`, `InstrumentDetailScreen.kt`, `InstrumentDetailViewModel.kt`, `ManageProjectViewModel.kt`, `ManageInstrumentViewModel.kt`, `CreateEditViewModels.kt`, `FilterSheet.kt`, `InstrumentPickerField.kt`, `AssociatedFilesCard.kt`, or `LinkResourceSheet.kt`.** All direct `CacheManager`/`ApiClient` access from these files is migrated to the new repository methods in Phase 3.
- **Does not delete `CacheManager`'s resource/project/instrument/project-detail methods.** They become dead weight from `CrucibleRepository`'s perspective after this phase, but are still load-bearing for every other file listed above. Removing them is the last step of Phase 3, once every consumer has migrated.

## Verification (full phase)

1. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:testAndroidHostTest` — all unit tests pass (26 total: 13 `ObservableCacheTest` + 13 `CrucibleRepositoryTest`).
2. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileAndroidMain` — `BUILD SUCCESSFUL`, zero new warnings.
3. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :composeApp:compileKotlinIosArm64` — `BUILD SUCCESSFUL`, only the 4 pre-existing unrelated warnings.
4. `JAVA_HOME=/home/roncofaber/software/android-studio/jbr ./gradlew :androidApp:assembleDebug` — `BUILD SUCCESSFUL`; install and smoke-test the resulting APK exactly as before this phase (sign-in, browse projects, open a resource, sibling-scroll) — since no screen code changed, behavior must be pixel-for-pixel identical to pre-Phase-1.
5. Grep for regressions: `grep -rn "resourceObservableCache\|projectsObservableCache\|instrumentsObservableCache\|projectSamplesObservableCache\|projectDatasetsObservableCache\|instrumentDatasetsObservableCache" app/src/commonMain/kotlin/crucible/lens/data/repository/CrucibleRepository.kt` should show all six fields declared and used only within that one file — confirming no premature leakage into screens.
