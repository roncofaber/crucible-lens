# Changelog

## [Unreleased]

### Added

- The QR scanner now accepts trusted Crucible Web links and verifies their extracted resource or project identifier through the authenticated API before opening it
- Project pages now show resources assigned to the project together with resources shared with it
- Search filters now suggest existing measurement, data format, session, and sample type values while still accepting new values
- Authorized dataset managers can now assign a different registered instrument while editing a dataset
- Platform administrators can create service accounts, change their platform roles, and rotate one-time API keys from Settings
- Search filters now include visibility, resources owned by the signed-in account, and missing scientific fields
- Authorized dataset editors can rename thumbnails or replace their images

### Changed

- Dataset views now show current project and instrument names and use stable references when opening their details
- Resource linking now searches the assigned project by its stable identity, keeping results correct after project ID changes
- Sample and dataset creation now submits stable project and instrument identities when available
- Create actions now follow the permissions reported for the signed-in account
- Resource detail opens a bounded server-anchored sibling window when the project collection is not already cached
- Instrument operator selection now searches service accounts as you type

### Fixed

- Owner and date filters now use the current API contract, including stable owner identities and UTC timestamps
- Connection diagnostics now show the deployed API version, database status, latency, and schema revisions even when readiness is degraded
- Service accounts can no longer be assigned project roles above Contributor
- Project pages keep successfully loaded shared samples or datasets visible when the other shared collection fails
- Service-account administration now preserves its list during refreshes and prevents stale account details from replacing the current selection
- Thumbnail controls now prevent overlapping edits and deletions while a change is in progress

## [0.9.0-alpha.1] - 2026-09-01

Crucible Lens 0.9.0-alpha.1 moves the app to Crucible API V3 with durable project synchronization, expanded project and instrument management, and sample and dataset access controls. It also strengthens uploads, deep links, caching, permissions, and cross-platform reliability ahead of the final 0.9.0 release.

### Added
- Authorized users can register instruments, manage service-account operators and lifecycle status, rename instrument IDs, and transfer ownership
- Synced projects remain available after restarting while offline
- Project management now shows authority-ordered role badges and lets eligible editors, admins, and leads assign roles or rename the project ID within their API-defined permissions
- Authorized sample and dataset managers can review direct access, grant or revoke user and project access, change grant levels, and control public visibility

### Changed
- Instrument browsing now covers every lifecycle status, loads datasets in explicit pages, and retains associations when instrument names change
- Project, instrument, sample, and dataset management actions now follow the permissions returned by the V3 API
- Dataset creation and details now use V3 instrument identifiers and omit the removed source-folder and aggregate-size fields
- Project leadership transfers and sample or dataset moves now show a preview and require explicit confirmation
- Project and instrument navigation, pins, sync selections, and offline data now remain associated when a project slug or instrument ID changes

### Fixed
- Project member controls no longer shift or collapse to an unreadable width, and Back closes the project editor before leaving Manage Project
- Crucible Web links, Android App Links, and iOS Universal Links now resolve the intended browser or in-app destination
- Resource owners now display consistently, and edit requests no longer include ownership, leadership, project, or instrument fields rejected by V3
- Project synchronization, refreshes, and offline detection no longer mix stale and current data, restore data from another account or server, hide linked resources, or miss offline status on iOS
- Camera, image, upload, download, sharing, unlink, and thumbnail failures now remain visible, report partial results accurately, and provide appropriate recovery or retry actions
- Management, linking, search, picker, deletion, and instrument workflows now distinguish empty results from failures, preserve retryable state, and avoid a rare collapsing-header crash

## [0.8.2] – 2026-08-11

Search for people and add them to your projects, customizable per-category search result limits, and a profile-completion step that asks for a username and email if either is missing.

### Added
- Search for people; add them to one of your projects from their profile
- Search results can be filtered to just People, Projects, Samples, or Datasets
- New Settings > Search screen to customize how many People and Projects show per search, independently
- Sign-in now asks you to add a username (and email, if missing) before continuing

### Changed
- Search fields use a neutral color instead of the accent color, matching most other apps
- "Create Project" and "Add to Project" buttons are no longer full-width
- Search now always includes projects you're not a member of, instead of needing a "Discover" toggle
- Search filter chips are now a single row
- Project search results are now capped like people results, instead of showing up to 20
- Home screen's QR scan icon uses the accent color

### Fixed
- Cached project data is no longer included in device backups or transfers
- Adding to a project now shows whether someone is already a member before you try
- Adding a member now shows a smooth confirmation instead of an abrupt swap, with a checkmark instead of wrapped text
- User search results now include accounts without a username instead of hiding them
- Not-syncing projects group remembers whether it's expanded after opening a project and going back
- Group headers no longer shift by a couple of pixels when expanding or collapsing
- Group headers no longer blend into the background in some themes when expanded
- A failed search now shows a retryable error instead of silently looking like no results

## [0.8.1] – 2026-08-07

Long-press a project for quick actions, loading placeholders instead of spinners, and a batch of consistency fixes to dialogs, colors, and typography.

### Added
- Long-press a project in the list to manage it, toggle sync, or copy its ID

### Changed
- New installs now default to the Cerulean accent color instead of Carmine
- Confirmation dialogs are consistently phrased as questions, with the icon beside the title
- Project, instrument, and search lists show placeholder rows while loading instead of a spinner
- Search bar and the direct-ID-lookup card now match the rest of the app's colors more closely
- Not-syncing projects now show a dimmed icon instead of a cramped icon next to the pin button
- Card and dialog headings are lighter weight, consistent with the rest of the app's text

### Fixed
- Typing in the Projects search field no longer lags with many synced projects
- Clearing the cache now asks for confirmation first

## [0.8.0] – 2026-08-06

Create projects directly in the app, a redesigned collapsing header for project and instrument screens, search fields that confirm a match instead of staying plain text, and new accent color/contrast choices, plus a large batch of UI polish and bug fixes.

### Added
- Manage Project: leave a project from the overflow menu
- Create a project from the Projects screen (bottom of the list, or the overflow menu)
- New Sample/Dataset/Project screens, and editing a sample or dataset, have a Home button
- Create and edit screens now warn before discarding unsaved changes

### Changed
- Project and instrument screens' expanded header is left-aligned with an icon badge, not centered
- Project and instrument headers are tinted while expanded, fading to the page background as you collapse
- Project and instrument headers now collapse smoothly as you scroll, instead of jumping at the halfway point
- Project screen's lead and organization now share one line, with member count on its own line below
- Instrument screen's header shows the type and location while expanded
- Pin button on project/instrument screens stays visible while scrolled
- Project and instrument titles open the manage screen when tapped
- Removed redundant search icons from screens that already have their own search field
- Group headers are larger, with an icon, tinted while expanded and matching the page when collapsed
- Group header counts now line up along the right edge and no longer shift width
- List rows no longer repeat the section header's icon
- Search results can now be collapsed by section
- Search results show which project they belong to
- Search result rows gained the tap-and-hold menu and chevron used elsewhere
- Manage Project: Add Member sheet supports adding multiple people without closing
- Manage Project: any member, not just the lead, can add a new member
- Manage Project: Add member button sits above the member list, styled like a row
- Manage Project: members are sorted alphabetically by last name
- Manage Project: Add Member search field says "Search user" and matches by name too
- Refined card, status, and tinted-surface colors to follow your accent color
- Avatar initials and accent swatches stay legible on light colors
- Normalized shadow and elevation depth on cards and bars to Material 3 levels
- Refined text sizing, weight, and hierarchy across lists, headers, and settings rows
- Names show in full instead of `@username` throughout the app
- Search results lead with name and avatar, username smaller below
- Instrument dataset rows now show the project instead of the dataset ID
- Instrument dataset rows can now open in web or be shared
- History rows show the time under the icon instead of a separate line
- Username and instrument fields now confirm the match with a chip instead of staying plain text
- New accent color choices, each with its own light/dark theme, plus a contrast level picker in Appearance settings
- Pinned cards on Home now stand out more than the Last Visited card
- Choose which projects sync in the background; others still open normally
- Linked/parent/child sample and dataset rows show their type icon again
- Resource row IDs are fainter so the name stands out more
- Sample/dataset groups now scroll through everything directly instead of a "Load more" button
- Cache settings shows instrument and file counts too
- Avatars now get a unique color per person
- Debug builds show "dev" instead of a version number wherever the app version appears
- Editing a sample or dataset now opens a full screen instead of a bottom sheet
- Search bar and tabs no longer slide with the page when switching between samples and datasets
- Icon-only buttons are easier to tap

### Fixed
- History rows now consistently show which project a resource belongs to
- Fixed a doubled divider line next to group headers in lists
- Fixed the add-member icon blending into its background in Manage Project
- Swiping to sync/unsync a project felt laggy and added a haptic tick at the commit point
- Dataset file download/share icons no longer shift position when tapped or when a file is pending
- Project screen's header shows the project ID, tap to copy
- API settings screen restyled to match the rest of Settings
- Connection test now sits below the fields it tests
- Account screen: added a button to copy your API key
- Home screen footer no longer wraps or gets clipped on narrow screens
- IDs (mfid, project/instrument ID) now look consistent everywhere they appear
- Link Resource sheet no longer shows the project name in monospace next to the ID
- Project list rows show the project ID beneath the name
- Linked/parent/child resource rows use a proper tinted background instead of a flat grey overlay
- Removed an unexplained shadow on dataset thumbnail cards
- Status/navigation bar icons were invisible (white on white) in light theme
- Projects with no samples or datasets are no longer hidden automatically
- Project header member count, title, and organization now update on refresh and after edits
- Tapping a collapsed project header could open the project lead's profile
- Refresh, image load, and metadata search failures now show an error instead of failing silently
- Project screen no longer leaks memory each time it is opened
- Instrument grouping choice is now remembered, like the project screen's
- Instrument search text survives opening a dataset and going back
- "Not a member" notice no longer sits high on the screen
- Instrument list/dataset refresh could show stale data
- File share/download links are no longer cached
- Debug builds could fail to install over a release build
- Expanded groups now stay expanded after opening a sample/dataset and going back
- Add Member sheet could overflow and cut off results
- Searching for a project lead no longer shifts the form as you type
- Already-added members now show as "Added" instead of a re-addable button
- Editing scientific metadata could silently fail without any error shown
- Metadata edits now merge instead of overwriting other fields changed elsewhere
- Editing metadata no longer discards the edit and other unsaved changes after tapping Done
- Project header no longer overlaps the list after opening a sample/dataset and going back
- Scrollbar no longer runs behind the project header while scrolling
- Project/instrument screens could become completely unscrollable

## [0.7.0] – 2026-07-28

Project join requests, with a lead-facing pending-request badge, plus several loading/caching fixes.

### Added
- **Project join requests**: request to join a project, leads/admins review pending requests, your request history is visible from the Account screen
- **Discover projects**: search can now find projects you're not a member of
- Non-member project view now shows a "Request to join" action instead of an empty list
- Home screen: dedicated Account icon in the top bar
- Manage Project and Manage Instrument screens: added a Home button
- Manage Project: lead/member rows and join-request requesters are tappable, opening their profile; Members card is collapsible
- Account screen: reviewed join requests show who reviewed them
- Project leads see a small badge with the pending count (Home, Projects list, Project screen) when a project has a pending join request

### Changed
- Instrument icon switched to a filled glyph, matching the Project icon
- Signed-out home actions now go straight to the Account screen
- API key/sign-in now live entirely on the Account screen, not API settings

### Fixed
- Pending join-request badge now also refreshes on pull-to-refresh/Refresh (previously only updated at app start)
- Approve/reject buttons for join requests were missing accessibility labels
- Project screen loaded slower and re-fetched data unnecessarily due to disconnected caches
- Sort/Group icon in resource detail could flicker while swiping between siblings

## [0.6.0] – 2026-07-23

### Changed
- Project/instrument/sample/dataset rows switched to Material 3 list items — consistent row height, typography, and dividers across Projects, Instruments, and detail lists
- Swipe-to-hide redesigned: standard 56dp swipe threshold, hide is applied immediately with an Undo snackbar (previously deferred until the snackbar timed out, which lost the hide if you navigated away first)
- "Hidden" sections and grouped list headers now share one component (icon, count badge, animated chevron, divider)
- Home screen pinned cards match the styling of regular project/instrument rows
- Creation, modification, and timestamp dates now convert from UTC to your device's local time (previously showed the raw UTC clock time)
- "Web Explorer" renamed to "Crucible Web" throughout
- `ResourceDetailScreen` reads directly from the shared repository cache instead of its own local cache
- Repository cache (projects, instruments, resources, thumbnails) migrated to a shared TTL-based cache with reactive reads

### Fixed
- Account screen not refreshing after signing in with ORCID until you left and came back
- Project lead permission check used username instead of ORCID, so some leads couldn't remove members from their own project
- A project lead could remove themselves from their own project
- Copy ID showed two toasts on Android 13+ (the OS already shows one)
- Hidden projects/instruments not staying hidden after navigating away and back
- Release bundle wasn't actually signed due to a build script bug — fixed and added a CI check that fails the build if signing didn't happen

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
