#!/usr/bin/env bash
# Build, sign, and zipalign a release APK for Crucible Lens.
# Uses apksigner (v1/v2/v3 schemes) — required for Android 7+.
# Usage: ./scripts/build_release.sh
# The signed APK lands on the Desktop as crucible-lens-signed.apk.

set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYSTORE="/home/roncofaber/WORK/Crucible/App/keystore/crucible-lens.jks"
KEY_ALIAS="crucible-lens"
UNSIGNED_APK="$REPO_ROOT/app/build/outputs/apk/release/composeApp-release-unsigned.apk"
ALIGNED_APK="/tmp/crucible-lens-aligned.apk"
SIGNED_APK="$HOME/Desktop/crucible-lens-signed.apk"
JAVA_HOME="${JAVA_HOME:-/home/roncofaber/software/android-studio/jbr}"
BUILD_TOOLS="$HOME/Android/Sdk/build-tools/36.0.0"

echo "=== Building release APK ==="
JAVA_HOME="$JAVA_HOME" PATH="$JAVA_HOME/bin:$PATH" \
  "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :composeApp:assembleRelease

echo ""
echo "=== Zipalign (must happen before signing with apksigner) ==="
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
