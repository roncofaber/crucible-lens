#!/usr/bin/env bash
# Sign and zipalign an already-built unsigned APK.
# Usage: ./scripts/sign_apk.sh [path/to/unsigned.apk] [output.apk]
# Defaults: unsigned = app/build/.../composeApp-release-unsigned.apk
#           signed   = ~/Desktop/crucible-lens-signed.apk

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="/home/roncofaber/WORK/Crucible/App/keystore/crucible-lens.jks"
KEY_ALIAS="crucible-lens"
UNSIGNED_APK="${1:-$REPO_ROOT/app/build/outputs/apk/release/composeApp-release-unsigned.apk}"
SIGNED_APK="${2:-$HOME/Desktop/crucible-lens-signed.apk}"
JAVA_HOME="${JAVA_HOME:-/home/roncofaber/software/android-studio/jbr}"
BUILD_TOOLS="$HOME/Android/Sdk/build-tools/36.0.0"

echo "Input:  $UNSIGNED_APK"
echo "Output: $SIGNED_APK"
echo ""

echo "=== Signing ==="
"$JAVA_HOME/bin/jarsigner" \
  -sigalg SHA256withRSA \
  -digestalg SHA-256 \
  -keystore "$KEYSTORE" \
  "$UNSIGNED_APK" \
  "$KEY_ALIAS"

echo ""
echo "=== Zipalign ==="
"$BUILD_TOOLS/zipalign" -f -v 4 "$UNSIGNED_APK" "$SIGNED_APK"

echo ""
echo "=== Verify ==="
"$JAVA_HOME/bin/jarsigner" -verify -certs "$SIGNED_APK" | head -3

echo ""
echo "Done: $SIGNED_APK"
