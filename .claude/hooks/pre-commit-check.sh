#!/usr/bin/env bash
# PreToolUse(Bash) gate: never let a commit land that doesn't compile or has failing tests.
#
# CLAUDE.md carried these as advisory "run the checks before committing" rules, which only
# worked when they were remembered. Exit code 2 blocks the tool call and feeds stderr back
# to Claude, turning the rules into facts.
#
# Tests are gated here rather than in CI because .github/workflows/release.yml only fires on
# a version tag and takes ~20 minutes, so this hook is the earliest automated signal a broken
# test gets. See dev/architecture.md's "Testing" section.
#
# Scope: this only sees commits made through Claude Code's Bash tool. Commits made by hand in
# another terminal are not gated; that would need a git pre-commit hook.
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | python3 -c \
  'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("command",""))' 2>/dev/null) || exit 0

# Only gate commits. Everything else (including `git status`) passes straight through,
# so the common case costs nothing.
case "$cmd" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[[ -x "$PROJECT_DIR/gradlew" ]] || exit 0

RESULTS_DIR="$PROJECT_DIR/app/build/test-results/testAndroidHostTest"

# One Gradle invocation for both tasks: Gradle dedupes the shared compile work, and when
# nothing has changed both are UP-TO-DATE and this costs about as much as the old
# compile-only check.
if out=$(JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" \
      "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" --quiet \
      :composeApp:compileAndroidMain :composeApp:testAndroidHostTest 2>&1); then
  exit 0
fi

# Compile errors first: if the build never got as far as running tests, the JUnit XML in
# RESULTS_DIR is stale from an earlier run and would misleadingly report zero failures.
if compile_errors=$(grep -E "^e:" <<<"$out") && [[ -n "$compile_errors" ]]; then
  {
    echo "Compile check failed - commit blocked. Fix these first:"
    head -30 <<<"$compile_errors"
  } >&2
  exit 2
fi

# Otherwise it's a test failure. Gradle's own message just points at an HTML report, so pull
# the failing cases out of the JUnit XML instead.
failures=$(python3 - "$RESULTS_DIR" <<'PY' 2>/dev/null
import glob, os, sys, xml.etree.ElementTree as ET
out = []
for path in sorted(glob.glob(os.path.join(sys.argv[1], "TEST-*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for bad in list(case.findall("failure")) + list(case.findall("error")):
            cls = (case.get("classname") or "").rsplit(".", 1)[-1]
            msg = (bad.get("message") or "").strip().splitlines()
            out.append(f"  {cls}.{case.get('name')}: {msg[0] if msg else bad.get('type', '')}")
print("\n".join(out[:30]))
PY
)

{
  if [[ -n "$failures" ]]; then
    echo "Tests failed - commit blocked. Fix these first:"
    echo "$failures"
    echo
    echo "Re-run: ./gradlew :composeApp:testAndroidHostTest"
  else
    # Neither a compile error nor a parseable test failure: surface the raw Gradle output so
    # the cause isn't swallowed (missing JDK, Gradle daemon problem, task wiring, etc).
    echo "Pre-commit check failed - commit blocked. Gradle output:"
    grep -E "^e:|^w:|FAILURE|What went wrong|Execution failed|Caused by" <<<"$out" | head -30
  fi
} >&2
exit 2
