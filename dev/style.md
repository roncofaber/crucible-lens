# UI Style Guide

## Compose experimental opt-ins

The Kotlin compiler is set to treat warnings as errors. Any use of experimental
Compose APIs requires an explicit `@OptIn` annotation on the composable (or file-level):

| API | Annotation |
|-----|-----------|
| `HorizontalPager`, `rememberPagerState` | `@OptIn(ExperimentalFoundationApi::class)` |
| `combinedClickable` | `@OptIn(ExperimentalFoundationApi::class)` |
| `TopAppBar`, `ModalBottomSheet`, pull-to-refresh | `@OptIn(ExperimentalMaterial3Api::class)` |
| Animated content/transitions | `@OptIn(ExperimentalAnimationApi::class)` |

The top-level screen composables in this project carry all three on one line:
```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
```

Private helpers only need the annotations relevant to their own body.

---

## Spacing & layout

| Context | Value |
|---------|-------|
| Screen edge padding | `16.dp` |
| Card internal padding | `12–16.dp` |
| Items inside a card (`Column`) | `Arrangement.spacedBy(6–8.dp)` |
| Top-level sections in a LazyColumn | `Arrangement.Top` + `padding(bottom = 16.dp)` inside each item's content box (NOT `Arrangement.spacedBy` — it creates phantom gaps for zero-height AnimatedVisibility items) |
| Icon button size (top bar) | `Modifier.size(40.dp)`, icon `24.dp` |
| Small inline icons | `14–18.dp` |

---

## Cards

Standard resource card: elevation `1.dp`, default `CardDefaults.cardColors()`.  
Section / info card: `containerColor = MaterialTheme.colorScheme.surfaceVariant`.  
Header surface: `color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)`.

```kotlin
// Typical resource list card
Card(
    modifier = Modifier.fillMaxWidth(),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
) { ... }
```

---

## Typography

| Role | Style | Weight |
|------|-------|--------|
| Card title / resource name | `titleSmall` | `SemiBold` |
| Section header | `titleMedium` | `Bold` |
| Metadata label | `labelSmall` | default |
| Body copy | `bodyMedium` | default |

Primary-coloured text: use `color = MaterialTheme.colorScheme.primary` on the title.  
Muted secondary text: `color = MaterialTheme.colorScheme.onSurfaceVariant`.

---

## AnimatedVisibility for lazy-list items

When an item in a `LazyColumn` can appear/disappear, **always keep the slot present**
and wrap content in `AnimatedVisibility` — never conditionally add/remove the `item {}` block,
which causes the surrounding items to jump.

```kotlin
item(key = "my_card") {
    AnimatedVisibility(
        visible = condition,
        enter = expandVertically() + fadeIn(),
        exit = ExitTransition.None   // no collapse animation — just disappears
    ) {
        Box(modifier = Modifier.padding(bottom = 16.dp)) {
            MyCard(...)
        }
    }
}
```

Using `ExitTransition.None` avoids a shrink animation that fights the LazyColumn's own layout.

---

## Horizontal group paging (ProjectDetailScreen pattern)

When resources are grouped (by type, date, etc.), use:
1. `ScrollableTabRow` with one tab per group — only shown when `groups > 1`
2. `HorizontalPager` (inner) whose pages each get their own `LazyColumn`, `LazyColumnScrollbar`, and `ScrollToTopButton`

Each inner page is self-contained; there is no shared `listState` passed from outside.
The outer `HorizontalPager` (Samples / Datasets tab) nests the inner one.

---

## Collapsing identity header (ProjectDetailScreen / InstrumentDetailScreen pattern)

The name/lead/org/type/location block ("identity") scrolls away with the list — WhatsApp
group-page style — while search/group-by/sort ("controls") stay pinned above it. No custom
scroll math, `NestedScrollConnection`, or interpolated header height is involved:

- **Identity** is passed as real list content via `SamplesList`/`DatasetsList`'s
  `leadingContent: (LazyListScope.() -> Unit)?` parameter (an `item { }` at the top of each
  `LazyColumn`), or as a plain `item { }` for `InstrumentDetailScreen`'s single list. It scrolls
  normally — it's just item 0.
- **Controls** are a separate composable rendered outside the pager for
  `ProjectDetailScreen` (search applies to both Samples/Datasets tabs from one shared
  `searchQuery`, so it can't live inside just one tab's list) or a `stickyHeader { }` inside the
  `LazyColumn` for `InstrumentDetailScreen`, matching `ProjectsListScreen`'s own
  `stickyHeader(key = "search_bar")` convention.
- Any state that replaces the list with something else (an `EmptyListCard`, an owner-name
  loading spinner) must still be its own `LazyColumn` with `leadingContent` invoked first — do
  not fall back to a bare `Box`, or identity disappears whenever that tab has zero results.
  `ProjectDetailScreen`'s `Loading`/`Error`/non-member states are the one accepted exception:
  there's no list to attach identity to yet, so it's simply absent until data loads.
- The icon+name `Row` is `.clickable { onManageProject() }` / `onManageInstrument()` — ripple
  only, no chevron, no color change. It's a sibling of the pin `IconButton`, not a shared
  clickable ancestor wrapping it, so the two tap targets never nest (see "Notification dots"-style
  layering — never put an unrelated actionable button inside another element's own tap target).
- The project lead's name uses `userDisplayName()` (`CLAUDE.md`'s "User identity conventions")
  like everywhere else a person is shown in context — full name, no username by default, one
  tap away via the profile the name already opens.
- Small inline icons in these headers (lead/org/date/type/location) are `14.dp`, matching this
  file's documented 14–18dp floor — not the `11.dp` these headers used before this pattern.

---

## Notification dots

Use `NotificationDot` (`ui/common/NotificationDot.kt`) for any "something needs your attention"
count on an icon — a thin wrapper around `BadgedBox`/`Badge()` (12.dp, down from M3's 16.dp
default) so the size/style changes in one place, not at every call site. Takes `count: Int?`
and wraps the icon content; `null` or `<= 0` hides the badge entirely:

```kotlin
NotificationDot(count = pendingRequestCount) {
    AppIcon(AppIcons.ManageMembers)
}
```

Not for `SearchScreen`'s active-filter-count badge — that one is always shown once filters are
active (a persistent state readout), where `NotificationDot` is for a count that's normally
absent and only appears when something needs the user's attention.

---

## No comments rule

Default: write no comments. Only add one when the WHY is non-obvious (hidden constraint,
workaround for a specific bug, subtle invariant). Never describe what the code does —
well-named identifiers do that.
