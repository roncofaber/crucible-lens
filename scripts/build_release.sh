#!/usr/bin/env bash
# Build, sign, and zipalign a release APK for Crucible Lens.
# Usage: ./scripts/build_release.sh
# The signed APK lands on the Desktop as crucible-lens-signed.apk.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="/home/roncofaber/WORK/Crucible/App/keystore/crucible-lens.jks"
KEY_ALIAS="crucible-lens"
UNSIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/composeApp-release-unsigned.apk"
SIGNED_APK="$HOME/Desktop/crucible-lens-signed.apk"
JAVA_HOME="${JAVA_HOME:-/home/roncofaber/software/android-studio/jbr}"
BUILD_TOOLS="$HOME/Android/Sdk/build-tools/36.0.0"

echo "=== Building release APK ==="
JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" \
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :composeApp:assembleRelease

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
