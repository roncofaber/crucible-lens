---
name: audit-docs
description: Audit CLAUDE.md and dev/*.md against the actual codebase and repair drift. Use when the user asks to review, audit, or refresh the project documentation, or asks whether the docs are still accurate. This is a periodic sweep, not something to run mid-task.
disable-model-invocation: true
---

# Auditing the project docs

Scope: `CLAUDE.md`, `dev/architecture.md`, `dev/style.md`, `dev/platform-parity.md`, `dev/icons.md`.

## What actually drifts

The last full audit found a clear pattern worth exploiting rather than rediscovering:

**Narrative content survives. Enumerative content rots.**

Prose explaining *why* something is the way it is — the `CollapsingAppTopBar` rationale, nested-scroll
ordering, the Koin platform-module split, avatar colour derivation — was accurate throughout. What had
gone stale was everything countable or nameable:

- Tables of values (typography roles, elevation levels, opt-in conventions)
- Parameter counts ("NavGraph takes 6 parameters" — it takes 5)
- Sealed-class shapes (`UiState.Success` documented with fields it doesn't carry)
- Named symbols that no longer exist (`AppIcons.ChevronRight`, `isUserRefreshing`,
  `preloadRelatedResources`, `ic_storage_cache.xml`)
- File and token counts
- Whole described mechanisms that were replaced (a virtual-infinite `Int.MAX_VALUE` pager with
  ±10/±2/±20 preload thresholds, none of which existed any more)

So spend the audit budget on claims that name a symbol, state a number, or enumerate a set. Skim the
prose; interrogate the tables.

## Method

Work one doc at a time, and for each claim of the countable kind, check it against the code before
believing it.

1. **Extract every named symbol** from the doc — class names, function names, property names, file
   names, icon tokens — and grep for each. A symbol that returns no hits is either renamed or
   deleted; find which.
2. **Recompute every stated count.** Don't accept "108 XML files" or "23 routes"; run the count. When
   a count disagrees with the doc, work out *why* before editing — the last audit's 108-vs-110
   discrepancy turned out to be two PNGs that legitimately aren't icons, which is worth documenting
   rather than silently changing the number.
3. **Read the actual definition** for any documented state machine, function signature, or parameter
   list. Sealed-class shapes and parameter counts are the highest-yield category and the cheapest to
   verify.
4. **Check described mechanisms still exist.** If a doc describes an algorithm (preload thresholds,
   eviction rules, wrap-around logic), find the code implementing it. Sometimes there is none.

## Verify before you trust — including yourself

Do not edit a doc based on what another doc says, on what a subagent reported, or on recall. Open the
file. In the last audit three of four exploration passes produced at least one wrong claim: a
non-existent iOS gap, a miscounted route total, and an inflated "21 missing icon tokens" that was
really 4 once aliases were excluded. Every one of those would have written a *new* error into the
docs had it been applied unchecked.

The same applies to your own drafts. Two corrections in that audit came from re-reading the source
after writing the fix — a `fillColor` value stated from memory that all three files contradicted, and
a swipe-to-dismiss note that was accurate but too vague to be useful.

## Repair

Fix drift in place, in the doc that owns the fact. CLAUDE.md's "Documentation map" assigns ownership
— respect it rather than duplicating a corrected fact into two files, since that duplication is the
original cause of the drift.

When a doc describes something that no longer exists, replace it with what the code actually does
now. Don't just delete the stale claim; the reason it was written usually still needs an answer.

## Report and finish

Summarise as: what was accurate (briefly — this is the reassuring part and needs no detail), then
what drifted, grouped by file, each with the correction.

- No `CHANGELOG.md` entry — documentation-only changes have no user-visible effect.
- Compile check anyway, since a docs-only pass should leave the build untouched and it's cheap
  insurance: `JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain`
