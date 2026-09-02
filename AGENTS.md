# Crucible Lens Agent Guide

Crucible Lens is a Kotlin Multiplatform and Compose Multiplatform app for Android and iOS that browses and manages sample, dataset, project, instrument, and user data from the Molecular Foundry Crucible service.

- Package: `crucible.lens`
- Main branch: `main`
- Architecture: MVVM with repositories and Koin dependency injection
- UI: shared Compose Multiplatform with Material 3
- Networking: Ktor with kotlinx.serialization
- Android: minimum SDK 26, compile SDK 36

## Instructions

- Follow the repository's existing architecture and UI patterns. Make the smallest change that fully addresses the task.
- Use `rg` or `rg --files` for repository searches.
- Do not edit unrelated user changes in a dirty worktree.
- Add or update tests for deterministic logic in `data/`. The project has no Compose UI, instrumented, screenshot, or general ViewModel test harness.
- Update the owning documentation in the same change when behavior, architecture, API coverage, UI conventions, icons, or platform parity changes.
- Add a concise entry under `## [Unreleased]` in `CHANGELOG.md` for user-visible changes. Do not add entries for internal refactors or documentation-only changes.
- Do not edit `PRIVACY.md` without separate user approval. Flag a possible update when a change affects stored data, retention, network hosts, permissions, telemetry dependencies, other users' personal data, or Android backup rules.
- Do not bump `app.versionName` or `app.versionCode` outside the release workflow.

## Documentation ownership

| File | Owns |
|---|---|
| `AGENTS.md` | Fast project reference and cross-agent working rules |
| `dev/architecture.md` | Package layout, data models, endpoints, caching, ViewModels, navigation, dependency injection, testing, and implementation traps |
| `dev/style.md` | Compose opt-ins, spacing, cards, elevation, typography, motion, top bars, search, pickers, and dialogs |
| `dev/platform-parity.md` | Shared and platform-specific behavior, iOS gaps, and iOS build setup |
| `dev/icons.md` | Material Symbols manifest for `AppIcons` tokens |
| `PRIVACY.md` | Published legal privacy representation, edited only with explicit approval |

## Project skills

Reusable workflows are under `agent-skills/`. Tool-specific directories should link to these canonical sources rather than copy them.

| Skill | Use when |
|---|---|
| `add-screen` | Adding a route, screen, ViewModel, or screen dependency injection wiring |
| `add-api-endpoint` | Adding or changing an endpoint, request or response model, pagination, caching, or scientific metadata writes |
| `audit-docs` | Auditing agent and developer documentation against the source |
| `release` | Bumping a version, building release artifacts, tagging, or publishing a release |

Load the relevant skill before beginning its workflow. `release` and `audit-docs` are explicit-only workflows.

## Build and validation

Use the Gradle wrapper with Java 17 or newer:

```bash
./gradlew <task>
```

| Task | Purpose |
|---|---|
| `:composeApp:compileAndroidMain` | Compile shared and Android Kotlin |
| `:composeApp:testAndroidHostTest` | Run common host unit tests |
| `:composeApp:compileKotlinIosSimulatorArm64` | Compile the iOS simulator target |
| `:androidApp:assembleDebug` | Build an installable debug APK |

Run `./scripts/verify-change.sh` for the standard Android compile and host-test gate. Build `:androidApp:assembleDebug`, not `:app:assembleDebug`; `composeApp` is the KMP library and `androidApp` is the application shell.

Expect a successful build with no new warnings. Before finishing, run checks proportionate to the changed targets and report anything that could not be run.

Common tests live under `app/src/commonTest/kotlin/` and mirror production package paths. Use `kotlin.test`, `kotlinx-coroutines-test`, and `runTest` for Flow-based behavior. Prefer injected clocks and recomputed expected values over sleeps or environment-dependent constants.

## Versioning, changelog, and releases

`gradle.properties` is the single source of truth for `app.versionName` and `app.versionCode`. Both application modules consume those values.

For a user-visible change, add one short sentence under the applicable `### Added`, `### Changed`, or `### Fixed` subsection of `## [Unreleased]`. State what changed for the user, not the implementation or root cause. Create the `Unreleased` heading if it is absent.

Use the `release` skill for every release. The ordering, separate debug and release Gradle invocations, generated build-config check, and signature verification are required safeguards.

## Architecture

- `app/src/commonMain/` contains shared UI, models, API code, repositories, preferences interfaces, navigation, and dependency injection.
- `app/src/androidMain/` and `app/src/iosMain/` contain platform implementations.
- `androidApp/` is the Android application shell. `iosApp/` is the Xcode project and Swift entry point.
- `CrucibleRepository` is the source of truth for in-memory caching through `ObservableCache`. `PersistentProjectCache` owns server- and account-bound disk caching.
- `DataSyncManager` owns persisted project restoration and synchronization. Keep lightweight project/resource summaries separate from authoritative detail caches, scope persistent replicas by both server and account, and guard asynchronous writes with the repository cache epoch.
- Repository network tests use Ktor `MockEngine` through `ApiClient.withEngine()`. Synchronization host tests use an in-memory `ProjectCacheStore`; do not add a general mocking framework for these paths.
- Feature screens that load data use ViewModels. Loading belongs in `viewModelScope`, not composables.
- `LoadState<T>` represents `Loading`, `Error`, and `Success`; use `isRefreshingNow` for pull-to-refresh state.
- `ApiResult<T>` wraps API calls through `safeCall`. Handle both `Success` and `Error` branches.
- Koin registrations live in `di/AppModule.kt`. A missing registration compiles and fails at runtime, so verify every new ViewModel and dependency registration.
- Screens resolve `AppPreferences` through Koin and collect its flows with `collectAsStateWithLifecycle`.
- `ResourceDetailScreen` receives a UUID and observes repository data. Do not pass mutable resource snapshots through navigation.
- Account-specific preferences and persistent cache data are scoped by stable account identity. API credentials use `SecureCredentialStore`, backed by Android Keystore encryption or the iOS Keychain.

## API rules

- Send `Authorization: Bearer {apiKey}` on authenticated calls.
- Select pagination by response shape: `fetchAllPagesCursor` for sample and dataset keyset pagination, `fetchAllPages` for offset pagination, and no helper for flat search responses.
- Scientific metadata is not accepted in sample or dataset create/update DTOs. Create and edit flows require a structural request followed by a dedicated metadata request.
- Metadata `POST` and `PATCH` return `ApiResult<JsonObject>`. On edit, use `diffMetadataWrite`: use `PATCH` for additions and replacements, or `POST ?overwrite=true` with the complete object when a key was deleted. On create, use a plain metadata `POST`.
- One-shot join-request mutations may call `apiClient.service` from the owning ViewModel when there is nothing to cache. Pending join-request counts belong in the repository because multiple screens consume them.
- Project search and project detail are readable by any authenticated user. Lead details and scientific metadata may be absent for non-members, and that asymmetry supports discovery browsing.
- Use explicit `@SerialName` values for API fields whose wire names must remain stable.

## User identity

- For a person already in context, use `userDisplayName()` for full-name text and `UserIdentityRow` for list rows. `UserIdentityRow` uses `compactUserDisplayName()` to abbreviate each given name, preserves the family name, and shows `@username` as supporting text. Both name formatters fall back to `@username`, then ORCID or unique ID.
- Search result rows are name-first with `@username` as supporting text. `UserProfileScreen` remains username-primary.
- Pass `user.uniqueId` as the ORCID to `UserAvatar` so its deterministic color remains stable.
- Owner and member rows navigate to the in-app user profile, not directly to orcid.org.

## UI conventions

The complete design rules live in `dev/style.md`. These rules are mandatory:

- Use `AppIcon(AppIcons.X)`, not direct Material icon constants. Update `dev/icons.md` when adding tokens.
- Use `ExpandChevron` for expanding in place and `AppIcons.NavigateNext` for navigation.
- Use motion specifications from `AppAnimations.kt`, elevation from `AppElevation`, shapes from `MaterialTheme.shapes`, and the stock typography ramp from `ui/theme/Type.kt`.
- Use `PullToRefreshBox` without translating content by `distanceFraction`.
- Use the existing shared user, resource, search, dialog, and top-bar components before creating a new local variant.
- The app targets phones and intentionally has no adaptive window-size-class layout.
- Dynamic color is Android-only. Static accent schemes are curated resources, not generated at runtime.

## Platform constraints

- Shared behavior should remain in `commonMain` unless it requires a platform API.
- Android preferences use DataStore. iOS preferences use NSUserDefaults through multiplatform-settings.
- Android API credentials use Keystore-backed AES-GCM encryption. iOS credentials use a device-only Keychain item.
- Kotlin/Native iOS compilation can be checked on supported hosts, but a runnable iOS app requires macOS and Xcode. See `dev/platform-parity.md`.
- When adding an `expect` or platform abstraction, implement and validate every target in the same change.

## Implementation traps

- Use `collectAsStateWithLifecycle`, not `collectAsState`, in navigation and lifecycle-bound UI.
- Keep `SearchScreen` on its currently supported Material 3 SearchBar API until the replacement is stable in the project's dependency version.
- Keep swipe-to-hide behavior centralized in `ui/common/SwipeToHideItem.kt`; re-arm a dismissed item with the caller's key rather than resetting its state.
- Android edge-to-edge icon contrast follows the resolved app theme, not raw system dark mode.
- Generated build constants come from `generateAppBuildConfig`. Do not hand-edit generated files.
- Avoid multiline text replacement with `sed`; use `apply_patch` for source and documentation edits.
