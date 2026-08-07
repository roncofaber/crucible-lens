---
name: add-screen
description: Add a new feature screen to Crucible Lens - route, NavGraph entry, ViewModel, Koin registration, and scaffold. Use this whenever adding a new screen, page, or destination to the app, or when wiring up navigation to something that doesn't have a route yet, even if the user only describes the UI they want. Getting the route encoding or the Koin registration wrong fails at runtime, not at compile time, so follow the wiring order here rather than pattern-matching from one existing screen.
---

# Adding a screen

A new screen touches five files. The two failure modes that matter both compile cleanly and break at
runtime, so they are called out where they occur.

Work in this order - each step depends on the previous one existing.

## 1. Route - `ui/navigation/Screen.kt`

Add an `object` to the `Screen` sealed class. If the route takes arguments, add a `createRoute`
helper alongside it:

```kotlin
object WidgetDetail : Screen("widget/{widgetId}") {
    fun createRoute(widgetId: String) = "widget/${encodeRouteSegment(widgetId)}"
}
```

**`createRoute` must run every interpolated argument through `encodeRouteSegment`.** Crucible UUIDs
and names routinely contain `/`, `?`, `&`, `=`, and spaces; an unencoded one silently produces a
route that matches nothing, and navigation just does nothing with no error. The handful of existing
routes that skip it (`ProjectDetail`) predate the helper and are not the pattern to copy.

## 2. NavGraph entry - `ui/navigation/NavGraph.kt`

```kotlin
composable(Screen.WidgetDetail.route) { backStackEntry ->
    val widgetId = backStackEntry.arguments?.getString("widgetId").orEmpty()
    WidgetDetailScreen(widgetId = widgetId, onBack = { navController.popBackStack() })
}
```

`NavGraph` takes 5 parameters (`navController`, `deepLinkUuid`, `openScanner`, `onScannerOpened`,
`viewModel`). `AppPreferences` is **not** one of them - obtain it internally via
`koinInject<AppPreferences>()`, collect preference flows with `collectAsStateWithLifecycle`, and call
`prefs.saveXxx()` directly. Don't thread it through as a parameter.

## 3. ViewModel - alongside the screen, in its feature package

Data loading belongs in `viewModelScope`, never in a composable. Expose one
`StateFlow<LoadState<T>>`. `ui/instruments/InstrumentListViewModel.kt` is the canonical shape:

```kotlin
class WidgetListViewModel(
    private val repository: CrucibleRepository
) : ViewModel() {

    private val _loadState = MutableStateFlow<LoadState<List<Widget>>>(LoadState.Loading)
    val loadState: StateFlow<LoadState<List<Widget>>> = _loadState.asStateFlow()

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
                when (val resp = repository.fetchWidgets(forceRefresh)) {
                    is ApiResult.Success -> _loadState.value = LoadState.Success(resp.data)
                    is ApiResult.Error -> _loadState.value = LoadState.Error("Failed to load widgets")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _loadState.value = LoadState.Error("Connection error - check your network")
            }
        }
    }
}
```

Two details that are easy to drop: re-emit `Success(current, isRefreshing = true)` on refresh so the
list stays on screen instead of flashing a spinner, and rethrow `CancellationException` before the
generic catch so coroutine cancellation isn't swallowed into a fake error state.

## 4. Koin registration - `di/AppModule.kt`

```kotlin
viewModelOf(::WidgetListViewModel)
```

**A missing registration compiles fine and throws at runtime** the first time the screen opens, so
add it in the same change as the ViewModel. `ApiClient`, `CrucibleRepository`, and `DataSyncManager`
are `single`s already in the graph; constructor-inject whichever you need.

A few leaf composables (`InstrumentPickerField`, `FilterSheet`, `AssociatedFilesCard`) call
`koinInject<T>()` directly instead of taking a ViewModel - that's a deliberate exception for
self-contained widgets, not the default for a screen.

## 5. Screen composable

```kotlin
@Composable
fun WidgetListScreen(onBack: () -> Unit) {
    val viewModel: WidgetListViewModel = koinViewModel()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    AppScaffold(topBar = { AppTopBar(title = "Widgets", onBack = onBack) }) {
        PullToRefreshBox(
            isRefreshing = loadState.isRefreshingNow,
            onRefresh = { viewModel.load(forceRefresh = true) }
        ) {
            when (loadState) { /* Loading / Error / Success */ }
        }
    }
}
```

Use `LoadState.isRefreshingNow` for the pull-to-refresh flag - it is true only when data is already
loaded *and* a refresh is in flight, which is what keeps the initial load from rendering as a
refresh. Don't reintroduce separate `isLoading` / `isRefreshing` booleans.

For a detail screen whose title is an entity name rather than a static label, use
`CollapsingAppTopBar` instead of `AppTopBar` - see `dev/style.md`'s "Collapsing top bar" section for
the required `scrollBehavior` and nested-scroll wiring, which is order-sensitive.

## Before you finish

- Icons via `AppIcon(AppIcons.X)`, never `Icon(Icons.Default.*)`
- Shapes via `MaterialTheme.shapes.X`, never a hardcoded `RoundedCornerShape(N.dp)`
- Animation specs from `ui/common/AppAnimations.kt`, never an inline tween
- Add a `CHANGELOG.md` entry under `## [Unreleased]` - a new screen is user-visible
- Compile: `JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain`

Deeper reference: `dev/architecture.md` (navigation routes, DI, ViewModels) and `dev/style.md`
(spacing, typography, row/card conventions).
