# UI Style Guide

Conventions for `app/src/commonMain/kotlin/crucible/lens/ui/`. Rules first; the rationale exists so a
rule doesn't get "simplified" back into the bug it prevents.

---

## Compose experimental opt-ins

Annotate the narrowest scope that needs it, never a blanket three-API line.

| API | Annotation | Where |
|---|---|---|
| `TopAppBar`, `ModalBottomSheet`, `SearchBar`, pull-to-refresh, `TopAppBarScrollBehavior` | `ExperimentalMaterial3Api` | `@file:OptIn(ExperimentalMaterial3Api::class)` - the common case, since most screens use several |
| `HorizontalPager`, `rememberPagerState`, `stickyHeader`, `combinedClickable` | `ExperimentalFoundationApi` | `@OptIn(...)` on the composable |
| `AnimatedContent` transition specs | `ExperimentalAnimationApi` | `@OptIn(...)` on the composable |

A missing opt-in is only a warning (`allWarningsAsErrors` is off), but the expected build output is
`BUILD SUCCESSFUL` with no warnings - treat it as an error.

---

## Spacing & layout

| Context | Value |
|---|---|
| Screen edge padding | `16.dp` |
| Card internal padding | `12-16.dp` |
| Items inside a card (`Column`) | `Arrangement.spacedBy(6-8.dp)` |
| Top-level sections in a `LazyColumn` | `Arrangement.Top` + `padding(bottom = 16.dp)` inside each item's content |
| Icon button size (top bar) | `Modifier.size(40.dp)`, icon `24.dp` |
| Small inline icons | `14-18.dp` |

Never `Arrangement.spacedBy` on a `LazyColumn`: it spaces *every* slot including zero-height
`AnimatedVisibility` items, producing phantom gaps.

---

## Rows & cards

**List rows are not `Card`s.** Rows are flat, full-width, separated by whitespace and `SectionHeader`
dividers, never wrapped in an inset elevated `Card`.

| Component (`ui/common/`) | Use |
|---|---|
| `ResourceRow` (`ResourceListComponents.kt`) | The standard list row - `ResourceCard` plus its trailing divider |
| `ResourceCard` | Full-bleed `Box { Row { … } }`; no container, no elevation, `combinedClickable` + `padding(horizontal = 16.dp, vertical = 8.dp)` |
| M3 `ListItem` | Project/instrument/search/history rows |
| `ListRowDividerInset` (72.dp) | Divider inset under an icon-leading row. Shared by `ResourceRow` and the hand-rolled Search/History rows so they line up on one grid |
| `EmptyListCard` | Shared empty state; caller supplies `emptyMessage` |
| `SearchBar` | The one search field look, `surfaceContainerHigh`/`onSurfaceVariant` - neutral, not accent-tinted. Used standalone (Projects/Instruments search, Home's search entry point) and via `ResourceControlsBar`. Only `shape`/`contentPadding` are customizable; every property that could drift is fixed here |
| `ResourceControlsBar` | Shared search + group-by + sort bar (`ProjectDetailScreen`, `InstrumentDetailScreen`). Callers supply group options and `containerColor` (Project paints it on a `surface` column, Instrument renders it as a `stickyHeader` that must be opaque against `background`). Its search field is a `SearchBar` call, not a second copy |
| `LongPressMenuBox` | Owns the `expanded` state, anchoring `Box`, and `DropdownMenu` for a row's long-press menu. `content` receives the `onLongClick` lambda for the caller's own `combinedClickable`, since the click target varies (`ResourceCard`, `ProjectCard`). Items come from `OverflowMenuItems.kt` so an action reads identically in a long-press menu and a top-bar overflow |

`Card` is reserved for genuinely contained blocks. A new elevated card needs a documented reason.

| Use | Style |
|---|---|
| Info / section card (empty states, metadata blocks) | `containerColor = surfaceContainerLow`, no elevation |
| `HomeScreen`'s transient error banner | `errorContainer` + `cardElevation(AppElevation.Level1)`; it slides in over content. The **only** `cardElevation` call in the app |
| `SectionHeader`, expanded | `Surface(color = surfaceContainerHigh)` |
| `SectionHeader`, collapsed | `Surface(color = surface)` + a leading `outlineVariant` divider - it pins above nothing, so it reads as a plain row |
| Tinted accent surface (stat tiles, `SectionHeader`'s count badge) | `Surface(color = secondaryContainer)`, content `onSecondaryContainer` |

### Accent-derived surfaces

M3 expresses elevation as **tonal colour**, not shadow. Chrome above content uses a
`surfaceContainer*` role - `surfaceContainer` for anything pinned, sticky section headers especially.
Never hardcode a grey for "slightly raised".

This works because `resolveAccentColorScheme()` (`Theme.kt`) looks up a hand-curated static
`ColorScheme` per accent (`ui/theme/accents/`, one file each, exported from the Material Theme
Builder) with **every** container role explicitly assigned, per contrast level
(`LightStandard`/`LightMedium`/`LightHigh`/`Dark*`). Palettes that set only
`primary`/`secondary`/`tertiary` let container roles fall through to M3's purple-seeded baseline -
that's how a blue-accented app rendered purple-grey headers. There is no runtime colour generation: a
new accent is one more exported file, not a formula.

`SearchScreen`'s expanded `SearchBar` is the deliberate exception - it keeps stock
`SearchBarDefaults.colors()` (container defaults to `surfaceContainerHigh`), being standard M3 chrome
rather than a full-bleed page background.

### No custom alpha

M3 pre-computes every emphasis level as a distinct, contrast-guaranteed role, so `.copy(alpha = X)`
reopens the contrast risk that role exists to close - `onSurfaceVariant` is its own computed colour
precisely *because* it isn't "`onSurface` at 60%".

| Need | Reach for | Not |
|---|---|---|
| De-emphasized text/icon on a plain surface | `onSurfaceVariant`, full opacity | `onSurface`/`onSurfaceVariant.copy(alpha = X)` |
| De-emphasized text/icon on a container | the matching `onXContainer`, full opacity | `onXContainer.copy(alpha = X)` - hierarchy inside a container comes from type scale, not a second opacity signal |
| Tinted "selected/accent" chip or badge | `primaryContainer` / `onPrimaryContainer` | `primary.copy(alpha = 0.12-0.15f)` |
| Decorative divider | `outlineVariant`, full opacity | `outlineVariant.copy(alpha = X)` |
| Text field border | `outline` (M3's stock default) | `outline.copy(alpha = X)` |

**Disabled content is the one exception**, and still not an arbitrary number:
`AppContentAlpha.Disabled` (`ui/common/AppContentAlpha.kt`) is `0.38f`, matching M3's own
`DisabledIconOpacity`/`DisabledLabelTextOpacity`, paired with `onSurfaceVariant`. Disabled is a
*state* any element can enter, not a colour family - WCAG 1.4.3 exempts inactive components from the
contrast requirement, so M3 implements it as a fixed opacity reduction rather than a role.

`LazyColumnScrollbar`'s thumb is solid `primary` - narrow and fast-moving enough that opacity buys
nothing. A one-off, not a precedent.

### Collapsible section headers

`SectionHeader`'s `onToggle` is nullable - pass a lambda when the section collapses; when null the
chevron isn't drawn and the row isn't clickable. An interactive-looking chevron that does nothing is
worse than no chevron (that bug shipped once, in Search).

All current callers collapse: resource groups on both detail screens, "Hidden" on both list screens,
and Search's Projects/Samples/Datasets. Search's default to expanded and keep state in
`rememberSaveable` so a collapse survives opening a result and navigating back.

---

## Elevation

`ui/common/AppElevation.kt` declares the 6 canonical M3 levels (`Level0`-`Level5` = 0/1/3/6/8/12dp).
Pass these to `tonalElevation`, `shadowElevation`, `CardDefaults.cardElevation()`, and
`FloatingActionButtonDefaults.elevation()` - never an inline `Xdp`. The resting-level-per-component
map is in that file's KDoc, sourced from `m3.material.io/styles/elevation`.

- **`Level4`/`Level5` are hover/focus/drag states only.** Never a resting value.
- **`tonalElevation` is a silent no-op once you set `color`.** Compose's tonal blend applies only
  while `color` is the default `colorScheme.surface`. If a surface looks flat, check this before
  raising the value.
- **Elevation and container colour are independent decisions.** Surface tint is deprecated and
  surface roles are not tied to elevation: pick a dp level for z-depth and a
  `surface`/`surfaceContainer*` role for colour, separately.

Default to no override. Every `Card` on the resource detail screen (`BasicInfoCard`,
`SampleDetailsCard`/`DatasetDetailsCard`, `LinkedResourceCards`, `AssociatedFilesCard`,
`ThumbnailsSection`) is a bare `Card { }`. M3's bar for a shadow is protection from a busy background
or signalling interactivity; clearing it needs a documented reason, the way `QrCodeDialog`'s
`tonalElevation = 0` carve-out does.

**Nested surfaces recede, they don't share a tier.** `LinkedResourceCards.kt`'s `ResourceRow` uses
`surface` because it nests inside a `surfaceContainerHighest` `Card` - deliberately different from
the standalone flat-tinted-row convention, which is `surfaceContainerLow` (~10 call sites:
`AddFilesScreen`, `MetadataEditor`, `ResourceListComponents`, `InstrumentListScreen`,
`ProjectsListScreen`, `UserComponents`, `SampleDetailsCard`, `DatasetDetailsCard`). The legacy
`surfaceVariant` role is retired; two call sites in `LinkResourceSheet.kt` should move to
`surfaceContainerLow` next time that file is touched. Accent files under `ui/theme/accents/`
legitimately still *assign* it, since M3's `ColorScheme` still declares it.

---

## Typography

| Treatment | Role | Size / weight | Colour |
|---|---|---|---|
| Expanded collapsing-bar hero | `emphasizedTitleLarge` | 22 Medium | `onSurface` |
| **Surface title** - every top bar (static + collapsed) and every sheet title | `titleLarge` | 22 Regular | `onSurface` |
| Card / dialog heading | `titleMedium` | 16 Medium | `onSurface` |
| **Group header** - in-list, has a container | `titleMedium` | 16 Medium | `onSurface` on `surfaceContainer` |
| Text input | `bodyLarge` | 16 Regular | M3's text-field default; never set it |
| **Row title / body copy / paragraphs** | `bodyMedium` | 14 Regular | `onSurface` |
| Button / chip label | `labelLarge` | 14 Medium | M3's button default; never set it |
| Secondary line / caption / metadata | `bodySmall` | 12 Regular | `onSurfaceVariant` |
| Machine ID (mfid, project/instrument ID) | `bodySmall` monospace, via `IdText` | 12 Regular | `onSurfaceVariant` |
| Inline section label | `labelMedium` | 12 Medium | `primary` (or `onSurfaceVariant` when nested in a card that already carries an accent) |
| Detail row label (`InfoRow`/`ClickableInfoRow`) | `titleSmall` | 14 Medium | `onSurfaceVariant` |

- **Stock ramp only.** `ui/theme/Type.kt` matches M3 1.4.0's defaults exactly. Every `Text` uses a
  plain `MaterialTheme.typography.X`: no `fontSize =` outside `Type.kt`, no `.copy()` that changes a
  role's size or weight, no extension properties beyond the `emphasizedX` block. If a size feels
  wrong, change the element's role.
- **Size cap: nothing above 22 sp.** Only two sizes carry all the chrome (22 for surface titles, 16
  for both header kinds); weight does the rest.
- **Retired roles:** all `display*`, all `headline*`, `labelSmall`, and all but two emphasized
  variants. `titleSmall` is sanctioned *only* for the detail-row label, where it sits at the same
  14 sp as the `bodyMedium` value it labels so the two align in a `label: value` row.
- **Governing principle:** pick the role that means the thing; carry emphasis with colour and
  container, not weight. M3 puts body text on `onSurface`, `onSurfaceVariant` for muted, and reserves
  `primary` for hyperlinks.
- **Never set a style on a button label or `textStyle` on a text field** - M3 supplies `labelLarge`
  and `bodyLarge`. Two carve-outs: `BasicTextField` has no `color` parameter, so `.copy(color = …)`
  is unavoidable (`SearchBar.kt`), and `MetadataEditor`'s dense key-value fields stay at `bodySmall`
  because they reflow badly larger.
- **List row titles are 14 sp**, deliberately below M3's `ListItem` spec of 16 - density matters in a
  data-heavy scientific app. Subtitles stay 12 sp.
- **Tabular figures** (`fontFeatureSettings = "tnum"`) on any number that changes in place, so digits
  don't shift width. `SectionHeader`'s count badge does this.

`Settings → Typography` (debug builds only, via `isDebugBuild`) renders the full ramp plus in-context
mocks. The greps enforcing all of the above live in the `audit-docs` skill.

### Emphasis: use the emphasized styles, never `fontWeight`

**`fontWeight` must not appear in `ui/` outside `Type.kt`**, and currently doesn't. M3's scale has 30
styles, not 15: the baseline set plus an *emphasized* set (same size, line height and tracking, one
weight heavier) from the May 2025 Expressive update. Weights are Regular, Medium, and Bold;
**SemiBold exists nowhere in M3**, so reaching for it means you want one of these.

Compose 1.4.0 ships the emphasized tokens with every accessor `internal`, so `Type.kt` derives all 15
locally. Two traps:

- **They must be named `emphasizedTitleMedium`, not `titleMediumEmphasized`.** The latter collides
  with Compose's internal member, and Kotlin resolves members ahead of extensions, so the call site
  binds to the inaccessible one and fails with `INVISIBLE_MEMBER`. `@file:Suppress` "fixes" it only
  by defeating the visibility system.
- **Never import the 15 stock roles from `crucible.lens.ui.theme`** - they're real `Typography`
  members; only the `emphasizedX` extensions are imported. Rewriting an `emphasizedX` call site
  without dropping its import fails to compile in a way that points at the wrong file.

Only `emphasizedTitleLarge` is sanctioned, for the expanded collapsing-bar hero. Card and dialog
headings use plain `titleMedium` (converted app-wide from `emphasizedTitleMedium`): the icon,
position, and divider already separate a card heading from its body. Emphasising a `body*` or
`label*` role means the element wants a *different* role. When CMP ships a stable M3 with the
official accessors, delete the block in `Type.kt` and rename this one to `titleLargeEmphasized`.

### `IdText` and `autoSize`

**`IdText`** (`ui/common/IdText.kt`) is the shared look for any mfid/project/instrument ID -
`bodySmall`, monospace by default (`monospace = false` opts out), `onSurfaceVariant`. Purely visual;
callers attach click/copy behaviour via `modifier`, since some IDs are tap-to-copy, some sit beside a
copy button, some are reachable only from a long-press menu. **Don't use it on a tinted container** -
its colour is hardcoded to `onSurfaceVariant`, which assumes a plain `surface` background
(`SearchScreen`'s "Open resource directly" banner correctly stays a hand-written `Text`).

**For a line that must never wrap or clip, use `autoSize` on one `Text`.**
`TextAutoSize.StepBased(minFontSize, maxFontSize)` shrinks to the largest size that still fits at
`maxLines`, falling back to `overflow` only if it doesn't fit at `minFontSize`. Cap `maxFontSize` at
the role's own size so it only shrinks. `HomeScreen`'s footer uses this with `maxLines = 1`.

This only works as **one** `Text`: `autoSize` measures a single call's content as a unit, so three
`Text`s in a `Row` land on different sizes. Where a line needs multiple colours or a clickable
segment, build one `AnnotatedString` (`buildAnnotatedString` + `withStyle`) and attach clicks as a
`LinkAnnotation.Clickable` span (`withLink`) inside it - not a separate `Modifier.clickable` `Text`.

---

## AnimatedVisibility for lazy-list items

When an item in a `LazyColumn` can appear or disappear, **keep the slot present** and wrap the content
in `AnimatedVisibility` - conditionally adding/removing the `item {}` block makes surrounding items
jump. Use `enter = expandVertically() + fadeIn()` and `exit = ExitTransition.None`; a shrink
animation on exit fights the `LazyColumn`'s own layout.

---

## Skeleton loading placeholders

Use `SkeletonRow`/`SkeletonBlock` (`ui/common/SkeletonLoading.kt`) for a list's initial
`LoadState.Loading`, so the loading state previews what's about to appear and swapping in real content
doesn't jump:

```kotlin
loadState is LoadState.Loading -> items(count = 6, key = { "__skeleton_${it}__" }) {
    SkeletonRow(hasSupportingLine = true, hasTrailing = true)
}
```

- **Only for a known, row-shaped list.** `LoadingContent` (full-screen spinner) and `LoadingItem`
  (inline spinner) remain correct for loads with no shape to preview yet (`ResourceDetailScreen`
  pre-siblings-resolved has no pager or cards to lay out) and for small individually-loading pieces
  (`CountChip`, a submit button).
- **`SkeletonRow` mirrors the app's two real row shapes**: `hasLeadingIcon` (default `true`, for
  `ListItem`-based rows like `ProjectCard`/`InstrumentCard`; `false` for icon-less `ResourceRow` rows
  such as search results, reusing `ResourceCard.kt`'s own `56.dp` start inset so the text columns line
  up), `hasSupportingLine` (a second, shorter bar), and `hasTrailing` (a chip-shaped block).
- **Pulses between `surfaceContainerHigh` and `surfaceContainerHighest`** via
  `InfiniteTransition.animateColor` - two real roles crossfaded, not alpha on one, per "No custom
  alpha". Same technique as `SectionHeader`'s crossfade, just looped (`RepeatMode.Reverse`). Each row
  runs its own `rememberInfiniteTransition` rather than sharing a hoisted clock - indistinguishable in
  use, and avoids threading a pulse colour through every call site.
- **No section headers above skeleton rows** - a `SectionHeader` only ever appears once grouped
  `Success` data exists.

---

## Tabs + grouping (`ProjectDetailScreen`)

Two mechanisms that don't nest:

1. **Tabs = resource kind.** A fixed two-tab `PrimaryTabRow` (Samples / Datasets) driving one
   `HorizontalPager(pageCount = { 2 })`. No `ScrollableTabRow` and no per-group tab anywhere; the tab
   count never varies with the data.
2. **Groups = sticky sections inside one `LazyColumn`.** `groupedResourceItems` (a `LazyListScope`
   extension in `ProjectResourceLists.kt`) emits a `stickyHeader` per group via `SectionHeader`, then
   that group's `ResourceRow`s. `GroupBy.NONE` emits a flat `items(...)`.

Each pager page (`SamplesList`/`DatasetsList`, both in `ProjectResourceLists.kt`) owns its own
`rememberLazyListState`, `LazyColumnScrollbar`, and `ScrollToTopButton`, so the tabs scroll
independently. Keeping list rendering here leaves `ProjectDetailScreen` a layout + state container.

**Groups render in full - no row cap, no "Load more".** `CrucibleRepository.fetchProjectData` caches
the complete lists before the screen renders, and `LazyColumn` only composes near the viewport.

**`LazyColumn`'s `content: LazyListScope.() -> Unit` is not a composable slot** - only the trailing
lambdas of `item {}`/`stickyHeader {}`/`items {}` are. That's why `groupedResourceItems` is a plain
function, not `@Composable`: any composable state it needs (`rememberOwnerNames`, the
`rememberSaveable` expand-state map, grouping, sorting) is resolved by the calling
`SamplesList`/`DatasetsList` and passed in already-sorted. Grouping
(`remember(samples, groupBy, ownerNames)`) and per-group sort (`remember(groupedByKey, sortState)`)
stay separate `remember`s so a sort-only change doesn't redo the more expensive regrouping.

---

## Collapsing top bar (`ProjectDetailScreen`, `InstrumentDetailScreen`)

`CollapsingAppTopBar` (`ui/common/AppTopBar.kt`) is a sibling of the plain `AppTopBar` used by the ~20
static-title screens. It is one ordinary `Surface` + `Column`, **composed exactly once**, reading
`scrollBehavior.state.collapsedFraction` directly: a fixed-height top row (nav icon + `actions`,
always present) holding the collapsed single-line `name`, and below it the expanded block (icon badge
+ `name` up to 3 lines + `expandedContent`).

### Three requirements that break silently if dropped

- **Set `scrollBehavior.state.heightOffsetLimit` manually.** This is the bookkeeping a real M3 app
  bar does in its own measure pass. It defaults to `-Float.MAX_VALUE`, and
  `exitUntilCollapsedScrollBehavior()`'s `onPreScroll` consumes the *entire* upward delta until
  `heightOffset` reaches the limit - unbounded, that's forever, so the whole list stops scrolling,
  not just the header. `CollapsingAppTopBar` measures the expanded block in a `Modifier.layout` on
  its content and publishes from that layout pass (comparing against a plain, non-snapshot
  `remember`ed box so it never reads back what it just wrote). **Not** from a `SideEffect`, which
  runs before measurement.
- **Attach `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to a child *inside*
  `PullToRefreshBox`'s content**, never to `PullToRefreshBox`'s own `modifier` or further out on
  `AppScaffold`. Nested scroll dispatches to the nearest ancestor first; attaching ours further out
  means a downward drag at the top of the list is swallowed by pull-to-refresh, so the bar never
  re-expands and a refresh fires instead.
- **No `AnimatedVisibility`, no animation spec.** Every scroll-varying value (bar height, both title
  alphas, container colour) is a pure function of `collapsedFraction`, read inside a
  `layout`/`graphicsLayer`/`drawBehind` lambda so it resolves in layout/draw and never recomposes
  while scrolling. An earlier threshold-plus-spring version ignored the finger until it jumped at the
  halfway point, ran a second animation over the scroll behavior's release snap, and fed its
  animating height back into `heightOffsetLimit`.

**Why not `MediumTopAppBar`:** it renders its single `title` slot twice and crossfades, so both
instances share whatever content decision lives in that lambda - per-row content flashes the
oversized expanded content in the compact strip early in a scroll. `TwoRowsTopAppBar`'s
`title: @Composable (expanded: Boolean) -> Unit` is the real fix but compiles `internal` in this CMP
artifact. `TopAppBarDefaults.MediumAppBarCollapsedHeight` and `.windowInsets` are still reused.

### Layout and content

- **Colour**: the container lerps `expandedContainerColor` → `surface` via `drawBehind`, so fully
  collapsed matches the page background. Both params default to
  `secondaryContainer`/`onSecondaryContainer` and neither screen overrides them. Everything inside
  `expandedContent` is `onSecondaryContainer`; don't mix in `onSurfaceVariant`, which only pairs
  safely with the `surface` family. Hierarchy comes from type scale alone. Override the two params
  **together** or the content loses its guaranteed contrast.
- **Expanded layout is left-aligned around an icon badge**, matching `LargeTopAppBar`: a circular
  `primary`/`onPrimary` badge (`HeroIconBadgeSize` = 44dp), with `expandedContent` indented by
  `HeroIconBadgeSize + HeroIconBadgeSpacing` so its left edge lines up with the title's. Inline icons
  in that block are `14.dp`.
- **`ProjectDetailScreen`'s byline is one line** ("Tim Kodalle · LBNL"): the lead is the only tappable
  part so it alone is `primary`, via `userDisplayName()`. Member count and sync status demote to a
  smaller second line.
- **The `name` is gated on the entity resolving** (`project?.title ?: projectId`), **not** on the
  resource-list load state - the entity fetch is usually warm from the list screen and independent of
  the slower samples/datasets fetch.
- **Member count** reads `CrucibleRepository.observeProjectMembers(projectId)`, the same cache
  `rememberOwnerNames` (OWNER group-by) uses, so opening a project fetches members once.
- **The pin toggle lives in `actions`** so it stays reachable at any scroll position; the search icon
  was dropped, since `ResourceControlsBar` covers in-screen search. List screens still show pin
  inline per-card - don't assume every top bar needs one.
- **Group headers and rows are shared**: both screens call `SectionHeader` and `ResourceRow` directly.
  Don't hand-roll either or reintroduce a pass-through wrapper. The only difference is the `subtitle` -
  Project shows the resource's own mfid (monospace); Instrument shows the dataset's owning project ID
  (`subtitleMonospace = false`, since a project ID reads as a slug), the more useful disambiguator in
  a cross-project view. `InstrumentDetailScreen` takes a `graphExplorerUrl` (wired from `NavGraph.kt`)
  purely so its rows can offer Open in Web / Share.

---

## Search & pick patterns (`ui/common/SearchPicker.kt`)

Two shapes for "search-as-you-type and pick an entity". Pick the one matching the interaction; don't
invent a third without a second real use case.

- **Inline - `SearchPickerField<T>`.** Pick exactly one; selection is terminal. A text field with a
  floating `DropdownMenu` (`heightIn(max = 240.dp)`) that overlays without pushing layout around. For
  a single value inside a bigger form: `OwnerPickerField`/`InstrumentPickerField`, the project-lead
  search in `ManageProjectScreen`/`CreateProjectScreen`. **Never render results as a plain
  `Column.forEach` below the field** - that shifts every sibling as results change.
- **Full - `SearchPickerSheet<T>`.** Repeated selection, act-per-row. A `ModalBottomSheet` with the
  search field pinned above a bounded `LazyColumn` (`heightIn(max = 420.dp)`), so the field can't
  scroll away. Use when picking *is* the sheet's purpose and each pick triggers an action
  (`AddMemberSheet` stays open for several adds).
- Both share `rememberDebouncedSearchResults<T>()` (debounce + min-length gating, constants in
  `data/util/SearchPickerConstants.kt`). ViewModel-owned searches that must fire from an event handler
  rather than recomposition - `ManageProjectViewModel`'s lead/member search - keep their own
  cancellable-`Job` pattern but reference the same constants.
- **Not unified:** `LinkResourceSheet.kt` mixes search with other contextual controls (selected-item
  summary, QR scan, direction picker). It's a workflow that *contains* a picker; it stays bespoke
  until a second case justifies generalizing.

### Resolve-to-field (`SearchPickerField`'s `resolution` param)

All four callers resolve free-typed text to a real record, modelled on Gmail's recipient resolution,
so a confirmed match reads as confirmed rather than as text that only fails at submit time.

- **`ResolutionState<T>`** (`Idle`/`Resolving`/`Resolved`/`NotFound`) is derived *purely* from the
  `(query, results, isSearching)` triple the field already receives (`resolveSearchMatch`, private to
  `SearchPicker.kt`). No caller carries its own "resolved" field.
- **Callers must keep a just-picked item in `results` as a singleton list, not clear it to
  `emptyList()`** - clearing re-derives the fresh selection as `NotFound` the instant it's picked (see
  `ManageProjectViewModel`/`CreateProjectViewModel`'s `selectLeadUser`). The two
  `rememberDebouncedSearchResults` callers hit this differently: selecting sets `query` to the item's
  exact value, retriggering a search that briefly flips the field back to editable. Both pin the
  picked item in a local `remember`ed var overriding the hook's `results`/`isSearching` while `query`
  still matches, released as soon as the user types again.
- **Opt in via `resolution: ResolvedPicker<T>?`** (`keyOf`, `resolvedLabel`, `resolvedLeading`,
  `onClear`). When resolved, the field becomes a **read-only `OutlinedTextField`** - same label, same
  transparent background and outline, with `resolvedLeading` as leading content, `resolvedLabel` as
  the value, and a clear "×" trailing icon. Not a coloured pill: a pill drops the label and reads as
  an error banner on any accent whose `secondaryContainer` leans orange. `NotFound` stays editable,
  tints via `isError`, and swaps the trailing icon to `AppIcons.SearchOff`. `User`-resolving callers
  reuse `UserChipLeading`/`userDisplayName()`.

---

## Avatar colors

`UserAvatar` (`ui/common/UserComponents.kt`) derives its background from the person's ORCID via a
private `orcidToColor()` - a deterministic hash into a constrained HSL range, narrowed so the fixed
white initials stay legible against every generated colour. Same person, same colour everywhere.
**Always pass `orcid = user.uniqueId`**; omitting it silently falls back to the static
`containerColor`/`contentColor` pair.

Prefer `UserIdentityRow` over a bare `UserAvatar` + hand-rolled name `Text` when the row is just
"avatar + one line of name" (member lists, join-request rows) - it wires `orcid` through and uses
`userDisplayName()`, so both conventions change in one place. Reach for `UserAvatar` directly only
when the layout doesn't fit (profile header, edit form, multi-line subtitle), and still pass `orcid`.

---

## Notification dots

`NotificationDot` (`ui/common/NotificationDot.kt`) wraps any "something needs your attention" count on
an icon - `BadgedBox`/`Badge()` at 12.dp, down from M3's 16.dp default, so the size changes in one
place. `count` of `null` or `<= 0` hides the badge entirely.

Not for `SearchScreen`'s active-filter count - that's a persistent state readout, always shown once
filters are active. `NotificationDot` is for a count that's normally absent.

---

## Confirmation dialogs

`ConfirmationDialog` (`ui/common/ConfirmationDialog.kt`) is the shape for a plain "confirm this one
action" dialog: icon, one-sentence title, one-sentence body, confirm/cancel. `DiscardChangesDialog`
wraps it for the recurring back/Home-with-unsaved-changes case.

- **`title` must end in "?"** - it answers a yes/no question. Use a bare `AlertDialog` when there's no
  question: a form dialog (Request deletion, Request to join), a picker (Choose Accent Color), or an
  informational dialog (How to use Crucible Lens, QR codes). None of those get a `?`.
- **The icon sits inline to the left of the title**, not stacked above it (`AlertDialog`'s own `icon`
  param default). `ConfirmationDialog` builds this into its `title` slot; bare `AlertDialog` calls
  that carry an icon build the same `Row { AppIcon(...); Text(...) }` by hand, so every dialog's icon
  reads the same regardless of which composable renders it.
- **`isDestructive = true`** tints the icon and confirm label `error` - Sign out, Remove member, Leave
  project, Delete thumbnail, Clear history, Discard changes. Both actions are `TextButton`s (M3's
  convention); don't use a filled error-container button.
- A dialog with async state (a submit that shows a spinner mid-flight, e.g. Unlink resource) needs a
  custom confirm button rather than a label, so it stays a bare `AlertDialog` - following the same
  title-question and icon-placement rules by hand.

---

## No comments rule

Default: write no comments. Add one only when the *why* is non-obvious - a hidden constraint, a
workaround for a specific bug, a subtle invariant. Never describe what the code does; well-named
identifiers do that.
