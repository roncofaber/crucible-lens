---
name: audit-docs
description: Audit CLAUDE.md and dev/*.md against the actual codebase and repair drift. Use when the user asks to review, audit, or refresh the project documentation, or asks whether the docs are still accurate. This is a periodic sweep, not something to run mid-task.
disable-model-invocation: true
---

# Auditing the project docs

Scope: `CLAUDE.md`, `dev/architecture.md`, `dev/style.md`, `dev/platform-parity.md`, `dev/icons.md`.

## Where to spend the budget

**Narrative content survives. Enumerative content rots.**

Prose explaining *why* something is the way it is stays accurate for years - the rationale doesn't
change when the code does. What goes stale is everything countable or nameable:

- Tables of values (typography roles, elevation levels, opt-in conventions)
- Parameter counts and sealed-class shapes
- Named symbols that were renamed or deleted
- File, token, and route counts
- Whole mechanisms that were replaced by something simpler

So skim the prose; interrogate the tables. Every claim that names a symbol, states a number, or
enumerates a set is worth a grep. Most other claims aren't.

## Method

One doc at a time. For each claim of the countable kind, check the code before believing it.

1. **Extract every named symbol** - classes, functions, properties, files, icon tokens - and grep for
   each. No hits means renamed or deleted; find out which.
2. **Recompute every stated count.** When a count disagrees, work out *why* before editing. A
   discrepancy sometimes turns out to be a legitimate exclusion worth documenting rather than a
   number to silently change.
3. **Read the actual definition** for any documented state machine, function signature, or parameter
   list. These are the highest-yield category and the cheapest to verify.
4. **Check described mechanisms still exist.** If a doc describes an algorithm (preload thresholds,
   eviction rules, wrap-around logic), find the code implementing it. Sometimes there is none.

## Ready-made checks

These enforce invariants the docs state outright. Run them from the repo root.

```bash
# fontWeight must not appear in ui/ outside Type.kt
grep -rn "fontWeight = FontWeight" --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/ | grep -v theme/Type.kt
# expect: zero

# fontSize likewise, except one monospace field
grep -rnE "fontSize = " --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/ | grep -v theme/Type.kt
# expect: exactly one - the 13sp monospace field in MetadataEditorScreen.kt

# retired type roles
grep -rnE 'typography\.(headline|display)|typography\.labelSmall' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v theme/Type.kt | grep -v TypographySettingsScreen
# expect: zero

# titleSmall is sanctioned only for the detail-row label
grep -rn 'typography\.titleSmall' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v theme/Type.kt | grep -v TypographySettingsScreen
# expect: only the two hits in InfoRows.kt (InfoRow / ClickableInfoRow)

# only two emphasized variants are sanctioned
grep -rhoE 'typography\.emphasized[A-Za-z]+' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui | grep -v TypographySettings | sort -u
# expect: emphasizedTitleLarge and emphasizedTitleMedium only

# textStyle overrides
grep -rn "textStyle = MaterialTheme" --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui
# expect: the two BasicTextField carve-outs (SearchBar.kt, ResourceListComponents.kt) and the two
#         MetadataEditor.kt dense-field exceptions

# stock roles must never be imported from crucible.lens.ui.theme - only the emphasizedX extensions
grep -rn 'import crucible.lens.ui.theme.\(body\|label\|title\|headline\|display\)' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui
# expect: zero

# legacy surfaceVariant role (excluding accent scheme definitions, which legitimately assign it)
grep -rn 'colorScheme\.surfaceVariant' --include=*.kt app/src/commonMain/kotlin/crucible/lens/ui/
# expect: only the two known LinkResourceSheet.kt hits, ideally zero

# counts asserted in the docs
ls app/src/commonMain/kotlin/crucible/lens/ui/theme/accents/ | wc -l                          # 12 accents
grep -c 'object .* : Screen' app/src/commonMain/kotlin/crucible/lens/ui/navigation/Screen.kt  # 27 routes
ls app/src/commonMain/composeResources/drawable/ic_*.xml | wc -l                              # 110 icon files
grep -cE '^\s+val [A-Za-z]+' app/src/commonMain/kotlin/crucible/lens/ui/common/AppIcons.kt    # 130 tokens
grep -rn "cardElevation" app/src/commonMain/kotlin/ | grep -v AppElevation.kt                 # 1 call site
```

When a number changes legitimately, update it in the doc **and** in the check above.

## Verify before you trust - including yourself

Do not edit a doc based on what another doc says, on what a subagent reported, or on recall. Open the
file. Exploration passes reliably produce confident-sounding claims the source contradicts: a gap
that was already closed, a miscounted total, an inflated list of "missing" symbols that turn out to
be intentional aliases. Applied unchecked, each writes a *new* error into the docs.

The same applies to your own drafts - re-read the source after writing a fix.

## Repair

Fix drift in place, in the doc that owns the fact. `CLAUDE.md`'s "Documentation map" assigns
ownership; respect it rather than duplicating a corrected fact into two files, since that duplication
is the original cause of the drift.

When a doc describes something that no longer exists, replace it with what the code does now. Don't
just delete the stale claim - the reason it was written usually still needs an answer.

Keep the house style while you're in there: rule first, one sentence of *why*, no blow-by-blow of
superseded implementations. If a section reads as archaeology, compress it.

## Report and finish

Summarise as: what was accurate (briefly - this needs no detail), then what drifted, grouped by file,
each with the correction.

- **No `CHANGELOG.md` entry** - documentation-only changes have no user-visible effect.
- Compile check anyway, as cheap insurance that a docs-only pass left the build untouched:
  ```bash
  JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :composeApp:compileAndroidMain
  ```
