#!/usr/bin/env bash
# Local release build for Crucible Lens — runs the documented steps from CLAUDE.md's
# "Release process" end to end (verify -> build -> verify signed -> copy to Drive).
#
# This does NOT bump the version or touch CHANGELOG.md, tag, commit, or push — those stay
# manual/deliberate steps. Run this after gradle.properties and CHANGELOG.md are already
# updated for the release you're cutting.
#
# Usage: ./scripts/release.sh
#
# Signing: androidApp/build.gradle.kts's signingConfigs.release reads KEYSTORE_PATH/
# KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD from the environment, falling back to
# local.properties' keystore.path/keystore.password/key.alias/key.password (gitignored).
# No separate zipalign/sign step is needed — Gradle's assembleRelease/bundleRelease already
# produce a signed artifact when signingConfigs.release.storeFile is set.
#
# The two passwords are prompted for interactively below (hidden input) rather than read from
# local.properties, so nothing sensitive needs to sit on disk — pull them from a password
# manager each run. Export KEYSTORE_PASSWORD/KEY_PASSWORD yourself beforehand to skip a prompt
# (e.g. scripting/CI use), otherwise leave keystore.password/key.password out of
# local.properties entirely.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

JAVA_HOME="${JAVA_HOME:-/home/$(whoami)/software/android-studio/jbr}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

VERSION="$(grep '^app.versionName=' gradle.properties | cut -d'=' -f2)"
echo "=== Releasing crucible-lens v$VERSION ==="

if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
  read -rs -p "Keystore password: " KEYSTORE_PASSWORD
  echo
fi
if [[ -z "${KEY_PASSWORD:-}" ]]; then
  read -rs -p "Key password: " KEY_PASSWORD
  echo
fi
export KEYSTORE_PASSWORD KEY_PASSWORD

echo ""
echo "=== Verify ==="
./gradlew :composeApp:compileAndroidMain :composeApp:testAndroidHostTest :composeApp:compileKotlinIosArm64

echo ""
echo "=== Build debug APK + release bundle/APK ==="
./gradlew :androidApp:assembleDebug :androidApp:bundleRelease :androidApp:assembleRelease

DEBUG_APK="$REPO_ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
RELEASE_APK="$REPO_ROOT/androidApp/build/outputs/apk/release/androidApp-release.apk"
RELEASE_AAB="$REPO_ROOT/androidApp/build/outputs/bundle/release/androidApp-release.aab"

echo ""
echo "=== Verify the release build is actually signed ==="
BUILD_TOOLS="${BUILD_TOOLS:-$HOME/Android/Sdk/build-tools/$(ls "$HOME/Android/Sdk/build-tools" | sort -V | tail -1)}"
if ! "$BUILD_TOOLS/apksigner" verify --print-certs "$RELEASE_APK"; then
  echo "ERROR: $RELEASE_APK failed apksigner verification — refusing to publish an unsigned APK." >&2
  echo "Check KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD or local.properties' keystore.* keys." >&2
  exit 1
fi
if ! unzip -l "$RELEASE_AAB" | grep -qE "META-INF/.*\.(RSA|EC|DSA)$"; then
  echo "ERROR: $RELEASE_AAB has no signature block (META-INF/*.RSA|EC|DSA) — refusing to publish an unsigned bundle." >&2
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
