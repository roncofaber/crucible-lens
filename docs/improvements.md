# Crucible Lens — Improvement Backlog

Identified improvements grouped by theme. Items marked **[M3]** were surfaced by the Material Design 3 skill audit.

---

## 1. Icons — Material Symbols Migration

**Current state:** `androidx.compose.material.icons` (deprecated, frozen, no new icons).  
**Target:** Material Symbols downloaded as Vector Drawable XML, placed in `commonMain/composeResources/drawable/`.

### Steps
1. Choose a consistent style — **Rounded** recommended (matches M3 rounded shapes throughout the app)
2. Create `AppIcons` object in `ui/common/AppIcons.kt` with all 99 icons exposed as `@Composable` painters
3. Replace all `Icon(Icons.Default.*, ...)` and `Icon(Icons.AutoMirrored.Filled.*, ...)` call sites
4. Remove `material-icons-core` / `material-icons-extended` from `build.gradle.kts`
5. Fix open icon issues (see section 2) while downloading

### Open icon issues to fix during migration
| Current | Context | Better choice |
|---------|---------|---------------|
| `Description` | PDF files AND data format field — same icon, two uses | `PictureAsPdf` for PDF; `Code` or `DataArray` for format |
| `Storage` | File size field AND cache settings entry | `DataUsage` for file size |
| `Restore` | Recently viewed items on home screen | `AccessTime` or `WatchLater` |
| `Straighten` | Instrument model field | `Numbers` or `Info` |
| `HistoryToggleOff` | Empty history state | Plain `History` (dimmed) |
| `Tune` | Group-by control | `CategoryAlt` or `DisplaySettings` |

### Fill axis opportunity **[M3]**
Material Symbols has a `fill` variable axis (0 = outlined, 1 = filled). Replace icon-pair patterns with a single icon + fill state:
- `Bookmark` / `BookmarkBorder` → single `Bookmark` with fill 0/1
- Navigation active/inactive states → fill 0/1 instead of separate icon names

---

## 2. Motion — Spring Physics **[M3]**

**Current state:** `StandardAnim = tween<Float>(200)` and `FastAnim = tween<Float>(150)` for all component animations.

M3 Expressive (May 2025) recommends **spring-based motion** for component expand/collapse. The `ExpandChevron` rotation and `animateContentSize` are the primary candidates.

### Proposed changes
```kotlin
// AppAnimations.kt — replace tween specs with springs for component motion
val StandardSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
val FastSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumHigh)
```

**Enter/exit `AnimatedVisibility` transitions** should remain easing-based per M3 spec:
- Enter: emphasized decelerate easing, ~400ms
- Exit: emphasized accelerate easing, ~200ms

Currently using `tween(200)` for both — enter is too fast for M3 spec. Consider updating `ResourceDetailScreen` card appearance animations to 400ms enter / 200ms exit.

---

## 3. Search Experience **[M3]**

**Current state:** Custom `TextField` embedded inside `TopAppBar`. Works but diverges from M3's search pattern.

**M3 expectation:** A dedicated `SearchBar` composable that:
- Sits at the top of the screen as a persistent bar (not embedded in the app bar)
- Expands to `SearchView` when focused, showing history and suggestions in a modal
- Includes voice, clear, and back affordances by default

**Impact:** `SearchScreen` would be restructured. The current approach (TextField in TopAppBar with filter/home buttons) would become a `SearchBar` at the top with the LazyColumn below. This is a meaningful UX improvement but requires careful migration given the filter sheet integration.

---

## 4. Adaptive Navigation **[M3]**

**Current state:** `NavigationBar` (bottom bar) on all screen sizes.

**M3 expectation:**
| Window width | Navigation pattern |
|---|---|
| Compact (< 600dp) | `NavigationBar` (bottom) — current |
| Medium (600–1200dp) | `NavigationRail` (side) |
| Expanded (> 1200dp) | `NavigationDrawer` (persistent side drawer) |

**Priority:** Low — the app is mobile-first and used on phones. However, if lab staff use tablets, the bottom bar on a landscape tablet feels off. Implement with `WindowSizeClass` when the time is right.

---

## 5. List Components **[M3]**

**Current state:** Custom `Row` / `Card` layouts for resource lists (samples, datasets in project detail; instrument datasets; search results).

**M3 expectation:** `ListItem` composable handles the canonical single-line / two-line / three-line list pattern with defined slots for leading icon, supporting text, and trailing content. Using it would make lists more consistent and reduce custom layout code.

**Best candidates for migration:**
- `ProjectDetailScreen` sample and dataset tabs
- `InstrumentDetailScreen` dataset list
- `HistoryScreen` history cards (the current Card-based layout works but is custom)

---

## 6. User Profile Page (deferred)

**Status:** Intentionally deferred — blocked by API auth constraint.

`GET /users/{orcid}` requires Admin OR the user being queried. Regular users cannot view arbitrary profiles, so tapping "owner" on a resource would 403 unless that user is in a shared access group.

**Graceful design when implemented:**
- Show what we already have (from resolved `owner` object: `@username`, name, ORCID) immediately
- Attempt `GET /users/{orcid}` in background; show their projects if accessible
- Fall back cleanly to the static info if 403
- Keep ORCID as a secondary "View full ORCID profile →" link

---

## 7. Resource Visibility Editing

**Current state:** `isPublic` (Public/Private) is displayed in the Advanced section of Sample and Dataset cards but is not editable — the `EditResourceSheet` does not expose it.

**What's needed:** Add a visibility toggle (Public / Private) to `EditResourceSheet` for both sample and dataset edit flows. The API supports it via `PATCH /samples/{id}` and `PATCH /datasets/{id}` with the `public` field.

---

## 8. Enter/Exit Animation Timing Refinement **[M3]**

M3 emphasized easing values (for `AnimatedVisibility` blocks in `ResourceDetailScreen`):
- **Enter (expandVertically + fadeIn):** ~400ms with decelerate easing
- **Exit (shrinkVertically + fadeOut):** ~200ms with accelerate easing

Currently both use `tween(200)`. Entry is visually abrupt. A small change with visible impact.

---

## 9. CLAUDE.md — Update with Session Learnings

The project `CLAUDE.md` should be updated to capture:
- `ExpandChevron` / `StandardAnim` / `FastAnim` / `StandardSizeAnim` / `FastSizeAnim` in `AppAnimations.kt`
- `AppIcons` object (once icons are migrated)
- `UserComponents.kt` shared composables
- Icon conventions (Biotech for instruments everywhere, etc.)
- The `tween(200)` → `StandardSizeAnim` pattern

---

## Priority Order

| Priority | Item |
|----------|------|
| 1 | Icon migration (Material Symbols + fix open issues) |
| 2 | Motion spring physics (`ExpandChevron`, `animateContentSize`) |
| 3 | Enter/exit animation timing (400ms enter, 200ms exit) |
| 4 | Resource visibility editing in `EditResourceSheet` |
| 5 | Search experience (`SearchBar` composable) |
| 6 | List components (`ListItem` migration) |
| 7 | User profile page (needs API auth discussion first) |
| 8 | Adaptive navigation (tablets / large screens) |
| 9 | CLAUDE.md update |
