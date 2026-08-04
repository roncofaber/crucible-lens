#!/usr/bin/env bash
# PreToolUse(Bash) gate: never let a commit land that doesn't compile.
#
# CLAUDE.md carried this as an advisory "run the compile check before committing" rule,
# which only worked when it was remembered. Exit code 2 blocks the tool call and feeds
# stderr back to Claude, turning the rule into a fact.
#
# Scope: this only sees commits made through Claude Code's Bash tool. Commits made by
# hand in another terminal are not gated — that would need a git pre-commit hook.
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

if ! out=$(JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" \
      "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" --quiet :composeApp:compileAndroidMain 2>&1); then
  {
    echo "Compile check failed — commit blocked. Fix these first:"
    grep -E "^e:|^w:|FAILURE|Caused by" <<<"$out" | head -30
  } >&2
  exit 2
fi

exit 0
