# UI Regressions: KMP Port vs Original Android

Comparison of `ios-development` (commonMain) against `main` (pure Android).

---

## Fixed

### QrCodeDialogWithNavigation — title area degraded
**Original**: Resource name was scrollable horizontally with a right-edge fade gradient; scroll state reset per page via `key(pagerState.currentPage)`; navigation arrows were subtle (`onSurface.copy(alpha=0.5f)`, 20dp).  
**KMP port had**: Plain `Text` with `TextOverflow.Ellipsis`; no scrolling; arrows were `primary` colour (too prominent); no `key()` so scroll position persisted across page swipes.  
**Status**: ✅ Fixed — restored scrollable title, fade, `key()`, and subtle arrow tint.

---

## Remaining known regressions

### Pull-to-refresh content shift removed
**Original**: During pull-to-refresh, the list content shifted down by `pullRefreshState.verticalOffset` via `graphicsLayer { translationY = ... }`, giving a physical "pushing the content down" feel.  
**KMP port**: `PullToRefreshBox` handles the indicator but content stays fixed — no vertical translation of the list during the pull gesture.  
**Severity**: Low — the refresh still works, the animation is just less tactile. Fixing requires wrapping content in a custom `PullToRefreshBox` subclass or using `Modifier.offset`.

### `animateItemPlacement()` → `animateItem()` spec change
**Original**: `Modifier.animateItemPlacement(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))` — custom bouncy spring.  
**KMP port**: `Modifier.animateItem()` with default spec (linear, less characterful).  
**Severity**: Low — items still animate on reorder, but the spring personality is gone.  
**Fix**: `Modifier.animateItem(placementSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))`

### App logo on iOS is plain text
**Original**: PNG logo with light/dark variants, proper branding.  
**iOS**: `Text("Crucible Lens")` with `headlineLarge` style.  
**Fix**: Move logo PNGs into `commonMain/composeResources/drawable/` and use `painterResource(Res.drawable.crucible_text_light)` from the CMP resources API.

### Toast notifications invisible on iOS
**Original**: `Toast.makeText()` for actions like "ID copied", "Thumbnail uploaded", etc.  
**iOS**: `println()` — completely invisible to the user.  
**Fix**: Add a `SnackbarHostState` overlay in `AppScaffold` and route `showToast()` through it cross-platform.

---

## No regression confirmed

| Feature | Status |
|---|---|
| ProjectCard layout (icon, title, ID, CountChips, pin toggle) | ✅ Identical |
| InstrumentCard layout | ✅ Identical |
| ResourceDetailScreen top bar (search, home, overflow menu items) | ✅ Identical |
| Overflow menu items (Edit, Duplicate, Add thumbnail, Link, Delete, Share, Open in web) | ✅ Identical |
| Sibling grouping pill in overflow | ✅ Identical |
| Help dialog content | ✅ Identical |
| Easter egg dialog | ✅ Identical |
| Pinned projects/instruments section on HomeScreen | ✅ Identical |
| Sort/Group controls (tune icon, popups) | ✅ Identical |
| Archived projects section (collapsed by default) | ✅ Identical |
| Share action (uses `shareText()` platform wrapper) | ✅ Equivalent |
| Horizontal pager for resource siblings | ✅ Identical |
| Thumbnail carousel in resource detail | ✅ Identical |
| Scientific metadata display | ✅ Identical |
| QrCodeDialog (single resource) | ✅ Identical |
| History screen | ✅ Identical |
| Search screen | ✅ Identical |
| Settings screens (API, Appearance, Cache, About) | ✅ Identical |
| API settings sticky save/discard bar | ✅ Identical |
| Dark/light theme switching | ✅ Identical |
| Accent colour themes | ✅ Identical |
| Instrument detail grouped datasets | ✅ Identical |
