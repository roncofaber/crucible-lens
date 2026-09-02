#!/usr/bin/env bash
set -uo pipefail

payload=$(cat)
cmd=$(printf '%s' "$payload" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input", {}).get("command", ""))' 2>/dev/null) || exit 0

case "$cmd" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
[[ -x "$PROJECT_DIR/scripts/verify-change.sh" ]] || exit 0

if "$PROJECT_DIR/scripts/verify-change.sh"; then
  exit 0
fi

echo "Compile or test verification failed - commit blocked." >&2
exit 2
