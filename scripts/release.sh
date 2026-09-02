#!/usr/bin/env bash
# Usage: ./scripts/release.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

JAVA_HOME="${JAVA_HOME:-/home/$(whoami)/software/android-studio/jbr}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

VERSION="$(grep '^app.versionName=' gradle.properties | cut -d'=' -f2)"
echo "=== Releasing crucible-lens v$VERSION ==="

has_local_property() {
  [[ -f local.properties ]] && grep -q "^$1=." local.properties
}

if [[ -z "${KEYSTORE_PASSWORD:-}" ]] && ! has_local_property "keystore.password"; then
  read -rs -p "Keystore password: " KEYSTORE_PASSWORD
  echo
  export KEYSTORE_PASSWORD
fi
if [[ -z "${KEY_PASSWORD:-}" ]] && ! has_local_property "key.password"; then
  read -rs -p "Key password: " KEY_PASSWORD
  echo
  export KEY_PASSWORD
fi

echo ""
echo "=== Verify ==="
./scripts/verify-change.sh
./gradlew :composeApp:compileKotlinIosArm64

echo ""
echo "=== Build debug APK + release bundle/APK ==="
# Keep debug and release in separate invocations because generateAppBuildConfig inspects requested task names.
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:bundleRelease :androidApp:assembleRelease

DEBUG_APK="$REPO_ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
RELEASE_APK="$REPO_ROOT/androidApp/build/outputs/apk/release/androidApp-release.apk"
RELEASE_AAB="$REPO_ROOT/androidApp/build/outputs/bundle/release/androidApp-release.aab"

echo ""
echo "=== Verify the release build isn't flagged as debug ==="
GENERATED_BUILD_CONFIG="$REPO_ROOT/app/build/generated/appBuildConfig/kotlin/crucible/lens/AppBuildConfig.kt"
if ! grep -q "DEBUG: Boolean = false" "$GENERATED_BUILD_CONFIG"; then
  echo "ERROR: AppBuildConfig.DEBUG is not false after the release build - refusing to publish a debug-flagged release." >&2
  echo "This generated file is shared across variants; see the comment above the build step." >&2
  exit 1
fi
echo "DEBUG = false, OK."

echo ""
echo "=== Verify the release build is actually signed ==="
BUILD_TOOLS="${BUILD_TOOLS:-$HOME/Android/Sdk/build-tools/$(ls "$HOME/Android/Sdk/build-tools" | sort -V | tail -1)}"
if ! "$BUILD_TOOLS/apksigner" verify --print-certs "$RELEASE_APK"; then
  echo "ERROR: $RELEASE_APK failed apksigner verification - refusing to publish an unsigned APK." >&2
  echo "Check KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD or local.properties' keystore.* keys." >&2
  exit 1
fi
if ! unzip -l "$RELEASE_AAB" | grep -qE "META-INF/.*\.(RSA|EC|DSA)$"; then
  echo "ERROR: $RELEASE_AAB has no signature block (META-INF/*.RSA|EC|DSA) - refusing to publish an unsigned bundle." >&2
  exit 1
fi
echo "Signed OK."

echo ""
echo "=== Copy artifacts to Drive ==="
DEST_DIR="$HOME/WORK/Crucible/App/apk"
mkdir -p "$DEST_DIR"
cp "$DEBUG_APK" "$DEST_DIR/crucible-lens-v${VERSION}-debug.apk"
cp "$RELEASE_AAB" "$DEST_DIR/crucible-lens-v${VERSION}-release.aab"
echo "Copied to $DEST_DIR/crucible-lens-v${VERSION}-{debug.apk,release.aab}"

echo ""
echo "=== Done ==="
echo "Next: commit, then tag+push v$VERSION to trigger .github/workflows/release.yml"
echo "  git tag v$VERSION && git push origin main v$VERSION"
