---
name: add-api-endpoint
description: Add or change a Crucible API call — CrucibleApiService method, ApiResult wrapping, pagination helper choice, and CrucibleRepository cache wiring. Use this whenever touching data/api/ or data/repository/, adding an endpoint, changing a request/response model, or working on create/edit flows that write scientific metadata. The scientific-metadata rules here are the ones that have caused silent data loss before.
---

# Adding an API endpoint

## 1. Service method — `data/api/CrucibleApiService.kt`

Every call is wrapped so failures become values rather than exceptions:

```kotlin
suspend fun getWidget(widgetId: String): ApiResult<Widget> = safeCall {
    client.get("$baseUrl/widgets/$widgetId").body()
}
```

`ApiResult` is `Success(data)` / `Error(code, message)` — always branch with
`is ApiResult.Success` / `is ApiResult.Error`. The shared `HttpClient` sets `expectSuccess = true`
specifically so a non-2xx **throws** and is caught by `safeCall`, instead of Ktor's default of
handing back a normal response object that reads as success.

The auth header is `Authorization: Bearer {apiKey}` on every request. Not `Api-Key`, not `Token`.

### Pagination

Pick by what the endpoint actually returns:

| Endpoint shape | Helper |
|---|---|
| Offset/limit list (instruments, projects, users, join requests) | `fetchAllPages { limit, offset -> … }` |
| Keyset cursor list (datasets, samples) | `fetchAllPagesCursor { limit, cursor -> … }` |
| Search endpoint | Neither — these return a flat list already |

Both helpers are `private suspend inline fun <reified T>` in the same file and loop until exhausted,
so callers get one complete `List<T>`. `fetchAllPagesCursor` also takes an optional `onTotalKnown`
callback for progress reporting.

## 2. Scientific metadata — the rule that has bitten us

**`scientific_metadata` is not accepted in `SampleUpdateRequest`, `DatasetUpdateRequest`,
`SampleCreateRequest`, or `DatasetCreateRequest`.** Sending it there is silently dropped. It only
moves through the dedicated routes:

- `POST /resources/{id}/metadata`
- `PATCH /resources/{id}/metadata`

So create and edit flows always make **two** calls: one structural `POST`/`PATCH`, one metadata call.

Both metadata calls return `ApiResult<JsonObject>` — the resulting `scientific_metadata`, not
`ApiResult<Unit>`. A prior bug returned `Unit` and discarded the response, which made a 409 or 500
from the metadata call completely invisible. Keep the payload in the return type.

**On edit**, call `diffMetadataWrite` (`ui/common/MetadataEditor.kt`) to diff edited against
original:

- No keys deleted → `PATCH` with only changed/added top-level keys. Survives concurrent edits to
  other keys, because PATCH shallow-merges server-side.
- Any key deleted → `POST ?overwrite=true` with the whole object, because PATCH has no way to
  express a delete.

**On create** there's no original to diff against, so it's always a plain `POST` with no `overwrite`.

## 3. Repository wiring — `data/repository/CrucibleRepository.kt`

`CrucibleRepository` is the single source of truth for all in-memory caching. There is no separate
cache layer. If the result should be cached, declare an `ObservableCache` next to the others and
follow the cache-first shape:

```kotlin
private val widgetsObservableCache = ObservableCache<Unit, List<Widget>>(
    ttlMillis = 10 * 60 * 1000L,
    maxSize = 1
)

suspend fun fetchWidgets(forceRefresh: Boolean = false): ApiResult<List<Widget>> {
    if (!forceRefresh) {
        widgetsObservableCache.get(Unit)?.let { return ApiResult.Success(it) }
    }
    return api.getWidgets().also { result ->
        if (result is ApiResult.Success) widgetsObservableCache.put(Unit, result.data)
    }
}
```

Key by `Unit` with `maxSize = 1` for a whole-collection cache; key by id with a larger `maxSize` for
per-entity caches. TTL and LRU eviction are handled inside `ObservableCache` — don't add manual
eviction on top.

Screens that need to react to cache updates observe directly (`observeResource(uuid)`,
`observeThumbnails(uuid)`) rather than holding the fetched object in Compose state.

**Skip the repository entirely** for one-shot mutations with nothing to cache. Join requests
(`requestToJoinProject`, `getJoinRequests`, `reviewJoinRequest`, `getMyJoinRequests`) are called
straight from the owning ViewModel via `apiClient.service.*`, deliberately.

## 4. Serialization

All `@SerialName` annotations must be explicit on multi-word field names **and** on single-word
fields that need to stay stable (e.g. `@SerialName("email")`). Relying on the Kotlin property name
is how a field silently stops deserializing after a rename.

## 5. Update the docs in the same change

`dev/architecture.md` carries the full endpoint list and the "Caching layers" breakdown. Add the new
endpoint there now — the audit history on this repo shows enumerative lists like that one drift
almost immediately when updates are deferred.

Add a `CHANGELOG.md` entry under `## [Unreleased]` only if the change is user-visible; a pure
plumbing addition isn't.

## Access notes

`GET /projects/search` and `GET /projects/{proj_id}` are readable by any authenticated user, not just
members — `lead` and `scientific_metadata` are populated only for members/admins. That asymmetry is
what makes discover-search and non-member project browsing work, so don't "fix" a null `lead` by
gating the endpoint.

## Verify

```bash
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain
```
