---
name: release
description: Cut a tagged release of Crucible Lens - version bump, changelog promotion, signed AAB/APK build, tag and push. Use this whenever the user asks to cut, ship, publish, or tag a release, bump the version, or produce release artifacts. Do NOT improvise these steps from memory; the ordering and the signature check exist because a past release silently shipped unsigned.
disable-model-invocation: true
---

# Releasing Crucible Lens

Every step, in order, every time. Skipping step 3 is how an unsigned build shipped once already.

## 1. Version and changelog (manual, deliberate)

Bump both properties in `gradle.properties` - the single source of truth. Neither `composeApp` nor
`androidApp` may hardcode a version:

```
app.versionName=X.Y.Z
app.versionCode=<n+1>
```

Then promote the changelog. `## [Unreleased]` should already be populated incrementally (see
CLAUDE.md's "Changelog discipline" - entries are written as changes land, never backfilled from git
history at release time):

- Rename the heading to `## [X.Y.Z] - YYYY-MM-DD`
- Add a one- or two-sentence **summary paragraph** above the `### ` groups
- Add a fresh, empty `## [Unreleased]` above it

The summary paragraph is not optional: `.github/workflows/release.yml` generates the GitHub release
notes from it and **fails the release if it is missing**.

## 2. Run the release script

```bash
./scripts/release.sh
```

One step, doing all of: verify (`:composeApp:compileAndroidMain`, `:composeApp:testAndroidHostTest`,
`:composeApp:compileKotlinIosArm64`), build the debug APK plus release AAB/APK, verify the release
build is actually signed (`apksigner verify` on the APK, `META-INF/*.RSA` present in the AAB), and
copy both artifacts to the synced Drive folder as
`~/WORK/Crucible/App/apk/crucible-lens-v{version}-{debug.apk,release.aab}`.

Signing reads `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` from the
environment, or `keystore.path` / `keystore.password` / `key.alias` / `key.password` from
`local.properties` (gitignored). This is the same `signingConfigs.release` Gradle already reads -
the script does not sign anything itself. `scripts/release.sh` prompts interactively (hidden
input) for `KEYSTORE_PASSWORD`/`KEY_PASSWORD` if they aren't already set as env vars, so the two
passwords never need to sit in `local.properties` - keep them in a password manager instead and
paste them in each run. `keystore.path`/`key.alias` (not secrets) can still live in
`local.properties`.

The script deliberately does **not** bump the version, touch `CHANGELOG.md`, commit, tag, or push.
Those stay manual.

## 3. Read the signing check before going further

If signature verification fails, stop and fix the keystore configuration. Do not continue.

This check exists because Gradle's `signingConfig` **no-ops rather than failing** when the keystore
path resolves to `null` - the build succeeds and produces a silently unsigned artifact. Not
hypothetical; it already shipped once.

## 4. Commit, push, tag, push the tag

In that order. The script prints the exact tag command.

`.github/workflows/release.yml` builds and signs in CI on a pushed `v*.*.*` tag (or manual
`workflow_dispatch`), using the `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
`KEY_PASSWORD` repo secrets, and runs the same signature verification. It opens a **draft** release
whose notes are the version's summary paragraph plus a link to `CHANGELOG.md` - not a copy of every
bullet.

## Ad-hoc debug builds (not a release)

For ad-hoc testing, build and copy to the Drive folder under the fixed name
`~/WORK/Crucible/App/apk/crucible-lens-dev-debug.apk`:

```bash
JAVA_HOME="${JAVA_HOME:-$HOME/software/android-studio/jbr}" ./gradlew :androidApp:assembleDebug
```

`dev` in place of a version number marks a build containing uncommitted or unreleased changes.
Always overwrite the same filename rather than accumulating one file per build. The versioned name
`crucible-lens-v{version}-debug.apk` is reserved for artifacts produced by a tagged release.

## Signing reference

- Debug builds self-sign with the local debug keystore and sideload fine.
- Debug uses `applicationIdSuffix = ".debug"` (`gov.lbl.crucible.debug` vs release's
  `gov.lbl.crucible`) so a sideloaded debug APK never collides with an installed release build.
  Android refuses to install over a differently-signed APK of the same package, and surfaces it as
  a bare, unexplained "App not installed."
- `*.jks` and `*.keystore` are git-ignored. Never commit one.
