package crucible.lens.platform

/** "dev" on debug builds instead of "v{numeric version}", [appVersionName] with a "v" prefix
 * otherwise — so a sideloaded debug build is visually distinguishable from a release build
 * anywhere the version string is shown. */
fun displayVersionName(): String = if (isDebugBuild) "dev" else "v${appVersionName()}"
