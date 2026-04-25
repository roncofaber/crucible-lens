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

## No comments rule

Default: write no comments. Only add one when the WHY is non-obvious (hidden constraint,
workaround for a specific bug, subtle invariant). Never describe what the code does —
well-named identifiers do that.
