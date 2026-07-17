# Changelog

## [0.4.2] – 2026-07-16

### Added
- **Account screen**: view and edit profile (name, email, username), sign in via ORCID or API key; API key entry moved from ApiSettings to Account
- **Manage Project screen**: edit project info (lead only), add/remove members with username search
- **Manage Instrument screen**: view all instrument fields (manufacturer, model, owner were previously invisible), edit any field
- **Server-side fuzzy search**: samples, datasets, projects, and instruments now search server-side (`/search` endpoints) instead of loading all data client-side
- **Owner resolution**: `include_owner=true` on sample/dataset fetches; owner shown as "F. LastName (@username)" in detail cards
- **Username-aware filtering**: FilterSheet owner field replaced with username autocomplete; project lead local search now matches username
- **Shared user components**: `UserAvatar`, `UserSearchField`, `UserResultItem` in `ui/common/` for consistent user identity display

### Changed
- ApiSettings simplified to connectivity-only (base URL, Crucible Web URL, health check)
- `userOrcid` derived from `userProfile` rather than a separate DataStore key
- Crucible Web URL default updated to `https://crucible.lbl.gov/explore/`
- Connection test UI in ApiSettings replaced with a proper card and "Test connection" button
- Owner display format: "F. LastName (@username)" (was raw ORCID link)
- Icon consistency: `Biotech` for instruments everywhere, `ExpandMore`/`ExpandLess` for expand/collapse, `Badge` for username, `Tag` for session, `Science` for linked sample rows
- Timestamp icons differentiated: `Schedule` for timestamp, `CalendarToday` for created, `Update` for modified

### Fixed
- Upload flow: SHA256 sent at initiation (enables server-side deduplication), `existing_file` handled to skip redundant uploads, ingestion uses canonical `POST /files/{mfid}/ingest`
- GCS chunk upload: retry loop with session probe on failure (up to 3 attempts)
- `completeUpload` returns `ApiResult<AssociatedFile>` instead of raw `JsonObject`

---

## [0.4.1] – 2026-06-10

### Added
- Deletion request banner in resource detail (red card when deletion is pending or approved)

### Changed
- Keyset cursor pagination for `GET /datasets` and `GET /samples` (was offset-based)
- Auth route updated to canonical `/auth/apikey` (was `/user_apikey`)
- Default ingestor set to `ApiUploadIngestor`
- Gradle wrapper bumped to 9.5.1

### Fixed
- `OrcidLoginScreen` crash (`ClassCastException`) when progress bar is rendering during page load finish
- `PaginatedResponse` model: `total` and `offset` now nullable, `next_cursor` added

---

## [0.4.0] – 2026-05-07

### Added
- Associated Files card on dataset detail: shows all files (ingested and pending), per-file download and share
- GCS resumable upload protocol with CRC32C chunk hashing
- File upload from dataset detail (add files to existing datasets)
- Scientific metadata card with recursive tree display and expand-all

### Changed
- Feature-first project layout (colocated screens/viewmodels/components)
- Sibling navigation: virtual infinite pager with wrap-around, ±10 preload, circular distance eviction
- Resource detail enrichment: background parallel fetch with cache-first display
- Graph explorer routes updated (`sample-graph→samples`, `dataset→datasets`)

### Fixed
- Thumbnail loading spinner stuck on failed fetch
- QR dialog showed wrong resource (initialIndex fix)
- Race condition in ResourceDetailViewModel (cancellable fetch jobs)
- Stale API service after key change (computed property pattern)

---

## [0.3.0] – 2026-04-28

### Added
- iOS support via KMP (shared commonMain, platform actuals for camera, Base64, preferences)
- ORCID login via WebView with JS key extraction
- Instrument list and detail screens with dataset grouping
- Pinned/hidden projects and instruments
- Appearance settings (theme, accent color, dynamic color)
- AI metadata extraction (direct Anthropic API or proxy)
- History screen with long-press actions

### Fixed
- M3 dependency conflict (`moko-media` pulling older Material3 version)
- `CreateDatasetScreen` moved from `androidMain` to `commonMain`

---

## [0.2.1] – 2026-03-27

### Changed
- API schema updates (timing fields, response model fixes)
- Sign-out button added to API settings

### Fixed
- Various lint warnings and deprecated API usages
- Thumbnail upload failure surfaced in dataset creation

---

## [0.2.0] – 2026-03-02

### Added
- Resource navigation with sibling browsing
- Download links card for datasets
- Cache layer with 10-minute TTL
- Pull-to-refresh on resource detail

---

## [0.1.0] – 2026-02-27

Initial release: QR scanner app migrated to KMP + Compose Multiplatform targeting Android.
