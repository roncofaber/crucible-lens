#!/usr/bin/env bash
# Sign and zipalign an already-built unsigned APK using apksigner (v1/v2/v3).
# Usage: ./scripts/sign_apk.sh [path/to/unsigned.apk] [output.apk]
# Defaults: unsigned = app/build/.../composeApp-release-unsigned.apk
#           signed   = ~/Desktop/crucible-lens-signed.apk

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="/home/roncofaber/WORK/Crucible/App/keystore/crucible-lens.jks"
KEY_ALIAS="crucible-lens"
UNSIGNED_APK="${1:-$REPO_ROOT/app/build/outputs/apk/release/composeApp-release-unsigned.apk}"
SIGNED_APK="${2:-$HOME/Desktop/crucible-lens-signed.apk}"
ALIGNED_APK="/tmp/crucible-lens-aligned.apk"
BUILD_TOOLS="$HOME/Android/Sdk/build-tools/36.0.0"

echo "Input:  $UNSIGNED_APK"
echo "Output: $SIGNED_APK"
echo ""

echo "=== Zipalign ==="
"$BUILD_TOOLS/zipalign" -f -v 4 "$UNSIGNED_APK" "$ALIGNED_APK"

echo ""
echo "=== Signing with apksigner (v1/v2/v3) ==="
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

echo ""
echo "=== Verify ==="
"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED_APK" | head -6

rm -f "$ALIGNED_APK"
echo ""
echo "Done: $SIGNED_APK"
