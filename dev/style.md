# UI Style Guide

## Contents

- [Compose experimental opt-ins](#compose-experimental-opt-ins)
- [Spacing & layout](#spacing--layout)
- [Rows & cards](#rows--cards)
- [Elevation](#elevation)
- [Typography](#typography)
- [AnimatedVisibility for lazy-list items](#animatedvisibility-for-lazy-list-items)
- [Tabs + grouping](#tabs--grouping-projectdetailscreen-pattern)
- [Collapsing top bar](#collapsing-top-bar-projectdetailscreen--instrumentdetailscreen-pattern)
- [Search & pick patterns](#search--pick-patterns-uicommonsearchpickerkt)
- [Avatar colors](#avatar-colors)
- [Notification dots](#notification-dots)
- [No comments rule](#no-comments-rule)

---

## Compose experimental opt-ins

Experimental Compose APIs need an explicit `@OptIn`. The build does **not** set
`allWarningsAsErrors` (the only compiler arg in `app/build.gradle.kts` is
`-Xexpect-actual-classes`), so a missing opt-in surfaces as a warning, not a failure —
but the expected build output is still `BUILD SUCCESSFUL` with no warnings, so treat it
as one.

| API | Annotation |
|-----|-----------|
| `HorizontalPager`, `rememberPagerState`, `stickyHeader`, `combinedClickable` | `@OptIn(ExperimentalFoundationApi::class)` |
| `TopAppBar`, `ModalBottomSheet`, `SearchBar`, pull-to-refresh, `TopAppBarScrollBehavior` | `@OptIn(ExperimentalMaterial3Api::class)` |
| `AnimatedContent` transition specs | `@OptIn(ExperimentalAnimationApi::class)` |

Annotate the narrowest scope that needs it, not a blanket three-API line:

- `@file:OptIn(ExperimentalMaterial3Api::class)` at the top of a screen file — by far the
  most common case, since nearly every screen uses `AppTopBar`/`ModalBottomSheet`/pull-to-refresh
  in several composables in the same file.
- `@OptIn(...)` on the individual composable for the rarer APIs, so the scope stays visible
  where it's actually used — e.g. `@OptIn(ExperimentalFoundationApi::class)` on
  `InstrumentDetailScreen`, `@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)`
  on `ProjectDetailScreen`.

`ExperimentalFoundationApi` is the widest-spread of the three (~14 files);
`ExperimentalAnimationApi` is needed in only two.

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

## Rows & cards

**List rows are not `Card`s.** List row entries use `ResourceRow` (`ui/common/ResourceListComponents.kt`) — the pairing of a `ResourceCard` plus its trailing divider. This composable exists to prevent repeating the same divider code and inset value eight times. `ResourceCard` itself is a full-bleed `Box { Row { … } }` — no container, no elevation, `combinedClickable` + `padding(horizontal = 16.dp, vertical = 8.dp)`. Project/instrument/search/history rows use M3's `ListItem` for the same reason (consistent row height and leading/trailing slots). Don't wrap a list row in an inset elevated `Card`; rows are flat and full-width, separated by whitespace and `SectionHeader` dividers rather than by card edges.

`ListRowDividerInset` (72.dp) is the left inset for the divider under an icon-leading list row, aligning it with the row's text rather than its leading icon. It is shared by `ResourceRow` and the hand-rolled rows in Search and History, which use different row composables but must line up with the same grid.

`EmptyListCard` is the shared empty-state composable, shown when a list has no items (either because the resource type is absent on that project/instrument, or because a search/filter returned nothing). It takes a caller-supplied `emptyMessage` because the reason a list is empty is screen-specific ("this project has none", "this instrument has none"), while the filtered-to-nothing case reads the same everywhere.

`ResourceControlsBar` is the shared search + group-by + sort bar used by `ProjectDetailScreen` and `InstrumentDetailScreen`. The caller supplies the group options because each screen groups by a different enum (and Project's set depends on which tab is showing), but the layout, tinted search surface, and sort menu are identical. The `containerColor` parameter exists because Project renders this inside an already-`surface`-painted column, while Instrument renders it as a `stickyHeader` that must be opaque against `background` — this parameter lets both screens render correctly without duplication.

`Card` is reserved for things that genuinely are contained blocks:

| Use | Style |
|-----|-------|
| Info / section card (empty states, metadata blocks, error banners) | `containerColor = surfaceContainerLow`; no elevation |
| Thumbnails block (`detail/components/ThumbnailsSection.kt`) | `cardElevation(2.dp)` |
| Home screen's primary scan card (`HomeScreen.kt`) | `cardElevation(6.dp)` — deliberately the one prominent, "this is the main action" surface |
| Section / group header (`SectionHeader`), expanded | `Surface(color = surfaceContainerHigh)`, animated — see "Accent-derived surfaces" below |
| Section / group header (`SectionHeader`), collapsed | `Surface(color = surface)` + a leading `outlineVariant` divider — a collapsed header isn't pinning above anything, so it reads as a plain row instead of chrome |
| Tinted accent surface (stat tiles, the count badge inside `SectionHeader`) | `Surface(color = secondaryContainer)`, content `onSecondaryContainer` — no alpha; see "No custom alpha" below |

Those two elevations are the *only* `cardElevation` calls in the app — a new elevated card
needs a reason, not a default.

### Accent-derived surfaces

M3 expresses elevation as **tonal colour**, not shadow: chrome that sits above content uses a
`surfaceContainer*` role rather than a shadow or a `tonalElevation` parameter. Reach for
`surfaceContainer` for anything pinned — sticky section headers especially.

That only works because `Theme.kt`'s `resolveAccentColorScheme()` looks up a fully hand-curated
static `ColorScheme` per accent (`ui/theme/accents/` — one file per named accent, exported from the
Material Theme Builder) — every container role explicitly assigned, not just
`primary`/`secondary`/`tertiary`. Before this, hand-written palettes only set those three roles, so
every container role fell through to M3's baseline — which is generated from a *purple* seed, and
made a blue-accented app render purple-grey headers.

Consequence worth knowing: every surface/container role shifts with the user's accent choice *and*
their chosen contrast level (Standard/Medium/High, matching M3's real contrast-level spec — see each
accent file's `LightStandard`/`LightMedium`/`LightHigh`/`Dark*` vals). There's no runtime color
generation anywhere in this app — adding an eleventh accent means exporting a new Theme Builder
bundle and adding one more file to `ui/theme/accents/`, not writing a formula. Don't hardcode a grey
where you want "slightly raised" — use the role and it follows the theme.

**Container roles are sized for compact chrome; a full-screen surface should use plain `surface`**,
which is why `SearchScreen` overrides `SearchBarDefaults.colors(containerColor = surface)` rather
than accepting the M3 default.

### No custom alpha

M3 already pre-computes every emphasis level as a distinct, contrast-guaranteed role. Fading a role
with `.copy(alpha = X)` is almost always a workaround for a role that already exists — and it quietly
reopens the exact contrast risk that role's generated value exists to close (that's why
`onSurfaceVariant` is its own computed colour rather than "`onSurface` at 60%" — dimming it further
undoes the guarantee). A color-role audit found ~50 ad hoc alpha values across the app (several
different numbers doing the same job, e.g. `primary.copy(alpha = 0.12f–0.15f)` for badge
backgrounds, `onSurfaceVariant.copy(alpha = 0.4f–0.7f)` for secondary text). None of them earned
their alpha:

| Need | Reach for | Not |
|---|---|---|
| De-emphasized text/icon on a plain surface | `onSurfaceVariant`, full opacity | `onSurface`/`onSurfaceVariant.copy(alpha = X)` |
| De-emphasized text/icon on a container (`errorContainer`, `primaryContainer`, ...) | the matching `onXContainer`, full opacity | `onXContainer.copy(alpha = X)` — hierarchy within a container comes from type scale (size/weight), not a second opacity signal on top of the first |
| Tinted "selected/accent" chip or badge background | `primaryContainer` / `onPrimaryContainer` | `primary.copy(alpha = 0.12–0.15f)` |
| Decorative divider | `outlineVariant`, full opacity | `outlineVariant.copy(alpha = X)` |
| Text field border | `outline` (M3's own stock default already) | `outline.copy(alpha = X)` |

**Disabled content is the one legitimate exception**, and it's still not an arbitrary number:
`AppContentAlpha.Disabled` (`ui/common/AppContentAlpha.kt`) is `0.38f`, the exact value M3's own
stock components use for disabled icon/label content (confirmed against `FilledButtonTokens.kt` in
the resolved M3 1.4.0 sources — `DisabledIconOpacity`/`DisabledLabelTextOpacity` are both `0.38f`,
paired with `onSurfaceVariant`). Disabled is a *state* any element can enter, not a fixed color
family — WCAG 1.4.3 explicitly exempts inactive components from the normal contrast requirement, so
Material implements it everywhere as a fixed opacity reduction rather than a role. Use
`onSurfaceVariant.copy(alpha = AppContentAlpha.Disabled)` for a genuinely disabled element; never a
one-off number.

`LazyColumnScrollbar`'s thumb used to fade `primary` to 60% for exactly this reason — a scrollbar
thumb only needs to avoid fully hiding scrolled content, which sounds like a legitimate functional
case for alpha. It's solid `primary` now: a scrollbar thumb is narrow enough, and moves fast enough,
that full opacity doesn't meaningfully block reading the content underneath it in practice. If a
genuine "must show through" need ever comes up, it doesn't map to any M3 role — treat it as a
one-off, not a precedent for reintroducing alpha elsewhere.

### Collapsible section headers

`SectionHeader`'s `onToggle` is nullable. Pass a lambda when the section collapses; when it's null
the chevron isn't drawn and the row isn't clickable. A chevron that renders as interactive and does
nothing is worse than no chevron — that exact bug shipped once, in Search.

All current callers are collapsible: resource groups on both detail screens, the "Hidden" sections
on both list screens, and Search's Projects/Samples/Datasets sections. Search's default to expanded
and keep their state in `rememberSaveable`, so collapsing one survives opening a result and
navigating back.

---

## Elevation

`ui/common/AppElevation.kt` declares the 6 canonical M3 elevation levels (`Level0`–`Level5` =
0/1/3/6/8/12dp) — pass these to `tonalElevation`, `shadowElevation`,
`CardDefaults.cardElevation()`, and `FloatingActionButtonDefaults.elevation()` instead of an
inline `Xdp`. An audit found several one-off values (2dp on a thumbnail card, 6dp on a home-screen
banner, 8dp on two different hand-rolled bottom bars, 4dp/8dp on a mini FAB) that were each either
a level that doesn't exist in the M3 scale, a canonical level borrowed from the wrong component's
tier, or `Level4`/`Level5` used as a resting value when M3 reserves those two for
hover/focus/dragged states only. All were normalized to `AppElevation` per the resting-level
mapping in that file's KDoc (sourced from `m3.material.io/styles/elevation`).

A follow-up pass on the resource detail screen found two more one-offs, since fixed:
`ThumbnailsSection`'s card had an elevation override at all - `BasicInfoCard`, `SampleDetailsCard`/
`DatasetDetailsCard`, `LinkedResourceCards`, and `AssociatedFilesCard` on that same screen all use a
bare `Card { }` with no override, which resolves to M3's own filled-card default (`Level0`, 0dp,
flat) - so the thumbnail card was the only one on the screen with any shadow, for no documented
reason. Dropped the override rather than keeping it, since nothing about thumbnail media on this
screen meets M3's stated bar for shadow use (protection from a busy background, or signalling
interactivity) that its flat siblings don't also meet. If a future thumbnail redesign wants
elevation back, document why, the way `QrCodeDialog`'s `tonalElevation = 0` carve-out does for the
opposite case.

`LinkedResourceCards.kt`'s `ResourceRow` originally used a raw `MaterialTheme.colorScheme.onSurface.copy(alpha
= 0.06f)` background instead of a real container role - a plain alpha-blend of a *foreground* color
role doesn't track the user's accent colour the way a real surface role does, so it read as flat grey
in every palette while everything else picked up the accent. It's now `surface` — the row nests
inside `LinkedResourceCard`'s outer `Card` (M3's default `surfaceContainerHighest`), so it needs to
*recede* relative to its parent, not share a tier with it.

A later color-role audit retired the legacy `surfaceVariant` role app-wide (it isn't one of M3's
current canonical roles) in favor of `surfaceContainerLow` for the "flat tinted row / info card"
convention used by roughly ten call sites (`AddFilesScreen`, `MetadataEditor`, `SearchBar`,
`ResourceListComponents`, `InstrumentListScreen`, `ProjectsListScreen`, `UserComponents`,
`SampleDetailsCard`, `DatasetDetailsCard`) rather than introducing a second, accent-derived treatment
for the same kind of element. `ResourceRow`'s `surface` choice is deliberately different from this
convention, precisely because it's nested rather than standalone.

**M3 elevation and container colour are two separate decisions, not one.** Per M3's current
guidance, *"surface tint colour is deprecated, use elevation level tokens instead"* and *"surface
roles are not tied to elevation"* — the modern pattern is: pick a dp level for shadow/z-depth, and
pick a `surface`/`surfaceContainer*` role for colour, independently. This matters because Compose's
automatic tonal-elevation blend (`Surface`'s `tonalElevation` parameter) **only applies when
`color` is left at the default `colorScheme.surface`** — setting any other explicit `color` (e.g.
`surfaceVariant`) makes `tonalElevation` a silent no-op. `HomeScreen.kt`'s search pill had exactly
this bug (`color = surfaceVariant` with a `tonalElevation = 2.dp` that did nothing); the fix was to
drop the dead parameter, not raise the value.

**The collapsing top bar's expanded/collapsed tint** (see below) is a deliberate exception to the
letter of M3's own app-bar table, which assigns the *scrolled* state elevation (`Level2`) and the
*resting* state none (`Level0`) — the opposite of what this app does. That's because
`CollapsingAppTopBar`'s content is padded below the bar rather than scrolling underneath it, so
there's no "something is now behind me" moment for elevation to signal; the tint there is a
containment choice (marking the hero block as its own panel), not an elevation one. Don't treat
that inversion as a precedent for other app bars — it applies to this one component for this
specific structural reason.

---

## Typography

| Treatment | Role | Size / weight | Colour |
|---|---|---|---|
| Expanded collapsing-bar hero | `emphasizedTitleLarge` | 22 Medium | `onSurface` |
| **Surface title** - every top bar (static + collapsed) and every sheet title | `titleLarge` | 22 Regular | `onSurface` |
| Card / dialog heading | `emphasizedTitleMedium` | 16 Bold | `onSurface` |
| **Group header** - in-list, has a container | `titleMedium` | 16 Medium | `onSurface` on `surfaceContainer` |
| Text input | `bodyLarge` | 16 Regular | M3's own text-field default; never set it |
| **Row title / body copy / paragraphs** | `bodyMedium` | 14 Regular | `onSurface` |
| Button / chip label | `labelLarge` | 14 Medium | M3's own button default; never set it |
| Secondary line / caption / metadata | `bodySmall` | 12 Regular | `onSurfaceVariant` |
| Machine ID (mfid, project/instrument ID) | `bodySmall` monospace, via `IdText` (`ui/common/IdText.kt`) | 12 Regular | `onSurfaceVariant` |
| Inline section label | `labelMedium` | 12 Medium | `primary` (or `onSurfaceVariant` when nested in a card that already carries an accent) |
| Detail row label (`InfoRow`/`ClickableInfoRow`) | `titleSmall` | 14 Medium | `onSurfaceVariant` |

Count badges use the inline-label treatment (`labelMedium` + `fontFeatureSettings = "tnum"`) on a
`primaryContainer` surface with `onPrimaryContainer` content - no alpha; see "No custom alpha"
below.

**`IdText`** (`ui/common/IdText.kt`) is the shared look for any mfid/project/instrument ID -
`bodySmall`, monospace by default (`monospace = false` opts out), `onSurfaceVariant` since an ID
is the least essential thing on a name-first row or card. It's purely visual - callers attach
click/copy behaviour themselves via `modifier` (some IDs are tap-to-copy, some sit next to a
separate copy button, some are inert and reachable only via a row's long-press menu). Before this
existed, `ResourceCard`, `SampleDetailsCard`/`DatasetDetailsCard`, and `ProjectDetailScreen`'s
header each hand-rolled the same `bodySmall` + `Monospace` + `onSurfaceVariant` combination with a
drifting alpha (some dimmed, some not) - use `IdText` for any new one instead of another one-off.
**Don't reach for it on a tinted container** (e.g. a banner on `primaryContainer`) - its color is
hardcoded to `onSurfaceVariant`, which assumes a plain `surface` background; `SearchScreen`'s
"Open resource directly" suggestion banner is a real example that correctly stays a hand-written
`Text` for exactly this reason.

**Size cap:** nothing above 22 sp. Only two sizes carry all the chrome - 22 for surface titles, 16 for both header kinds - and weight (Regular / Medium / Bold) does the rest. This keeps the palette small and manageable.

**Retired roles:** all three `display*`, all three `headline*`, `labelSmall`, and all but two of the emphasized variants. Reaching for any of these means the element's job has not been decided yet - not that the system is missing a role.

**Governing principle:** pick the role that means the thing; carry emphasis with colour and container, not weight. Weight is the weakest of the three channels. The detail-row label is the one place emphasis still comes from weight, but it no longer needs a synthetic `emphasizedX` override to get there: `titleSmall` (14 Medium) sits one step up from the value's `bodyMedium` (14 Regular) - same size, so the two align in a `label: value` row, with weight plus the muted `onSurfaceVariant` colour doing the differentiation. This is why `titleSmall` is sanctioned here despite being retired everywhere else - it isn't reused as a generic "medium-emphasis" role, only for this specific row shape.

**Stock ramp only.** The type scale is stock M3 1.4.0 with zero deviations. Every `Text` uses a plain `MaterialTheme.typography.X`. No extension properties on `Typography`, no `fontSize =` outside `Type.kt`, and no `.copy()` that changes a role's size or weight. If a size feels wrong, change the element's role, not the ramp. One-off size tweaks are how a scale stops being a scale.

**Never set a style on button label text, and never set `textStyle` on a text field.** M3 supplies `labelLarge` to buttons and `bodyLarge` to text fields; both are the sanctioned defaults. The one carve-out is `BasicTextField`, which takes no `color` parameter - `.copy(color = …)` is unavoidable there (`SearchBar.kt`, `ResourceListComponents.kt`). Similarly, `MetadataEditor`'s dense key-value fields stay at `bodySmall` (12 sp) rather than bumping to 16 - they sit next to the monospace exception and reflow badly at a larger size.

**Density decision:** list row titles are 14 sp, deliberately below M3's `ListItem` spec (16 sp). Information density matters in a data-heavy scientific app. Subtitles stay 12 sp so they distinguish from row titles.

**Reference and debug:** Settings > Typography (visible in debug builds via `isDebugBuild`) renders the full ramp plus in-context mocks at `TypographySettingsScreen.kt`. It is the fastest way to verify a proposed change.

Verify with these greps:

```bash
grep -rn "fontWeight = FontWeight" --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/ | grep -v theme/Type.kt
```
Should return zero.

```bash
grep -rnE "fontSize = " --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/ | grep -v theme/Type.kt
```
Should return exactly one - the 13 sp monospace field in `MetadataEditorScreen.kt`.

```bash
grep -rnE 'typography\.(headline|display)|typography\.labelSmall' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v theme/Type.kt | grep -v TypographySettingsScreen
```
Should return zero.

```bash
grep -rn 'typography\.titleSmall' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v theme/Type.kt | grep -v TypographySettingsScreen
```
Should return only the two hits in `InfoRows.kt` (`InfoRow`/`ClickableInfoRow`'s label).

```bash
grep -rhoE 'typography\.emphasized[A-Za-z]+' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v TypographySettings | sort -u
```
Should return exactly `typography.emphasizedTitleLarge` and `typography.emphasizedTitleMedium`.

```bash
grep -rn "textStyle = MaterialTheme" app/src/commonMain/kotlin/crucible/lens/ui --include=*.kt
```
Should return only the two `BasicTextField` carve-outs (in `SearchBar.kt`, `ResourceListComponents.kt`) and the two `MetadataEditor.kt` dense-field exceptions.

```bash
grep -rn 'import crucible.lens.ui.theme.\(body\|label\|title\|headline\|display\)' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui
```
Should return zero. Only the `emphasizedX` styles are extensions on `Typography`; the 15 stock roles
are real members and must never be imported from `crucible.lens.ui.theme`. A find-and-replace that
rewrites an `emphasizedX` call site without also dropping its import produces exactly this, and it
fails to compile in a way that points at the wrong file.

### Emphasis: use the emphasized styles, never `fontWeight`

**`fontWeight` must not appear anywhere in `ui/` outside `Type.kt`.** This is a hard invariant, and it currently holds at zero occurrences:

```bash
grep -rn "fontWeight = FontWeight" --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/ | grep -v theme/Type.kt
```

M3's scale has **30** styles, not 15: the baseline set plus an *emphasized* set added in the May 2025 Expressive update. An emphasized style is its baseline at a heavier weight - identical size, line height and tracking - and the guidance is to swap the baseline token for the emphasized one rather than override a weight. Weights are Regular, Medium and Bold; **SemiBold exists nowhere in M3**, so if you're reaching for it you want one of these instead.

Compose 1.4.0 ships the emphasized tokens but every accessor is `internal` - the public surface was removed from the stable branch (1.4.0-beta01: *"All public APIs tagged with `ExperimentalMaterial3ExpressiveApi` have been removed, please switch to 1.5.0-alpha"*). So `Type.kt` derives all 15 locally as `MaterialTheme.typography.emphasizedTitleMedium` and friends.

**The name matters.** They are deliberately *not* called `titleMediumEmphasized`: that collides with Compose's internal member, and Kotlin resolves members ahead of extensions, so the call site binds to the inaccessible internal one and fails with `INVISIBLE_MEMBER`. Silencing that with `@file:Suppress` compiles, but only by defeating the visibility system and binding the app to internal Compose API - don't. The `emphasizedX` prefix sidesteps the collision entirely.

| Need emphasis on… | Use |
|---|---|
| `titleMedium` (card / dialog heading) | `emphasizedTitleMedium` (Bold) |
| `titleLarge` (expanded collapsing-bar hero) | `emphasizedTitleLarge` (Medium) |

`Type.kt` declares all 15 emphasized variants so the block mirrors M3's real scale, but only these
two are sanctioned. The other 13 are retired: emphasising a `body*` or `label*` role means the
element wants a different role, not a heavier one. The detail-row label (`InfoRow`/
`ClickableInfoRow`) used to be one of these (`emphasizedLabelMedium`) but now uses stock `titleSmall`
instead - see the Typography table above - since it sits at the same 14sp size as the value it
labels rather than needing a synthetic Bold override at a smaller size.

When CMP ships a stable M3 exposing the official accessors, delete the block in `Type.kt` and rename `emphasizedTitleMedium` / `emphasizedTitleLarge` -> `titleMediumEmphasized` / `titleLargeEmphasized` throughout.

**Colour, not weight, for accent.** M3 puts body text on `onSurface`, with `onSurfaceVariant` as the muted alternative, and reserves `primary` for hyperlinks. Section headers get their accent from the container plus a tinted icon, not from primary-coloured body text.

**Tabular figures** (`fontFeatureSettings = "tnum"`) on any number that changes in place - counts, timers - so digits don't shift width. `SectionHeader`'s count badge does this.

**A line that must never wrap or clip: `autoSize`, on one `Text`, not several.** M3 1.4.0's `Text` (via Foundation 1.10.3's `BasicText`) takes a stable `autoSize: TextAutoSize?` parameter - `TextAutoSize.StepBased(minFontSize, maxFontSize)` shrinks that `Text`'s font to the largest size that still fits its constraints at `maxLines`, falling back to `overflow` (use `TextOverflow.Ellipsis`) only if it still doesn't fit at `minFontSize`. Cap `maxFontSize` at the role's own size (e.g. `MaterialTheme.typography.bodySmall.fontSize`) so it only ever shrinks, never grows past the intended design. `HomeScreen`'s footer (`Crucible Lens vX.Y.Z • by Crucible Team • Molecular Foundry`) uses this: `maxLines = 1` + `autoSize` guarantees it never wraps to a second line or gets clipped past the screen edge on a narrow phone or a longer version string.

**This only works as one `Text`.** `autoSize` measures and shrinks a single `Text`/`BasicText` call's content as a unit; three separate `Text`s in a `Row` (as the footer used to be, split by color/clickability) each measure and autosize independently against the same available width, landing on different font sizes with no guarantee they line up. Where a line needs multiple colours or a clickable segment, build one `AnnotatedString` (`buildAnnotatedString` + `withStyle(SpanStyle(color = …))`) and attach the click behaviour as a `LinkAnnotation.Clickable` span (`withLink`) inside that same string, rather than reaching for a separate `Modifier.clickable` `Text`.

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

## Tabs + grouping (ProjectDetailScreen pattern)

Two separate mechanisms, often confused — they don't nest:

1. **Tabs = resource kind, not group.** A fixed two-tab `PrimaryTabRow` (Samples / Datasets) drives
   a single `HorizontalPager(pageCount = { 2 })`. There is no `ScrollableTabRow` and no per-group
   tab anywhere in the app; the tab count never varies with the data.
2. **Groups = sticky sections inside one `LazyColumn`.** `groupedResourceItems` (a `LazyListScope`
   extension in `ProjectResourceLists.kt`) emits a `stickyHeader` per group (via the shared `SectionHeader` — tapping expands/collapses, state in a `rememberSaveable` `SnapshotStateMap` keyed by `groupBy`), then that group's `ResourceRow`s in full — no row cap, no "load more" item (see the pagination note below).
   Ungrouped (`GroupBy.NONE`) skips the headers and emits a flat `items(...)`.

Each pager page (`SamplesList`/`DatasetsList`, both in `ProjectResourceLists.kt`) owns its own `rememberLazyListState`,
`LazyColumnScrollbar`, and `ScrollToTopButton` — nothing is passed in from outside, so the two
tabs scroll independently.

---

## Collapsing top bar (ProjectDetailScreen / InstrumentDetailScreen pattern)

The project/instrument name lives in the **top bar itself** and collapses natively as the list
scrolls. `CollapsingAppTopBar` (`ui/common/AppTopBar.kt`) is a sibling of the plain `AppTopBar`
(used by the ~20 other static-title screens, untouched) — it's a **custom, single-render-pass**
composable, not `MediumTopAppBar`/`TwoRowsTopAppBar`, and that choice was deliberate, arrived at
after several other approaches:

- **Why not `MediumTopAppBar`.** Its public API exposes one `title: @Composable () -> Unit` slot,
  but Material3 renders that *same* composable **twice** internally — once sized for the large
  expanded row, once for the compact collapsed row — crossfading opacity between the two based on
  `collapsedFraction`. Both instances necessarily share whatever content decision lives inside that
  lambda. Every attempt to show *different* content per row (an identity block only while expanded,
  a name that wraps to more lines while expanded) hit the same failure mode: early in a scroll, the
  compact row's fade-in alpha is already non-zero before the content has caught up to "collapsed,"
  so the compact row (a fixed ~64dp strip) transiently renders the oversized expanded content —
  visible as it flashing at the top of the screen before the swap catches up. The real fix for
  "different content per row" — `TwoRowsTopAppBar`'s `title: @Composable (expanded: Boolean) -> Unit`,
  which tells each row instance which one it is — exists in this M3 version but compiles `internal`
  in this project's CMP artifact, so it isn't usable.
- **What `CollapsingAppTopBar` actually is**: one ordinary `Surface` + `Column`, composed exactly
  once, reading `scrollBehavior.state.collapsedFraction` directly to decide what to show — the same
  public `TopAppBarState`/`TopAppBarScrollBehavior` a standard component would use, just without
  Material3's row-duplicating rendering on top of it. Because there's no second hidden instance to
  desync from, content decisions here are unconditionally safe, *including ones that change
  height* — no thresholds to tune, no residual risk, unlike every attempt built on top of
  `MediumTopAppBar`. Structure: a fixed-height top row (nav icon + `actions`, always present,
  matching Material3's own Medium/Large bar convention) holding the collapsed single-line `name`;
  below that, the expanded block (icon badge + `name`, up to 3 lines, + `expandedContent` —
  lead/org/member-count, or type/location). **There is deliberately no `AnimatedVisibility` and no
  animation spec anywhere in this component.** An earlier version flipped content at a
  `collapsedFraction > 0.5f` threshold and let `AnimatedVisibility` run its own spring, which (a)
  made the bar ignore the finger until it suddenly jumped at the halfway point, (b) ran a second
  animation on top of the snap `exitUntilCollapsedScrollBehavior` already performs on release, and
  (c) fed the *animating* height back into `heightOffsetLimit`, so the limit moved mid-animation and
  could re-cross its own threshold. Every scroll-varying value (bar height, both title alphas,
  container colour) is instead a pure function of `collapsedFraction`, read inside a
  `layout`/`graphicsLayer`/`drawBehind` lambda so it resolves in the layout/draw phase and never
  recomposes while scrolling — an actual continuous crossfade driven only by the finger, not two
  independently-timed transitions. `TopAppBarDefaults.MediumAppBarCollapsedHeight`/`.windowInsets`
  are still reused from Material3 for the fixed row height and status-bar insets — only the
  row-duplicating title mechanism and `AnimatedVisibility` were the problem, not the rest of the M3
  toolkit.
- **Must manually set `scrollBehavior.state.heightOffsetLimit`** — this is the one piece of
  bookkeeping `MediumTopAppBar`/`TwoRowsTopAppBar` normally do for you during their own measure
  pass, and skipping it silently breaks scrolling entirely (not just the header — the whole list
  underneath stops responding to scroll). `TopAppBarState.heightOffsetLimit` defaults to
  `-Float.MAX_VALUE` (confirmed by reading M3 1.4.0's `AppBar.kt`), and
  `exitUntilCollapsedScrollBehavior()`'s `nestedScrollConnection.onPreScroll` consumes the *entire*
  upward scroll delta itself for as long as `heightOffset` hasn't hit that limit — with an
  unbounded limit, that's forever, so the list never sees a scroll event. `CollapsingAppTopBar`
  fixes this by measuring the expanded block's real height in a `Modifier.layout` on its content,
  comparing against a plain (non-snapshot) `remember`ed box holding the last published height so it
  never reads back the same snapshot state it just wrote, and publishing straight into
  `heightOffsetLimit` from that layout pass — **not** from a `SideEffect`, which would only run once
  at first composition, before the block has been measured, leaving the limit at `-Float.MAX_VALUE`
  forever. Any future rewrite of this composable that stops calling a real M3 app bar composable
  needs to keep doing this.
- **Container colour lerps `expandedContainerColor` (expanded) -> `surface` (collapsed)**, read via
  `drawBehind` on the wrapping `Surface` (itself `color = Color.Transparent`) so the blend is a
  draw-phase read of `collapsedFraction`, not a composable-time one - consistent with every other
  scroll-driven value in this composable. `expandedContainerColor`/`expandedContentColor` default to
  `secondaryContainer`/`onSecondaryContainer`, and neither `ProjectDetailScreen` nor
  `InstrumentDetailScreen` overrides them - both detail screens share one expanded-state identity
  rather than each inventing its own tier, matching the earlier fix that made every `SectionHeader`
  identical across screens. Every `expandedContent` colour inside both screens' blocks (lead/org/
  member-count/ID for a project, type/location for an instrument) is `onSecondaryContainer`
  throughout rather than mixing in `onSurfaceVariant`, which only pairs safely with the `surface`
  family - hierarchy between a project's lead (`bodyMedium`) and everything else (`bodySmall`) is
  carried by type scale alone, not colour, matching `SectionHeader`'s convention. Fully collapsed
  always matches `surface` exactly (the same tone as the page background), which is what the earlier
  "collapsed bar looks like a different colour than the page" bug required. The two params remain
  overridable together for a future screen that needs a different expanded identity - changing only
  the container would leave `expandedContent`'s colours paired with a container that never
  guaranteed contrast against them.
- **The pin toggle moved into the top bar's `actions`** (before home/overflow — the search icon was
  dropped from this row entirely, since the inline filter field in `ResourceControlsBar` already
  covers in-screen search and the top bar was crowded), so it's
  always reachable regardless of scroll position — it used to live inside the identity block and
  vanish along with it once collapsed. This is a new placement (list-screen rows still show pin
  inline per-card; this is the first *detail*-screen top bar to host one) — not copied from an
  existing convention, so don't assume every top bar needs one.
- **Nested scroll ordering with `PullToRefreshBox`**: `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
  must be attached to a child *inside* `PullToRefreshBox`'s content (e.g. the `Column`/`Box`
  wrapping the list), never to `PullToRefreshBox`'s own `modifier` parameter or further out on
  `AppScaffold`. Nested scroll dispatches to the nearest ancestor connection first; attaching ours
  further out than `PullToRefreshBox`'s own connection meant a downward drag at the top of the
  list (intended to re-expand the collapsed bar) was consumed entirely by the pull-to-refresh
  gesture before our connection ever saw it — the bar would stay collapsed and pull-to-refresh
  would fire instead. This is a known class of bug in `PullToRefreshBox` + collapsing-app-bar
  combinations, not something obviously wrong-looking in the code, so don't "simplify" this
  placement back to the `AppScaffold`/`PullToRefreshBox`-level modifier without re-testing scroll
  behavior at the top of the list.
- **No separate meta row anymore** — `ProjectIdentityHeader`/`InstrumentIdentityHeader` and their
  later `ProjectMetaRow`/`InstrumentMetaRow` replacements are gone entirely. All of a project's
  identity metadata (name, lead, org, member count) and an instrument's (name, type, location) now
  lives in `CollapsingAppTopBar`'s `expandedContent`, so there's nothing left to render as a
  separate fixed block or scrolling list item above the resource list.
- **Group headers still pin correctly** via native `stickyHeader` stacking — unaffected by this
  change, since it was already solved by moving identity out of any single `LazyColumn`'s
  coordinate space (see the "no z-order conflict" reasoning that motivated the tab-swipe fix this
  section used to describe in more detail).
- **`LazyColumn`'s `content: LazyListScope.() -> Unit` is not itself a composable slot** — only
  the trailing lambdas passed to `item {}`/`stickyHeader {}`/`items {}` are. This is why
  `groupedResourceItems` (the shared grouping/sticky-header rendering logic used by both
  `SamplesList` and `DatasetsList`) is a plain function, not `@Composable` — any `@Composable`
  state it needs (`rememberOwnerNames`, the `rememberSaveable` expand-state map, the grouping and
  sorting) is resolved by the calling `@Composable` function (`SamplesList`/`DatasetsList`) and
  passed in as plain, already-sorted values; the shared function only calls `item`/`stickyHeader`
  itself, with the actual `@Composable` calls (`ResourceCard`, etc.) living inside those trailing
  lambdas. There's no manual pagination/"Load more" step anymore — every group renders in full
  (the data's already entirely in memory; `LazyColumn` only composes what's near the viewport
  regardless of how many items are registered) — but the constraint on where `@Composable` state
  can live is unchanged, which is why the grouping (`remember(samples, groupBy, ownerNames)`) and
  the per-group sort (`remember(groupedByKey, sortState)`, kept separate so a sort-only change
  doesn't redo the more expensive regrouping) both still live in `SamplesList`/`DatasetsList`, not
  in `groupedResourceItems` itself.
- The top bar's `name` is gated on the entity itself resolving (`project?.title ?: projectId` /
  `instrument?.instrumentName ?: instrumentId`), **not** on the resource-list load state — the
  project/instrument fetch is usually already warm (from the list screen) and independent of the
  slower samples/datasets fetch, so gating on the wrong one delays the title for no reason.
- **Project member count** comes from `CrucibleRepository.observeProjectMembers(projectId)`/
  `fetchProjectMembers(projectId)` — a cache fetched alongside the project itself
  (`ProjectDetailScreen`'s load effect), not a one-off call from the header. `rememberOwnerNames`
  (used for OWNER-groupby resolution) reads from the *same* cache instead of its own separate
  `getProjectUsers` call, so opening a project only ever fetches its member list once regardless of
  whether the header, the owner-groupby feature, or both end up needing it.
- The project lead's name uses `userDisplayName()` (`CLAUDE.md`'s "User identity conventions")
  like everywhere else a person is shown in context — full name, no username by default; tappable
  in the header (opens the profile), same as it was in the old meta row.
- Small inline icons in `expandedContent` (lead/org/member-count/type/location) are `14.dp`,
  matching this file's documented 14–18dp floor.
- **Expanded layout is left-aligned with an icon badge as the anchor**, not centered — matching
  M3's own `LargeTopAppBar`, which start-aligns its expanded title rather than centering it. The
  `icon` renders as a circular `primary`/`onPrimary` badge (`HeroIconBadgeSize` = 44dp, same
  container/content pairing as `ManageProjectScreen`'s "Add member" badge) beside the title, and
  `expandedContent` is indented by the badge's footprint (`HeroIconBadgeSize + HeroIconBadgeSpacing`)
  so its left edge lines up with the title's rather than the two reading as separately-aligned
  blocks. This replaced an earlier centered, plain-icon layout that read as four lines of
  same-weight centered text with no focal point. `ProjectDetailScreen` uses `AppIcons.Project` as a
  static badge today (a future per-project custom icon would slot into the same badge unchanged);
  `InstrumentDetailScreen` uses `AppIcons.Instrument`.
- **`ProjectDetailScreen`'s byline consolidates lead + organization onto one line** ("Tim Kodalle ·
  LBNL") rather than each getting its own row — the lead is the only tappable part, so it alone
  stays `primary`; organization shares the block's plain `onSecondaryContainer`. Member count (and
  sync status) demotes to a second, smaller line below — hierarchy between the two is carried by
  type scale (`bodyMedium` vs `bodySmall`), not by introducing another colour.
- Row titles and group-header titles are both 16sp and neither overrides `fontWeight` — see
  [Typography](#typography).
- **Group headers**: both screens call the shared `SectionHeader` (`ui/common/SectionHeader.kt`)
  directly. The per-screen `GroupStickyHeader` wrappers that used to sit in front of it were
  pass-throughs and have been deleted. Don't hand-roll a group header inline, and don't
  reintroduce a wrapper — any screen with expandable, counted groups delegates to `SectionHeader`.
- **Resource rows**: both screens' list rows use the shared `ResourceRow`
  (`ui/common/ResourceListComponents.kt`), which pairs `ResourceCard` with its trailing divider —
  icon + title (`bodyLarge`) + one subtitle
  line (`labelSmall`, `onSurfaceVariant`) + a `NavigateNext` chevron, long-press for a context menu
  (Copy ID, plus Open in Web/Share once `projectId`+`graphExplorerUrl` are known). What differs by
  screen is only the `subtitle` value and whether it's monospace (`subtitleMonospace`, default
  `true`): `ProjectDetailScreen` shows the resource's own mfid (monospace, since it's an opaque
  ID); `InstrumentDetailScreen` shows the dataset's owning project ID instead (`subtitleMonospace
  = false`, since a project ID reads as a normal word/slug, not an ID meant to line up
  character-by-character) — a dataset row from an aggregated, cross-project instrument view is
  more usefully disambiguated by *which project* it belongs to than by its own mfid, which is
  already one tap away via Copy ID. `InstrumentDetailScreen` now takes a `graphExplorerUrl`
  parameter (wired from `NavGraph.kt`, same source `ProjectDetailScreen` already used) purely so
  its rows can offer the same Open in Web/Share actions, using each dataset's own `projectId`.

---

## Search & pick patterns (`ui/common/SearchPicker.kt`)

Two shared shapes for "search-as-you-type and pick an entity" — pick the one matching the
interaction, don't invent a third without a second real use case:

- **Inline** (`SearchPickerField<T>`) — pick exactly one, selection is terminal. A text field
  with a floating `DropdownMenu` of results (capped `heightIn(max = 240.dp)`) that overlays
  without pushing surrounding layout around. Use for a single value inside a bigger form:
  `OwnerPickerField`/`InstrumentPickerField` (`FilterSheet.kt`), the project-lead search in
  `ManageProjectScreen.kt`'s `ProjectEditCard`. Never render results as a plain `Column.forEach`
  below the field — that shifts every sibling below it as results appear/change (the bug this
  pattern replaced).
- **Full** (`SearchPickerSheet<T>`) — repeated selection, act-per-row. A `ModalBottomSheet` with
  the search field pinned above a bounded `LazyColumn` of results (`heightIn(max = 420.dp)`), so
  the field can never scroll off-screen and a long result list scrolls within its own region
  instead of overflowing the sheet. Use when opening the sheet's whole purpose is picking one or
  more items and each pick triggers an immediate action (e.g. `AddMemberSheet`'s per-row "Add"
  button — the sheet stays open so you can add several people in one visit).
- Both share `rememberDebouncedSearchResults<T>()` (debounce + min-length gating, defaults from
  `data/util/SearchPickerConstants.kt`'s `SEARCH_DEBOUNCE_MS`/`SEARCH_MIN_QUERY_LENGTH`) for
  composable-local search state, mirroring `ProjectDetailScreen.kt`'s `rememberOwnerNames` hook
  pattern. ViewModel-owned searches that must be triggered from an event handler (not
  recomposition) — `ManageProjectViewModel`'s lead/member search — keep their own cancellable-Job
  pattern but reference the same shared constants.
- **Not unified**: `LinkResourceSheet.kt`'s bottom sheet mixes search with other contextual
  controls (a selected-item summary card, QR scan, a same-type/cross-type direction picker) —
  it's a workflow that *contains* a picker-like fragment, not a picker itself. It stays bespoke
  (already correctly scrollable, no bug) until a second "search mixed with other controls in a
  sheet" case exists to justify generalizing that shape too.

### Resolve-to-field (`SearchPickerField`'s `resolution` param)

All four `SearchPickerField<T>` callers (`OwnerPickerField`/`InstrumentPickerField` in
`FilterSheet.kt`/`InstrumentPickerField.kt`, `ManageProjectScreen.kt`'s and
`CreateProjectScreen.kt`'s project-lead fields) resolve free-typed text to a real record — modeled
on Gmail's recipient resolution, so a confirmed match reads as confirmed rather than as plain,
unvalidated text that only fails at submit time.

- **`ResolutionState<T>`** (`Idle`/`Resolving`/`Resolved`/`NotFound`) is derived *purely* from the
  same `(query, results, isSearching)` triple the field already receives (`resolveSearchMatch`,
  private to `SearchPicker.kt`) — no caller carries a dedicated "resolved" field in its own state.
  `Resolved` fires either from tapping a dropdown suggestion or from typing the exact
  name/username and having the debounced search confirm it.
- **Callers must keep a just-picked item in `results` as a singleton list, not clear it to
  `emptyList()`** — clearing would make the freshly-selected value immediately re-derive as
  `NotFound` (empty results, no exact match) the instant it's picked. See
  `ManageProjectViewModel`/`CreateProjectViewModel`'s `selectLeadUser`. The two
  `rememberDebouncedSearchResults`-driven callers (`OwnerPickerField`, `InstrumentPickerField`) hit
  the same problem from a different angle: selecting an item changes `query` to its exact value,
  which retriggers a fresh debounced search of that same value and briefly flips the field back to
  its searching/editable state before the redundant re-search confirms the same match again. Both
  pin the just-picked item in a local `remember`ed var, overriding the live hook's
  `results`/`isSearching` while `query` still matches the pin, so the field stays resolved
  continuously instead of flashing — released the moment the user types again.
- **Opt in via `resolution: ResolvedPicker<T>?`** (`keyOf`, `resolvedLabel`, `resolvedLeading`,
  `onClear`). When resolved, `SearchPickerField` renders a private `ResolvedField` — a **read-only
  `OutlinedTextField`**, not a colored pill: same `label`, same transparent background/outline as
  every other field, `resolvedLeading` as the leading content and `resolvedLabel` as the value, with
  a clear "×" trailing icon — instead of the editable text field entirely, not an overlay on top of
  it. An earlier version rendered a solid `secondaryContainer` pill instead; that dropped the
  field's label entirely (no indication of what a resolved field was) and read as an
  error/warning banner rather than a confirmed value on any accent whose `secondaryContainer` leans
  orange/red, so it was replaced with the plain-field approach, which has no theme-dependent fill
  color to get wrong. `NotFound` (still editable) tints the field via `isError` (M3's own error
  styling) and swaps the trailing icon to `AppIcons.SearchOff` (already used for "no results" empty
  states elsewhere — no new icon needed). Every current `User`-resolving caller reuses
  `UserChipLeading`/`userDisplayName()` for `resolvedLeading`/`resolvedLabel`.

---

## Avatar colors

`UserAvatar` (`ui/common/UserComponents.kt`) derives its background color from the person's
ORCID via a private `orcidToColor()` — a deterministic hash into a constrained HSL range (not
the full 0-1 saturation/lightness space, specifically so the fixed white initials text stays
legible against every generated color). Same person → same color everywhere in the app; no
per-call-site color logic to keep in sync. Pass `orcid = user.uniqueId` (or any other stable
per-person string) to opt an avatar into this; omitting it falls back to the static
`containerColor`/`contentColor` pair (used only where no stable per-person ID exists yet).

Prefer `UserIdentityRow` over a bare `UserAvatar` + hand-rolled name `Text` when the row is
just "avatar + one line of name" (member lists, join-request rows) — it already wires `orcid`
through and uses `userDisplayName()`, so there's one place to change if either convention
changes. Reach for `UserAvatar` directly only when the layout doesn't fit `UserIdentityRow`
(a profile header with a larger avatar and stacked name/username, an edit form, a row with a
multi-line subtitle) — in those cases still pass `orcid` explicitly so the color logic doesn't
silently regress to the static fallback.

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
