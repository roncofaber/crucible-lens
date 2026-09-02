#!/usr/bin/env bash
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULTS_DIR="$REPO_ROOT/app/build/test-results/testAndroidHostTest"

if out=$("$REPO_ROOT/gradlew" -p "$REPO_ROOT" --quiet :composeApp:compileAndroidMain :composeApp:testAndroidHostTest 2>&1); then
  [[ -n "$out" ]] && printf '%s\n' "$out"
  echo "Verification passed."
  exit 0
fi

if compile_errors=$(grep -E "^e:" <<<"$out") && [[ -n "$compile_errors" ]]; then
  echo "Compile check failed:"
  head -30 <<<"$compile_errors"
  exit 1
fi

failures=$(python3 - "$RESULTS_DIR" <<'PY' 2>/dev/null
import glob
import os
import sys
import xml.etree.ElementTree as ET

failures = []
for path in sorted(glob.glob(os.path.join(sys.argv[1], "TEST-*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for error in list(case.findall("failure")) + list(case.findall("error")):
            class_name = (case.get("classname") or "").rsplit(".", 1)[-1]
            message = (error.get("message") or "").strip().splitlines()
            failures.append(f"  {class_name}.{case.get('name')}: {message[0] if message else error.get('type', '')}")
print("\n".join(failures[:30]))
PY
)

if [[ -n "$failures" ]]; then
  echo "Tests failed:"
  echo "$failures"
else
  echo "Verification failed:"
  grep -E "^e:|^w:|FAILURE|What went wrong|Execution failed|Caused by" <<<"$out" | head -30
fi

exit 1
