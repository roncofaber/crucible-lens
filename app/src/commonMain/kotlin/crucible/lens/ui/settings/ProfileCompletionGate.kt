package crucible.lens.ui.settings

/**
 * Whether the current process has already dismissed the "Complete your profile" prompt via
 * "Skip for now" - session-only, never persisted, so the prompt reappears on the next launch
 * until a username is actually saved. Same in-memory-object convention as
 * `ui/create/DuplicateHolder.kt`/`ui/metadata/MetadataHolder.kt`.
 */
object ProfileCompletionGate {
    var skippedThisLaunch: Boolean = false
}
